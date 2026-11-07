package com.wanglemai.app.data.update

import com.wanglemai.app.domain.UpdateCheckResult
import com.wanglemai.app.domain.UpdateException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Render state of an update download; held process-wide so it survives tab switches. */
sealed interface ApkDownloadState {
    data object Idle : ApkDownloadState
    data class Downloading(
        val received: Long,
        val total: Long,
        val viaAcceleratedSource: Boolean
    ) : ApkDownloadState
    data class Ready(
        val file: File,
        val sha256Verified: Boolean,
        val viaAcceleratedSource: Boolean
    ) : ApkDownloadState
    data class Failed(val message: String) : ApkDownloadState
}

/** One-shot notices for app-wide surfaces (e.g. a download finished while another tab is shown). */
sealed interface UpdateEvent {
    data class Downloaded(val file: File, val sha256Verified: Boolean) : UpdateEvent
}

/**
 * Owns the check/download state for the whole process; the settings screen only renders the
 * [MutableStateFlow]s from here. Downloads run on the application scope and keep going while
 * another tab is shown or the activity is recreated; the Ready file stays until the next download.
 */
class UpdateCenter(
    private val scope: CoroutineScope,
    private val updatesDir: File,
    private val downloader: ApkDownloader = ApkDownloader()
) {
    private var downloadJob: Job? = null

    val checking = MutableStateFlow(false)
    val result = MutableStateFlow<UpdateCheckResult?>(null)
    val checkError = MutableStateFlow<String?>(null)
    val download = MutableStateFlow<ApkDownloadState>(ApkDownloadState.Idle)
    val events = MutableSharedFlow<UpdateEvent>(
        extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Re-checks the latest release; a running download is cancelled because its result goes stale. */
    fun check(currentVersion: String) {
        downloadJob?.cancel()
        checking.value = true
        checkError.value = null
        result.value = null
        scope.launch {
            try {
                result.value = GitHubUpdateClient(currentVersion).check()
            } catch (e: UpdateException) {
                checkError.value = e.message
            } catch (_: Exception) {
                checkError.value = "检查更新失败，请稍后重试"
            } finally {
                checking.value = false
            }
        }
    }

    fun downloadApk(mirrorUrl: String?, githubUrl: String?, expectedSha256: String?) {
        if (mirrorUrl == null && githubUrl == null) return
        downloadJob?.cancel()
        download.value = ApkDownloadState.Downloading(0, 0, viaAcceleratedSource = mirrorUrl != null)
        downloadJob = scope.launch {
            val self = coroutineContext[Job]
            fun finish(state: ApkDownloadState) {
                // Only the still-current job may write; a cancelled run must not clobber
                // the state of a newer download (or of the user's cancel).
                if (downloadJob === self) {
                    download.value = if (isActive) state else ApkDownloadState.Idle
                }
            }
            try {
                val downloaded = downloader.download(
                    apkMirrorUrl = mirrorUrl,
                    apkUrl = githubUrl,
                    expectedSha256 = expectedSha256,
                    targetDir = updatesDir
                ) { received, total, accelerated ->
                    if (isActive) {
                        download.value = ApkDownloadState.Downloading(received, total, accelerated)
                    }
                }
                finish(ApkDownloadState.Ready(
                    downloaded.file, downloaded.sha256Verified, downloaded.viaAcceleratedSource
                ))
                events.tryEmit(UpdateEvent.Downloaded(downloaded.file, downloaded.sha256Verified))
            } catch (e: CancellationException) {
                finish(ApkDownloadState.Idle)
                throw e
            } catch (e: UpdateException) {
                finish(ApkDownloadState.Failed(e.message))
            } catch (_: Exception) {
                finish(ApkDownloadState.Failed("下载失败，请重试"))
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }
}
