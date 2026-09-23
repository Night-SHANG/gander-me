package com.arjun.gander.ui.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Null is not loaded. An empty collection is a successful, genuinely empty result. */
data class ContentSnapshot<T : Any>(
    val value: T? = null,
    val refreshing: Boolean = false,
    val failed: Boolean = false,
)

/** Session-owned data; callers and [scope] use the main dispatcher. */
class RetainedContent<T : Any>(
    private val scope: CoroutineScope,
    private val load: suspend () -> T,
) {
    private val mutableSnapshots = MutableStateFlow(ContentSnapshot<T>())
    val snapshots = mutableSnapshots.asStateFlow()
    private var request = 0L
    private var job: Job? = null
    private var closed = false

    fun refresh() {
        if (closed) return
        val currentRequest = ++request
        job?.cancel()
        mutableSnapshots.value = mutableSnapshots.value.copy(refreshing = true, failed = false)
        job = scope.launch {
            try {
                val value = load()
                if (!closed && request == currentRequest) {
                    mutableSnapshots.value = ContentSnapshot(value = value)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!closed && request == currentRequest) {
                    mutableSnapshots.value = mutableSnapshots.value.copy(
                        refreshing = false,
                        failed = true,
                    )
                }
            }
        }
    }

    fun close() {
        closed = true
        request += 1
        job?.cancel()
        job = null
        mutableSnapshots.value = ContentSnapshot()
    }
}
