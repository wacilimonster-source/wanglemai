package com.wanglemai.app.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {
    private val releaseBase = "https://github.com/wacilimonster-source/wanglemai/releases/tag/v0.2.1"
    private val assetBase = "https://github.com/wacilimonster-source/wanglemai/releases/download/v0.2.1"

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
                put(JSONObject().put("name", name).put("browser_download_url", url))
            }
        }).toString()

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

    @Test fun uppercaseApkExtensionIsRecognized() {
        val result = UpdateChecker.parseLatestRelease(
            json(assets = listOf("APP.APK" to "$assetBase/APP.APK")), "0.2.0"
        )
        assertEquals("$assetBase/APP.APK", result.apkUrl)
    }
}
