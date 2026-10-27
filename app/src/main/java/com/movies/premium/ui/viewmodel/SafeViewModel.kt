package com.movies.premium.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movies.premium.util.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * The base class of every view model in the app.
 *
 * **Why it exists.** `viewModelScope` is a `SupervisorJob` scope, and a coroutine
 * that fails inside it passes its exception to the *global* uncaught-exception
 * handler — which kills the process. A single failing HTTP request was enough
 * to close the app: opening a title called the stream backend, the backend
 * answered `404`, Retrofit turned that into an `HttpException`, and the whole
 * app went down with it.
 *
 * [scope] is the same scope with a [CoroutineExceptionHandler] attached, so a
 * failure in the background is logged and the app stays alive. Cancellation is
 * still rethrown, so a cancelled coroutine behaves the way it should.
 *
 * Use [scope] instead of `viewModelScope` in the subclasses.
 */
abstract class SafeViewModel : ViewModel() {

    /**
     * The scope the subclasses use.
     *
     * It is [viewModelScope] with a logging exception handler: the job and the
     * dispatcher are the original's, only the failure handling changes.
     */
    protected val scope: CoroutineScope = viewModelScope +
        CoroutineExceptionHandler { _, throwable ->
            Logger.e(TAG, "Unhandled error in ${this@SafeViewModel.javaClass.simpleName}", throwable)
        }

    /**
     * Launches [block] in [scope] and logs anything it throws.
     *
     * The explicit `try`/`catch` is on top of the handler, because a failure
     * inside a `launch` would otherwise be swallowed without the view model
     * knowing about it.
     */
    protected fun launchSafely(block: suspend CoroutineScope.() -> Unit) {
        scope.launch { block() }
    }

    companion object {
        private const val TAG = "SafeViewModel"
    }
}
