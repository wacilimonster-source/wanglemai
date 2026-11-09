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
        val viaAcceleratedSource: Boolean,
        val version: String = ""
    ) : ApkDownloadState
    data class Failed(
        val message: String,
        val previous: Ready? = null
    ) : ApkDownloadState
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
    private val downloader: ApkDownloader = ApkDownloader(),
    private val checkLatest: suspend (String) -> UpdateCheckResult = {
        GitHubUpdateClient(it).check()
    }
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
                val checked = checkLatest(currentVersion)
                discardDownloadFromOlderVersion(checked.latestVersion)
                result.value = checked
            } catch (e: UpdateException) {
                checkError.value = e.message
            } catch (_: Exception) {
                checkError.value = "检查更新失败，请稍后重试"
            } finally {
                checking.value = false
            }
        }
    }

    /** A package downloaded for another release must never be offered as the current one. */
    private fun discardDownloadFromOlderVersion(latestVersion: String) {
        when (val state = download.value) {
            is ApkDownloadState.Ready -> {
                if (state.version.isNotEmpty() && state.version != latestVersion) {
                    download.value = ApkDownloadState.Idle
                    runCatching { state.file.delete() }
                }
            }
            is ApkDownloadState.Failed -> {
                val previous = state.previous
                if (previous != null && previous.version.isNotEmpty() &&
                    previous.version != latestVersion) {
                    download.value = state.copy(previous = null)
                    runCatching { previous.file.delete() }
                }
            }
            else -> Unit
        }
    }

    fun downloadApk(
        mirrorUrl: String?,
        githubUrl: String?,
        expectedSha256: String?,
        version: String = ""
    ) {
        if (mirrorUrl == null && githubUrl == null) return
        downloadJob?.cancel()
        // Keep a verified package from the previous attempt. A failed re-download restores it;
        // a successful one replaces it after the downloaded bytes pass verification.
        val previous = when (val state = download.value) {
            is ApkDownloadState.Ready -> state
            is ApkDownloadState.Failed -> state.previous
            else -> null
        }?.takeIf { it.file.exists() }
        download.value = ApkDownloadState.Downloading(0, 0, viaAcceleratedSource = mirrorUrl != null)
        downloadJob = scope.launch {
            val self = coroutineContext[Job]
            fun finish(state: ApkDownloadState) {
                // Only the still-current job may write; a cancelled run must not clobber
                // the state of a newer download (or of the user's cancel).
                if (downloadJob === self) {
                    download.value = if (isActive) state else previous ?: ApkDownloadState.Idle
                }
            }
            fun fail(message: String) = finish(ApkDownloadState.Failed(message, previous))
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
                    downloaded.file, downloaded.sha256Verified, downloaded.viaAcceleratedSource,
                    version
                ))
                events.tryEmit(UpdateEvent.Downloaded(downloaded.file, downloaded.sha256Verified))
            } catch (e: CancellationException) {
                finish(ApkDownloadState.Idle)
                throw e
            } catch (e: UpdateException) {
                fail(e.message)
            } catch (_: Exception) {
                fail("下载失败，请重试")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }
}
