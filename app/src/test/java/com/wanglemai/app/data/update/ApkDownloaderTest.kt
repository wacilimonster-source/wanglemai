package com.wanglemai.app.data.update

import com.wanglemai.app.domain.UpdateChecker
import com.wanglemai.app.domain.UpdateException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class ApkDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val mirrorUrl = "${UpdateChecker.MIRROR_PREFIX}v0.2.2/releases/wanglemai-0.2.2-debug.apk"
    private val githubUrl = "${UpdateChecker.RELEASE_PREFIX}download/v0.2.2/wanglemai-0.2.2-debug.apk"

    private fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private class FakeStream(
        private val data: ByteArray,
        override val contentLength: Long = data.size.toLong()
    ) : DownloadStream {
        override fun stream(): InputStream = ByteArrayInputStream(data)
        override fun disconnect() {}
    }

    /** Serves [cutAt] bytes then behaves as if the connection closed early. */
    private class TruncatedStream(private val data: ByteArray, private val cutAt: Int) : DownloadStream {
        override val contentLength: Long = data.size.toLong()
        override fun stream(): InputStream = object : InputStream() {
            var pos = 0
            override fun read(): Int {
                if (pos >= cutAt) return -1
                return data[pos++].toInt() and 0xff
            }
        }
        override fun disconnect() {}
    }

    /** Serves [cutAt] bytes then throws, simulating a dropped connection mid-body. */
    private class DroppedStream(private val data: ByteArray, private val cutAt: Int) : DownloadStream {
        override val contentLength: Long = data.size.toLong()
        override fun stream(): InputStream = object : InputStream() {
            var pos = 0
            override fun read(): Int {
                if (pos >= cutAt) throw IOException("connection dropped")
                return data[pos++].toInt() and 0xff
            }
        }
        override fun disconnect() {}
    }

    private class FakeOpener(vararg handlers: Pair<String, DownloadStream>) {
        val requested = mutableListOf<String>()
        private val map = handlers.toMap()
        val opener: (String) -> DownloadStream = { url ->
            requested.add(url)
            map[url] ?: throw UpdateException("连接失败")
        }
    }

    @Test
    fun prefersMirrorAndReportsAcceleratedSource() = runBlocking {
        val data = ByteArray(64) { it.toByte() }
        val opener = FakeOpener(mirrorUrl to FakeStream(data), githubUrl to FakeStream(ByteArray(1)))
        val result = ApkDownloader(streamOpener = opener.opener)
            .download(mirrorUrl, githubUrl, sha256(data), tmp.root) { _, _, _ -> }
        assertEquals(mirrorUrl, opener.requested.single())
        assertTrue(result.viaAcceleratedSource)
        assertTrue(result.sha256Verified)
        assertTrue(data.contentEquals(result.file.readBytes()))
    }

    @Test
    fun fallsBackToGitHubWhenMirrorUnreachable() = runBlocking {
        val data = ByteArray(64) { it.toByte() }
        val opener = FakeOpener(githubUrl to FakeStream(data))
        val result = ApkDownloader(streamOpener = opener.opener)
            .download(mirrorUrl, githubUrl, sha256(data), tmp.root) { _, _, _ -> }
        assertEquals(listOf(mirrorUrl, githubUrl), opener.requested)
        assertFalse(result.viaAcceleratedSource)
        assertTrue(data.contentEquals(result.file.readBytes()))
    }

    @Test
    fun fallsBackToGitHubWhenMirrorBodyDropsMidStream() = runBlocking {
        val data = ByteArray(64) { it.toByte() }
        val opener = FakeOpener(
            mirrorUrl to DroppedStream(data, 10),
            githubUrl to FakeStream(data)
        )
        val result = ApkDownloader(streamOpener = opener.opener)
            .download(mirrorUrl, githubUrl, sha256(data), tmp.root) { _, _, _ -> }
        assertEquals(listOf(mirrorUrl, githubUrl), opener.requested)
        assertTrue(data.contentEquals(result.file.readBytes()))
    }

    @Test
    fun bothSourcesFailingGivesCombinedMessage() {
        val opener = FakeOpener()
        try {
            runBlocking {
                ApkDownloader(streamOpener = opener.opener)
                    .download(mirrorUrl, githubUrl, null, tmp.root) { _, _, _ -> }
            }
            throw AssertionError("expected UpdateException")
        } catch (e: UpdateException) {
            assertEquals("加速源与 GitHub 均下载失败，请检查网络后重试", e.message)
        }
        assertEquals(listOf(mirrorUrl, githubUrl), opener.requested)
    }

    @Test
    fun sha256MismatchDeletesFileAndFallsBack() = runBlocking {
        val bad = ByteArray(64) { 1 }
        val good = ByteArray(64) { 2 }
        val opener = FakeOpener(mirrorUrl to FakeStream(bad), githubUrl to FakeStream(good))
        val result = ApkDownloader(streamOpener = opener.opener)
            .download(mirrorUrl, githubUrl, sha256(good), tmp.root) { _, _, _ -> }
        assertTrue(good.contentEquals(result.file.readBytes()))
        assertTrue(tmp.root.listFiles()!!.size == 1)
    }

    @Test
    fun bothSourcesMismatchingReportsVerificationFailure() {
        val bad = ByteArray(64) { 1 }
        val opener = FakeOpener(mirrorUrl to FakeStream(bad), githubUrl to FakeStream(bad))
        try {
            runBlocking {
                ApkDownloader(streamOpener = opener.opener)
                    .download(mirrorUrl, githubUrl, sha256(ByteArray(64) { 2 }), tmp.root) { _, _, _ -> }
            }
            throw AssertionError("expected UpdateException")
        } catch (e: UpdateException) {
            assertEquals("安装包校验失败，已删除", e.message)
        }
        assertTrue(tmp.root.listFiles().isNullOrEmpty())
    }

    @Test
    fun missingSha256SkipsVerification() = runBlocking {
        val data = ByteArray(64) { 3 }
        val opener = FakeOpener(mirrorUrl to FakeStream(data))
        val result = ApkDownloader(streamOpener = opener.opener)
            .download(mirrorUrl, null, null, tmp.root) { _, _, _ -> }
        assertFalse(result.sha256Verified)
        assertTrue(data.contentEquals(result.file.readBytes()))
    }

    @Test
    fun progressIsMonotonicAndEndsAtFullSize() = runBlocking {
        val data = ByteArray(300_000) { (it % 251).toByte() }
        val opener = FakeOpener(mirrorUrl to FakeStream(data))
        val reports = mutableListOf<Pair<Long, Long>>()
        ApkDownloader(streamOpener = opener.opener)
            .download(mirrorUrl, null, sha256(data), tmp.root) { received, total, _ ->
                reports.add(received to total)
            }
        assertTrue(reports.isNotEmpty())
        assertTrue(reports.all { it.second == data.size.toLong() })
        assertTrue(reports.zipWithNext().all { (a, b) -> a.first < b.first })
        assertEquals(data.size.toLong() to data.size.toLong(), reports.last())
    }

    @Test
    fun unknownContentLengthStillDownloads() = runBlocking {
        val data = ByteArray(64) { 5 }
        val opener = FakeOpener(mirrorUrl to FakeStream(data, contentLength = -1L))
        val result = ApkDownloader(streamOpener = opener.opener)
            .download(mirrorUrl, null, sha256(data), tmp.root) { _, _, _ -> }
        assertTrue(data.contentEquals(result.file.readBytes()))
    }

    @Test
    fun truncatedBodyFailsAndDeletesFile() {
        val data = ByteArray(64) { 6 }
        val opener = FakeOpener(mirrorUrl to TruncatedStream(data, 10))
        try {
            runBlocking {
                ApkDownloader(streamOpener = opener.opener)
                    .download(mirrorUrl, null, sha256(data), tmp.root) { _, _, _ -> }
            }
            throw AssertionError("expected UpdateException")
        } catch (e: UpdateException) {
            assertEquals("下载不完整，请重试", e.message)
        }
        assertTrue(tmp.root.listFiles().isNullOrEmpty())
    }

    @Test
    fun untrustedUrlIsRejectedWithoutConnecting() {
        val opener = FakeOpener()
        try {
            runBlocking {
                ApkDownloader(streamOpener = opener.opener)
                    .download(null, "https://evil.example/app.apk", null, tmp.root) { _, _, _ -> }
            }
            throw AssertionError("expected UpdateException")
        } catch (e: UpdateException) {
            assertEquals("下载地址无效", e.message)
        }
        assertTrue(opener.requested.isEmpty())
    }

    @Test
    fun untrustedFileNameIsRejected() {
        val opener = FakeOpener()
        val url = "${UpdateChecker.MIRROR_PREFIX}v0.2.2/releases/evil name.apk"
        try {
            runBlocking {
                ApkDownloader(streamOpener = opener.opener)
                    .download(url, null, null, tmp.root) { _, _, _ -> }
            }
            throw AssertionError("expected UpdateException")
        } catch (e: UpdateException) {
            assertEquals("下载文件名无效", e.message)
        }
        assertTrue(opener.requested.isEmpty())
    }

    @Test
    fun staleApksInTargetDirectoryAreCleaned() = runBlocking {
        val data = ByteArray(64) { 7 }
        File(tmp.root, "wanglemai-0.2.1-debug.apk").writeBytes(ByteArray(1))
        File(tmp.root, "notes.txt").writeText("keep me")
        val opener = FakeOpener(mirrorUrl to FakeStream(data))
        ApkDownloader(streamOpener = opener.opener)
            .download(mirrorUrl, null, sha256(data), tmp.root) { _, _, _ -> }
        assertEquals(listOf("notes.txt", "wanglemai-0.2.2-debug.apk"),
            tmp.root.listFiles()!!.map { it.name }.sorted())
    }

    @Test
    fun failedDownloadKeepsPreviouslyVerifiedApk() {
        val old = File(tmp.root, "wanglemai-0.2.1-debug.apk")
        old.writeBytes(ByteArray(8) { 9 })
        try {
            runBlocking {
                ApkDownloader(streamOpener = FakeOpener().opener)
                    .download(mirrorUrl, null, null, tmp.root) { _, _, _ -> }
            }
            throw AssertionError("expected UpdateException")
        } catch (e: UpdateException) {
            assertEquals("连接失败", e.message)
        }
        assertTrue(old.exists())
        assertEquals(8L, old.length())
        assertTrue(tmp.root.listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun verificationFailureKeepsPreviouslyVerifiedApk() {
        val old = File(tmp.root, "wanglemai-0.2.1-debug.apk")
        old.writeBytes(ByteArray(8) { 9 })
        val data = ByteArray(64) { 1 }
        try {
            runBlocking {
                ApkDownloader(streamOpener = FakeOpener(mirrorUrl to FakeStream(data)).opener)
                    .download(mirrorUrl, null, sha256(ByteArray(64) { 2 }), tmp.root) { _, _, _ -> }
            }
            throw AssertionError("expected UpdateException")
        } catch (e: UpdateException) {
            assertEquals("安装包校验失败，已删除", e.message)
        }
        assertTrue(old.exists())
        assertEquals(8L, old.length())
        assertTrue(tmp.root.listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun noSourcesGivesUsableError() {
        try {
            runBlocking {
                ApkDownloader(streamOpener = { FakeStream(ByteArray(1)) })
                    .download(null, null, null, tmp.root) { _, _, _ -> }
            }
            throw AssertionError("expected UpdateException")
        } catch (e: UpdateException) {
            assertEquals("没有可用的下载地址", e.message)
        }
    }
}
