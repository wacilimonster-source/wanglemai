package com.wanglemai.app.data.update

import com.wanglemai.app.domain.UpdateChecker
import com.wanglemai.app.domain.UpdateException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

data class DownloadedApk(
    val file: File,
    val sha256Verified: Boolean,
    val viaAcceleratedSource: Boolean
)

/** One open HTTP response; a narrow surface so tests can supply fake streams. */
interface DownloadStream {
    val contentLength: Long
    fun stream(): InputStream
    fun disconnect()
}

/**
 * Downloads the update APK inside the app instead of handing the URL to a browser.
 * The jsDelivr mirror is tried first and GitHub is the fallback, mirroring [GitHubUpdateClient].
 * Every chunk is hashed while it is written, so the file is verified against the manifest
 * SHA256 without a second pass; installation itself only starts after the user confirms.
 * Progress callbacks carry the source actually in use so the UI can show a mid-download
 * fallback from the jsDelivr mirror to GitHub.
 */
class ApkDownloader(
    private val timeoutMillis: Int = 15_000,
    private val streamOpener: (String) -> DownloadStream = { url -> openHttpStream(url, timeoutMillis) }
) {
    suspend fun download(
        apkMirrorUrl: String?,
        apkUrl: String?,
        expectedSha256: String?,
        targetDir: File,
        onProgress: (received: Long, total: Long, viaAcceleratedSource: Boolean) -> Unit
    ): DownloadedApk = withContext(Dispatchers.IO) {
        val sources = buildList {
            apkMirrorUrl?.let { add(it to true) }
            apkUrl?.let { add(it to false) }
        }
        if (sources.isEmpty()) throw UpdateException("没有可用的下载地址")
        var lastError: UpdateException? = null
        for ((url, accelerated) in sources) {
            try {
                return@withContext downloadFrom(url, expectedSha256, targetDir, onProgress, accelerated)
            } catch (e: UpdateException) {
                // A cancelled run must stop here: its next attempt would clean the target
                // directory and race with the newer download that replaced it.
                currentCoroutineContext().ensureActive()
                lastError = e
            }
        }
        val last = lastError ?: UpdateException("下载失败，请稍后重试")
        throw if (sources.size > 1 && !last.message.contains("校验失败")) {
            UpdateException("加速源与 GitHub 均下载失败，请检查网络后重试")
        } else {
            last
        }
    }

    private suspend fun downloadFrom(
        url: String,
        expectedSha256: String?,
        targetDir: File,
        onProgress: (Long, Long, Boolean) -> Unit,
        viaAcceleratedSource: Boolean
    ): DownloadedApk {
        if (!UpdateChecker.isTrustedDownloadUrl(url)) throw UpdateException("下载地址无效")
        val fileName = url.substringBefore('?').substringAfterLast('/')
        if (!UpdateChecker.isTrustedApkAssetName(fileName)) throw UpdateException("下载文件名无效")
        targetDir.mkdirs()
        val target = File(targetDir, fileName)
        // Write beside the verified package, not over it: a cancelled or failed download must
        // leave the previously verified APK (and its install entry) intact.
        val temp = File(targetDir, "$fileName.${UUID.randomUUID()}.part")
        val response = try {
            streamOpener(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UpdateException) {
            throw e
        } catch (_: Exception) {
            throw UpdateException("下载连接失败，请检查网络后重试")
        }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8_192)
            val total = response.contentLength
            var received = 0L
            var lastReported = -1L
            FileOutputStream(temp).use { output ->
                response.stream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        received += read
                        if (shouldReport(lastReported, received, total)) {
                            currentCoroutineContext().ensureActive()
                            lastReported = received
                            onProgress(received, total, viaAcceleratedSource)
                        }
                    }
                }
            }
            if (total > 0 && received != total) throw UpdateException("下载不完整，请重试")
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedSha256 != null && !actual.equals(expectedSha256, ignoreCase = true)) {
                throw UpdateException("安装包校验失败，已删除")
            }
            replaceVerified(target, temp)
            // Older versions are only removed after the new package is verified and in place.
            targetDir.listFiles()?.filter {
                it.isFile && it.name.endsWith(".apk") && it.name != target.name
            }?.forEach { it.delete() }
            return DownloadedApk(target, expectedSha256 != null, viaAcceleratedSource)
        } catch (e: CancellationException) {
            temp.delete()
            throw e
        } catch (e: UpdateException) {
            temp.delete()
            throw e
        } catch (_: IOException) {
            temp.delete()
            throw UpdateException("下载连接中断，请重试")
        } catch (_: Exception) {
            temp.delete()
            throw UpdateException("下载失败，请重试")
        } finally {
            response.disconnect()
        }
    }

    private fun replaceVerified(target: File, temp: File) {
        try {
            // Atomic on the same filesystem; replaces the old package only when the new one
            // has been fully written and verified.
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            throw UpdateException("安装包替换失败，请重试")
        }
    }

    private fun shouldReport(lastReported: Long, received: Long, total: Long): Boolean {
        if (lastReported < 0) return true
        if (received == total) return true
        if (total <= 0) return received - lastReported >= 256L * 1024
        return received - lastReported >= maxOf(total / 100, 64L * 1024)
    }
}

internal fun openHttpStream(url: String, timeoutMillis: Int): DownloadStream {
    val connection = try {
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.android.package-archive, */*")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", "wanglemai-android")
        }
    } catch (_: Exception) {
        throw UpdateException("下载地址无效")
    }
    try {
        val code = connection.responseCode
        return when (code) {
            200 -> HttpDownloadStream(connection)
            404 -> throw UpdateException("下载文件不存在，可能还没有发布")
            403, 429 -> throw UpdateException("下载源暂时限制请求，请稍后再试")
            else -> throw UpdateException("下载失败（HTTP $code）")
        }
    } catch (e: UpdateException) {
        connection.disconnect()
        throw e
    } catch (_: IOException) {
        connection.disconnect()
        throw UpdateException("下载连接失败，请检查网络后重试")
    }
}

private class HttpDownloadStream(private val connection: HttpURLConnection) : DownloadStream {
    override val contentLength: Long = connection.contentLengthLong
    override fun stream(): InputStream = connection.inputStream
    override fun disconnect() = connection.disconnect()
}
