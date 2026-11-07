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
    val isNewer: Boolean,
    val sha256: String? = null,
    val apkMirrorUrl: String? = null,
    val viaAcceleratedSource: Boolean = false
)

class UpdateException(override val message: String) : Exception(message)

/**
 * Reads the latest public GitHub Release for this repository. No token or account is used.
 * The APK is downloaded inside the app (jsDelivr mirror first, GitHub fallback) and its
 * SHA256 is verified while streaming; installation only starts after the user confirms.
 *
 * A static manifest on jsDelivr is used only as a fallback when api.github.com is unreachable.
 */
object UpdateChecker {
    const val REPO = "wacilimonster-source/wanglemai"
    const val RELEASES_URL = "https://github.com/$REPO/releases"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO/releases/latest"
    const val MIRROR_MANIFEST_URL = "https://cdn.jsdelivr.net/gh/$REPO@main/update/latest.json"
    const val RELEASE_PREFIX = "https://github.com/$REPO/releases/"
    const val MIRROR_PREFIX = "https://cdn.jsdelivr.net/gh/$REPO@"
    private const val MANIFEST_SCHEMA_VERSION = 1
    private val SAFE_TAG = Regex("^[A-Za-z0-9._-]{1,64}$")
    private val SAFE_ASSET_NAME = Regex("^[A-Za-z0-9._-]+\\.apk$")
    private val SHA256 = Regex("^[0-9a-fA-F]{64}$")

    /** True when [url] points at this repository's GitHub release downloads or its jsDelivr mirror. */
    fun isTrustedDownloadUrl(url: String): Boolean =
        url.startsWith("${RELEASE_PREFIX}download/") || url.startsWith(MIRROR_PREFIX)

    /** True when [name] matches the APK asset naming the updater accepts. */
    fun isTrustedApkAssetName(name: String): Boolean = SAFE_ASSET_NAME.matches(name)

    fun parseLatestRelease(json: String, currentVersion: String): UpdateCheckResult {
        val root = parseRoot(json)
        val tag = root.optString("tag_name").trim()
        if (tag.isEmpty()) throw UpdateException("发行版缺少版本号")
        val releaseUrl = root.optString("html_url").trim()
        if (!releaseUrl.startsWith(RELEASE_PREFIX)) {
            throw UpdateException("发行版链接无效")
        }
        val releaseName = root.optString("name").trim().ifEmpty { tag }
        val publishedAt = root.optString("published_at").trim().ifEmpty { null }

        var apkUrl: String? = null
        var apkName: String? = null
        var sha256: String? = null
        val assets = root.optJSONArray("assets")
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url").trim()
                if (name.endsWith(".apk", ignoreCase = true) &&
                    url.startsWith("${RELEASE_PREFIX}download/")) {
                    apkUrl = url
                    apkName = name
                    sha256 = parseDigest(asset.optString("digest"))
                    break
                }
            }
        }
        val mirror = if (apkName != null && SAFE_TAG.matches(tag) &&
            SAFE_ASSET_NAME.matches(apkName)) {
            "$MIRROR_PREFIX$tag/releases/$apkName"
        } else null
        return UpdateCheckResult(
            currentVersion = currentVersion,
            latestVersion = tag,
            releaseName = releaseName,
            releaseUrl = releaseUrl,
            apkUrl = apkUrl,
            publishedAt = publishedAt,
            isNewer = compareVersions(tag, currentVersion) > 0,
            sha256 = sha256,
            apkMirrorUrl = mirror
        )
    }

    /** Parses the static jsDelivr manifest used when the GitHub API is unavailable. */
    fun parseManifest(json: String, currentVersion: String): UpdateCheckResult {
        val root = parseRoot(json)
        if (root.optInt("schemaVersion", -1) != MANIFEST_SCHEMA_VERSION) {
            throw UpdateException("更新清单版本不支持")
        }
        val version = root.optString("version").trim()
        if (version.isEmpty()) throw UpdateException("更新清单缺少版本号")
        val releaseUrl = root.optString("releaseUrl").trim()
        if (!releaseUrl.startsWith(RELEASE_PREFIX)) throw UpdateException("更新清单链接无效")
        val apkUrl = root.optString("apkUrl").trim().takeIf { it.isNotEmpty() }
        if (apkUrl != null && !apkUrl.startsWith("${RELEASE_PREFIX}download/")) {
            throw UpdateException("更新清单 APK 链接无效")
        }
        val mirror = root.optString("apkMirrorUrl").trim().takeIf { it.isNotEmpty() }
        if (mirror != null && !mirror.startsWith(MIRROR_PREFIX)) {
            throw UpdateException("更新清单加速链接无效")
        }
        val sha = root.optString("sha256").trim().takeIf { it.isNotEmpty() }
        if (sha != null && !SHA256.matches(sha)) throw UpdateException("更新清单校验值无效")
        return UpdateCheckResult(
            currentVersion = currentVersion,
            latestVersion = version,
            releaseName = root.optString("releaseName").trim().ifEmpty { version },
            releaseUrl = releaseUrl,
            apkUrl = apkUrl,
            publishedAt = root.optString("publishedAt").trim().ifEmpty { null },
            isNewer = compareVersions(version, currentVersion) > 0,
            sha256 = sha?.lowercase(),
            apkMirrorUrl = mirror,
            viaAcceleratedSource = true
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

    private fun parseRoot(json: String): JSONObject = try {
        JSONObject(json)
    } catch (_: JSONException) {
        throw UpdateException("更新信息格式无效")
    }

    private fun parseDigest(value: String): String? {
        val digest = value.trim()
        if (!digest.startsWith("sha256:", ignoreCase = true)) return null
        return digest.substringAfter(':').takeIf { SHA256.matches(it) }?.lowercase()
    }

    private fun numericParts(value: String): List<Int>? {
        val match = Regex("^[vV]?([0-9]+(?:\\.[0-9]+)*)").find(value.trim()) ?: return null
        return match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
    }
}
