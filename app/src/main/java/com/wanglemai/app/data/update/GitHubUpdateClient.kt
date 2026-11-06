package com.wanglemai.app.data.update

import com.wanglemai.app.domain.UpdateCheckResult
import com.wanglemai.app.domain.UpdateChecker
import com.wanglemai.app.domain.UpdateException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL

class GitHubUpdateClient(
    private val currentVersion: String,
    private val apiUrl: String = UpdateChecker.LATEST_RELEASE_API,
    private val mirrorManifestUrl: String = UpdateChecker.MIRROR_MANIFEST_URL,
    private val timeoutMillis: Int = 6_000,
    private val textFetcher: ((String) -> String)? = null
) {
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            fetch(apiUrl) { UpdateChecker.parseLatestRelease(it, currentVersion) }
        } catch (_: UpdateException) {
            // GitHub is canonical; jsDelivr is only a reachability fallback.
            try {
                fetch(cacheBusted(mirrorManifestUrl)) {
                    UpdateChecker.parseManifest(it, currentVersion)
                }
            } catch (_: UpdateException) {
                throw UpdateException("GitHub 与国内加速源均不可用，请检查网络后重试")
            }
        }
    }

    private fun fetch(url: String, parser: (String) -> UpdateCheckResult): UpdateCheckResult {
        textFetcher?.let { fetcher ->
            val body = try {
                fetcher(url)
            } catch (e: UpdateException) {
                throw e
            } catch (_: Exception) {
                throw UpdateException("网络连接失败，请检查网络后重试")
            }
            return parser(body)
        }
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMillis
                readTimeout = timeoutMillis
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json, application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "wanglemai-android")
            }
        } catch (_: Exception) {
            throw UpdateException("更新地址无效")
        }
        try {
            val code = connection.responseCode
            when (code) {
                200 -> {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                        readLimited(it, 2 * 1024 * 1024)
                    }
                    return parser(body)
                }
                404 -> throw UpdateException("仓库还没有发布版本")
                403, 429 -> throw UpdateException("GitHub 暂时限制请求，请稍后再试")
                else -> throw UpdateException("检查更新失败（HTTP $code）")
            }
        } catch (e: UpdateException) {
            throw e
        } catch (_: IOException) {
            throw UpdateException("网络连接失败，请检查网络后重试")
        } finally {
            connection.disconnect()
        }
    }

    private fun cacheBusted(url: String): String {
        val separator = if (url.contains('?')) '&' else '?'
        return "$url${separator}t=${System.currentTimeMillis()}"
    }

    private fun readLimited(reader: Reader, maxChars: Int): String {
        val builder = StringBuilder()
        val buffer = CharArray(8_192)
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            builder.append(buffer, 0, read)
            if (builder.length > maxChars) throw UpdateException("更新信息过大")
        }
        return builder.toString()
    }
}
