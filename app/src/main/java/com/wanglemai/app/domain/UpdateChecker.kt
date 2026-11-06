package com.wanglemai.app.domain

import org.json.JSONException
import org.json.JSONObject

data class UpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String,
    val releaseName: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val publishedAt: String?,
    val isNewer: Boolean
)

class UpdateException(override val message: String) : Exception(message)

/**
 * Reads the latest public GitHub Release for this repository. No token or account is used.
 * Updates are opened in the browser; nothing is downloaded or installed automatically.
 */
object UpdateChecker {
    const val REPO = "wacilimonster-source/wanglemai"
    const val RELEASES_URL = "https://github.com/$REPO/releases"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val RELEASE_PREFIX = "https://github.com/$REPO/releases/"

    fun parseLatestRelease(json: String, currentVersion: String): UpdateCheckResult {
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            throw UpdateException("更新信息格式无效")
        }
        val tag = root.optString("tag_name").trim()
        if (tag.isEmpty()) throw UpdateException("发行版缺少版本号")
        val releaseUrl = root.optString("html_url").trim()
        if (!releaseUrl.startsWith(RELEASE_PREFIX)) {
            throw UpdateException("发行版链接无效")
        }
        val releaseName = root.optString("name").trim().ifEmpty { tag }
        val publishedAt = root.optString("published_at").trim().ifEmpty { null }

        var apkUrl: String? = null
        val assets = root.optJSONArray("assets")
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url").trim()
                if (name.endsWith(".apk", ignoreCase = true) &&
                    url.startsWith("${RELEASE_PREFIX}download/")) {
                    apkUrl = url
                    break
                }
            }
        }
        return UpdateCheckResult(
            currentVersion = currentVersion,
            latestVersion = tag,
            releaseName = releaseName,
            releaseUrl = releaseUrl,
            apkUrl = apkUrl,
            publishedAt = publishedAt,
            isNewer = compareVersions(tag, currentVersion) > 0
        )
    }

    /** Compares numeric version prefixes; tags like "v0.2.0" and "0.2.0-mvp" compare equal. */
    fun compareVersions(left: String, right: String): Int {
        val a = numericParts(left) ?: return 0
        val b = numericParts(right) ?: return 0
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    private fun numericParts(value: String): List<Int>? {
        val match = Regex("^[vV]?([0-9]+(?:\\.[0-9]+)*)").find(value.trim()) ?: return null
        return match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
    }
}
