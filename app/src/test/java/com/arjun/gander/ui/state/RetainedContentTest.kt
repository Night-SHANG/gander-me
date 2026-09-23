package com.arjun.gander.ui.state

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class RetainedContentTest {
    @Test
    fun unloadedIsDifferentFromSuccessfullyLoadedEmpty() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val result = CompletableDeferred<List<String>>()
            val content = RetainedContent(scope) { result.await() }
            content.refresh()
            assertThat(content.snapshots.value.value).isNull()
            assertThat(content.snapshots.value.refreshing).isTrue()
            result.complete(emptyList())
            assertThat(content.snapshots.value.value).isEmpty()
            assertThat(content.snapshots.value.refreshing).isFalse()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun tabReentryAndBackgroundRefreshKeepTheLoadedBooks() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var result = CompletableDeferred(listOf("existing book"))
            val content = RetainedContent(scope) { result.await() }
            content.refresh()
            result = CompletableDeferred()
            content.refresh()
            assertThat(content.snapshots.value.value).containsExactly("existing book")
            result.complete(listOf("renamed book", "imported book"))
            assertThat(content.snapshots.value.value)
                .containsExactly("renamed book", "imported book")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failedRefreshDoesNotPretendTheLibraryIsEmpty() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var fail = false
            val content = RetainedContent(scope) {
                check(!fail)
                listOf("book")
            }
            content.refresh()
            fail = true
            content.refresh()
            assertThat(content.snapshots.value.value).containsExactly("book")
            assertThat(content.snapshots.value.failed).isTrue()
            assertThat(content.snapshots.value.refreshing).isFalse()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun firstLoadFailureIsNotASuccessfullyEmptyLibrary() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val content = RetainedContent<List<String>>(scope) { error("unavailable") }
            content.refresh()
            assertThat(content.snapshots.value.value).isNull()
            assertThat(content.snapshots.value.failed).isTrue()
            assertThat(content.snapshots.value.refreshing).isFalse()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun obsoleteNonCancellableLoadCannotReplaceANewerResult() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val requests = mutableListOf<Continuation<List<String>>>()
            val content = RetainedContent<List<String>>(scope) {
                suspendCoroutine { requests.add(it) }
            }
            content.refresh()
            content.refresh()
            requests[1].resume(listOf("new book"))
            requests[0].resume(listOf("stale book"))
            assertThat(content.snapshots.value.value).containsExactly("new book")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun closingSessionClearsDataAndDiscardsInFlightResults() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val requests = mutableListOf<Continuation<List<String>>>()
            val content = RetainedContent<List<String>>(scope) {
                suspendCoroutine { requests.add(it) }
            }
            content.refresh()
            requests[0].resume(listOf("private book"))
            content.refresh()
            content.close()
            requests[1].resume(listOf("late private book"))
            content.refresh()
            assertThat(requests).hasSize(2)
            assertThat(content.snapshots.value.value).isNull()
            assertThat(content.snapshots.value.refreshing).isFalse()
        } finally {
            scope.cancel()
        }
    }
}
