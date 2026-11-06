package com.wanglemai.app.domain

import org.json.JSONArray
import org.json.JSONObject
import com.wanglemai.app.data.update.GitHubUpdateClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class UpdateCheckerTest {
    private val releaseBase = "https://github.com/wacilimonster-source/wanglemai/releases/tag/v0.2.1"
    private val assetBase = "https://github.com/wacilimonster-source/wanglemai/releases/download/v0.2.1"

    private val sha = "e10616e56212ae80b356936f3881f2bd487ab20362218b0b51055e596527db90"
    private val mirrorUrl = "https://cdn.jsdelivr.net/gh/wacilimonster-source/wanglemai@v0.2.1/releases/wanglemai-0.2.1-debug.apk"

    private fun json(
        tag: String = "v0.2.1",
        html: String = releaseBase,
        assets: List<Pair<String, String>> = listOf(
            "notes.txt" to "$assetBase/notes.txt",
            "wanglemai-0.2.1-debug.apk" to "$assetBase/wanglemai-0.2.1-debug.apk"
        )
    ): String = JSONObject()
        .put("tag_name", tag)
        .put("name", "忘了没 $tag")
        .put("html_url", html)
        .put("published_at", "2026-11-06T06:00:00Z")
        .put("assets", JSONArray().apply {
            assets.forEach { (name, url) ->
                put(JSONObject().put("name", name)
                    .put("browser_download_url", url)
                    .put("digest", if (name.endsWith(".apk", true)) "sha256:$sha" else JSONObject.NULL))
            }
        }).toString()

    private fun manifest(
        version: String = "v0.2.1",
        release: String = releaseBase,
        apk: String = "$assetBase/wanglemai-0.2.1-debug.apk",
        mirror: String = mirrorUrl,
        digest: String = sha
    ): String = JSONObject()
        .put("schemaVersion", 1)
        .put("version", version)
        .put("releaseName", "忘了没 $version")
        .put("releaseUrl", release)
        .put("apkUrl", apk)
        .put("apkMirrorUrl", mirror)
        .put("sha256", digest)
        .put("publishedAt", "2026-11-06T06:00:00Z")
        .toString()

    @Test fun numericVersionComparisonIgnoresPrefixAndSuffix() {
        assertEquals(0, UpdateChecker.compareVersions("v0.2.0", "0.2.0-mvp"))
        assertEquals(0, UpdateChecker.compareVersions("0.2", "v0.2.0"))
        assertTrue(UpdateChecker.compareVersions("0.2.10", "0.2.9") > 0)
        assertTrue(UpdateChecker.compareVersions("v1.0.0", "0.9.9") > 0)
        assertTrue(UpdateChecker.compareVersions("0.1.9", "0.2.0") < 0)
        assertEquals(0, UpdateChecker.compareVersions("not-a-version", "0.2.0"))
    }

    @Test fun parsesLatestReleaseAndFindsApkAsset() {
        val result = UpdateChecker.parseLatestRelease(json(), "0.2.0-mvp")
        assertEquals("v0.2.1", result.latestVersion)
        assertTrue(result.isNewer)
        assertEquals(releaseBase, result.releaseUrl)
        assertEquals("$assetBase/wanglemai-0.2.1-debug.apk", result.apkUrl)
        assertEquals("2026-11-06T06:00:00Z", result.publishedAt)
        assertEquals(sha, result.sha256)
        assertEquals(mirrorUrl, result.apkMirrorUrl)
        assertFalse(result.viaAcceleratedSource)
    }

    @Test fun sameVersionIsNotReportedAsNewer() {
        val result = UpdateChecker.parseLatestRelease(json(tag = "v0.2.0"), "0.2.0-mvp")
        assertFalse(result.isNewer)
        assertEquals("v0.2.0", result.latestVersion)
    }

    @Test fun missingApkAssetIsAllowed() {
        val result = UpdateChecker.parseLatestRelease(json(assets = emptyList()), "0.2.0")
        assertTrue(result.isNewer)
        assertNull(result.apkUrl)
    }

    @Test fun onlyApkAssetsFromThisRepositoryAreAccepted() {
        val result = UpdateChecker.parseLatestRelease(
            json(assets = listOf("wanglemai.apk" to "https://evil.example/wanglemai.apk")),
            "0.2.0"
        )
        assertNull(result.apkUrl)
    }

    @Test fun rejectsReleaseLinksOutsideThisRepository() {
        assertThrows(UpdateException::class.java) {
            UpdateChecker.parseLatestRelease(json(html = "https://github.com/other/repo/releases/tag/v9"),
                "0.2.0")
        }
    }

    @Test fun rejectsMissingTagAndMalformedJson() {
        assertThrows(UpdateException::class.java) {
            UpdateChecker.parseLatestRelease(JSONObject().put("html_url", releaseBase).toString(), "0.2.0")
        }
        assertThrows(UpdateException::class.java) {
            UpdateChecker.parseLatestRelease("{broken", "0.2.0")
        }
    }

    @Test fun parsesFallbackManifest() {
        val result = UpdateChecker.parseManifest(manifest(), "0.2.0")
        assertTrue(result.isNewer)
        assertTrue(result.viaAcceleratedSource)
        assertEquals("v0.2.1", result.latestVersion)
        assertEquals(mirrorUrl, result.apkMirrorUrl)
        assertEquals(sha, result.sha256)
    }

    @Test fun fallbackManifestRejectsBadSchemaAndLinks() {
        assertThrows(UpdateException::class.java) {
            UpdateChecker.parseManifest(manifest().replace("\"schemaVersion\":1", "\"schemaVersion\":2"), "0.2.0")
        }
        assertThrows(UpdateException::class.java) {
            UpdateChecker.parseManifest(manifest(release = "https://evil.example/release"), "0.2.0")
        }
        assertThrows(UpdateException::class.java) {
            UpdateChecker.parseManifest(manifest(mirror = "https://evil.example/wanglemai.apk"), "0.2.0")
        }
        assertThrows(UpdateException::class.java) {
            UpdateChecker.parseManifest(manifest(digest = "not-a-hash"), "0.2.0")
        }
    }

    @Test fun clientUsesGitHubWhenAvailableAndDoesNotTouchMirror() = runBlocking {
        var mirrorHits = 0
        val client = GitHubUpdateClient("0.2.0",
            "https://api.example/latest", "https://mirror.example/latest.json", 1_000) { url ->
            if (url.startsWith("https://api.example")) json()
            else { mirrorHits++; manifest() }
        }
        val result = client.check()
        assertEquals("v0.2.1", result.latestVersion)
        assertFalse(result.viaAcceleratedSource)
        assertEquals(0, mirrorHits)
    }

    @Test fun clientFallsBackToMirrorWhenGitHubIsUnavailable() = runBlocking {
        var mirrorHits = 0
        val client = GitHubUpdateClient("0.2.0",
            "https://api.example/latest", "https://mirror.example/latest.json", 1_000) { url ->
            if (url.startsWith("https://api.example")) throw IOException("blocked")
            else { mirrorHits++; manifest() }
        }
        val result = client.check()
        assertTrue(result.viaAcceleratedSource)
        assertEquals("v0.2.1", result.latestVersion)
        assertEquals(mirrorUrl, result.apkMirrorUrl)
        assertEquals(1, mirrorHits)
    }

    @Test fun clientReportsErrorWhenBothSourcesFail() = runBlocking {
        val client = GitHubUpdateClient("0.2.0",
            "https://api.example/latest", "https://mirror.example/latest.json", 1_000) { url ->
            if (url.startsWith("https://api.example")) throw IOException("blocked")
            else throw IOException("mirror blocked")
        }
        val error = runCatching { client.check() }.exceptionOrNull()
        assertTrue(error is UpdateException)
        assertTrue(error?.message.orEmpty().contains("均不可用"))
    }

    @Test fun uppercaseApkExtensionIsRecognized() {
        val result = UpdateChecker.parseLatestRelease(
            json(assets = listOf("APP.APK" to "$assetBase/APP.APK")), "0.2.0"
        )
        assertEquals("$assetBase/APP.APK", result.apkUrl)
    }
}
