package com.wanglemai.app.data.update

import com.wanglemai.app.domain.UpdateChecker
import com.wanglemai.app.domain.UpdateException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class UpdateCenterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val mirrorUrl = "${UpdateChecker.MIRROR_PREFIX}v0.2.2/releases/wanglemai-0.2.2-debug.apk"
    private val githubUrl = "${UpdateChecker.RELEASE_PREFIX}download/v0.2.2/wanglemai-0.2.2-debug.apk"

    private fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    private fun checkResult(latest: String) = com.wanglemai.app.domain.UpdateCheckResult(
        currentVersion = "0.3.3-mvp",
        latestVersion = latest,
        releaseName = latest,
        releaseUrl = "${UpdateChecker.RELEASE_PREFIX}tag/$latest",
        apkUrl = "${UpdateChecker.RELEASE_PREFIX}download/$latest/wanglemai-0.3.4-debug.apk",
        publishedAt = null,
        isNewer = true
    )

    private class FakeStream(private val data: ByteArray) : DownloadStream {
        override val contentLength: Long = data.size.toLong()
        override fun stream(): InputStream = ByteArrayInputStream(data)
        override fun disconnect() {}
    }

    /** Blocks the first read until released, so the test can cancel mid-download. */
    private class BlockingStream(
        private val started: CountDownLatch,
        private val release: CompletableFuture<*>
    ) : DownloadStream {
        override val contentLength: Long = 100L
        override fun stream(): InputStream = object : InputStream() {
            override fun read(): Int {
                started.countDown()
                try {
                    release.get()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                return -1
            }
        }
        override fun disconnect() {}
    }

    @Test
    fun downloadCompletesToReadyAndFlagsAcceleratedSource() = runBlocking {
        val data = ByteArray(64) { 1 }
        val center = UpdateCenter(
            scope = CoroutineScope(Dispatchers.IO),
            updatesDir = tmp.root,
            downloader = ApkDownloader(streamOpener = { FakeStream(data) })
        )
        center.downloadApk(mirrorUrl, null, sha256(data), "v0.2.2")
        val state = withTimeout(5_000) {
            center.download.first { it is ApkDownloadState.Ready }
        } as ApkDownloadState.Ready
        assertTrue(state.viaAcceleratedSource)
        assertTrue(state.sha256Verified)
        assertEquals("v0.2.2", state.version)
        assertTrue(data.contentEquals(state.file.readBytes()))
    }

    @Test
    fun failedReDownloadKeepsPreviousVerifiedPackage() = runBlocking {
        val data = ByteArray(64) { 4 }
        val attempts = AtomicInteger(0)
        val center = UpdateCenter(
            scope = CoroutineScope(Dispatchers.IO),
            updatesDir = tmp.root,
            downloader = ApkDownloader(streamOpener = {
                if (attempts.getAndIncrement() == 0) FakeStream(data)
                else throw UpdateException("连接失败")
            })
        )
        center.downloadApk(mirrorUrl, null, sha256(data), "v0.2.2")
        val ready = withTimeout(5_000) {
            center.download.first { it is ApkDownloadState.Ready }
        } as ApkDownloadState.Ready
        assertTrue(ready.file.exists())

        center.downloadApk(mirrorUrl, null, sha256(data), "v0.2.2")
        val failed = withTimeout(5_000) {
            center.download.first { it is ApkDownloadState.Failed }
        } as ApkDownloadState.Failed
        assertEquals(ready, failed.previous)
        assertTrue(ready.file.exists())
        assertTrue(tmp.root.listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun cancelReDownloadRestoresPreviousReady() = runBlocking {
        val data = ByteArray(64) { 5 }
        val started = CountDownLatch(1)
        val release = CompletableFuture<Unit>()
        val attempts = AtomicInteger(0)
        val center = UpdateCenter(
            scope = CoroutineScope(Dispatchers.IO),
            updatesDir = tmp.root,
            downloader = ApkDownloader(streamOpener = {
                if (attempts.getAndIncrement() == 0) FakeStream(data)
                else BlockingStream(started, release)
            })
        )
        center.downloadApk(mirrorUrl, null, sha256(data), "v0.2.2")
        val ready = withTimeout(5_000) {
            center.download.first { it is ApkDownloadState.Ready }
        } as ApkDownloadState.Ready

        center.downloadApk(mirrorUrl, null, sha256(data), "v0.2.2")
        assertTrue(started.await(2, TimeUnit.SECONDS))
        center.cancelDownload()
        release.complete(Unit)
        val restored = withTimeout(5_000) {
            center.download.first { it is ApkDownloadState.Ready }
        } as ApkDownloadState.Ready
        assertEquals(ready, restored)
        assertTrue(restored.file.exists())
    }

    @Test
    fun checkDiscardsPackageFromAnotherVersion() = runBlocking {
        val data = ByteArray(64) { 6 }
        val center = UpdateCenter(
            scope = CoroutineScope(Dispatchers.IO),
            updatesDir = tmp.root,
            downloader = ApkDownloader(streamOpener = { FakeStream(data) }),
            checkLatest = { checkResult("v0.2.3") }
        )
        center.downloadApk(mirrorUrl, null, sha256(data), "v0.2.2")
        val ready = withTimeout(5_000) {
            center.download.first { it is ApkDownloadState.Ready }
        } as ApkDownloadState.Ready

        center.check("0.2.2")
        withTimeout(5_000) { center.result.first { it != null } }
        assertEquals(ApkDownloadState.Idle, center.download.value)
        assertFalse(ready.file.exists())
    }

    @Test
    fun failedCheckKeepsReadyPackageAvailable() = runBlocking {
        val data = ByteArray(64) { 7 }
        val center = UpdateCenter(
            scope = CoroutineScope(Dispatchers.IO),
            updatesDir = tmp.root,
            downloader = ApkDownloader(streamOpener = { FakeStream(data) }),
            checkLatest = { throw UpdateException("网络连接失败") }
        )
        center.downloadApk(mirrorUrl, null, sha256(data), "v0.2.2")
        val ready = withTimeout(5_000) {
            center.download.first { it is ApkDownloadState.Ready }
        } as ApkDownloadState.Ready

        center.check("0.2.2")
        withTimeout(5_000) { center.checkError.first { it != null } }
        assertEquals(ready, center.download.value)
        assertTrue(ready.file.exists())
    }

    @Test
    fun fallbackMidDownloadReportsGitHubSource() = runBlocking {
        val data = ByteArray(64) { 2 }
        val center = UpdateCenter(
            scope = CoroutineScope(Dispatchers.IO),
            updatesDir = tmp.root,
            downloader = ApkDownloader(streamOpener = { url ->
                if (url == mirrorUrl) throw UpdateException("连接失败") else FakeStream(data)
            })
        )
        center.downloadApk(mirrorUrl, githubUrl, sha256(data))
        val state = withTimeout(5_000) {
            center.download.first { it is ApkDownloadState.Ready }
        } as ApkDownloadState.Ready
        assertFalse(state.viaAcceleratedSource)
        assertTrue(data.contentEquals(state.file.readBytes()))
    }

    @Test
    fun bothSourcesFailingEndsInFailedState() = runBlocking {
        val center = UpdateCenter(
            scope = CoroutineScope(Dispatchers.IO),
            updatesDir = tmp.root,
            downloader = ApkDownloader(streamOpener = { throw UpdateException("连接失败") })
        )
        center.downloadApk(mirrorUrl, githubUrl, null)
        val state = withTimeout(5_000) {
            center.download.first { it is ApkDownloadState.Failed }
        } as ApkDownloadState.Failed
        assertEquals("加速源与 GitHub 均下载失败，请检查网络后重试", state.message)
    }

    @Test
    fun cancelMidDownloadReturnsToIdleWithoutPartialFile() = runBlocking {
        val started = CountDownLatch(1)
        val release = CompletableFuture<Unit>()
        val center = UpdateCenter(
            scope = CoroutineScope(Dispatchers.IO),
            updatesDir = tmp.root,
            downloader = ApkDownloader(streamOpener = { BlockingStream(started, release) })
        )
        center.downloadApk(mirrorUrl, null, null)
        assertTrue(started.await(2, TimeUnit.SECONDS))
        center.cancelDownload()
        release.complete(Unit)
        withTimeout(5_000) { center.download.first { it is ApkDownloadState.Idle } }
        assertTrue(tmp.root.listFiles().isNullOrEmpty())
    }

    @Test
    fun newDownloadReplacesStateOfCancelledOne() = runBlocking {
        val data = ByteArray(64) { 3 }
        val started = CountDownLatch(1)
        val release = CompletableFuture<Unit>()
        val attempts = AtomicInteger(0)
        val center = UpdateCenter(
            scope = CoroutineScope(Dispatchers.IO),
            updatesDir = tmp.root,
            downloader = ApkDownloader(streamOpener = { url ->
                if (url == mirrorUrl) {
                    val first = attempts.incrementAndGet() == 1
                    if (first) started.countDown()
                    if (first) release.get() // hold the first attempt open until the test says go
                    throw UpdateException("连接失败")
                }
                FakeStream(data)
            })
        )
        center.downloadApk(mirrorUrl, githubUrl, null)
        assertTrue(started.await(2, TimeUnit.SECONDS))
        center.downloadApk(mirrorUrl, githubUrl, sha256(data)) // restart while first attempt hangs
        release.complete(Unit)
        val state = withTimeout(5_000) {
            center.download.first { it is ApkDownloadState.Ready || it is ApkDownloadState.Failed }
        }
        assertTrue(state is ApkDownloadState.Ready)
        assertTrue(data.contentEquals((state as ApkDownloadState.Ready).file.readBytes()))
    }

    @Test
    fun freshCenterStartsWithEmptyState() {
        val center = UpdateCenter(
            scope = CoroutineScope(Dispatchers.IO),
            updatesDir = tmp.root,
            downloader = ApkDownloader(streamOpener = { FakeStream(ByteArray(1)) })
        )
        assertNull(center.result.value)
        assertNull(center.checkError.value)
        assertFalse(center.checking.value)
        assertEquals(ApkDownloadState.Idle, center.download.value)
    }
}
