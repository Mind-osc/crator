package com.crator.launcher

import kotlin.coroutines.cancellation.CancellationException

/** Σαν το runCatching, αλλά αφήνει την ακύρωση να περάσει — αλλιώς η coroutine δεν σταματά σωστά */
suspend inline fun <R> suspendRunCatching(block: () -> R): Result<R> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
