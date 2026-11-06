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
    private val apiUrl: String = UpdateChecker.LATEST_RELEASE_API
) {
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val connection = try {
            (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
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
                    UpdateChecker.parseLatestRelease(body, currentVersion)
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
