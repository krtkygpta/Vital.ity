package com.healthcompanion.app.ui.state

import androidx.compose.material3.SnackbarDuration

/**
 * One-shot user feedback, delivered through a Snackbar instead of a Toast.
 *
 * Toasts are not reliably announced by TalkBack, disappear before a screen-reader
 * user finishes reading them, and cannot carry an action. Every write result —
 * success *and* failure — is reported through here, so a failed Health Connect
 * write is never silent.
 */
sealed interface AppMessage {
    val text: String

    /** A write succeeded. */
    data class Success(override val text: String) : AppMessage

    /** A write failed; [cause] is for logging only. */
    data class Failure(override val text: String, val cause: Throwable? = null) : AppMessage

    /** Neutral information (e.g. permission counts). */
    data class Info(override val text: String) : AppMessage

    /** Failure carrying a retry, e.g. "Couldn't read Health Connect". */
    data class Retryable(
        override val text: String,
        val actionLabel: String,
        val onRetry: () -> Unit
    ) : AppMessage
}

val AppMessage.duration: SnackbarDuration
    get() = when (this) {
        is AppMessage.Retryable -> SnackbarDuration.Long
        is AppMessage.Failure -> SnackbarDuration.Long
        else -> SnackbarDuration.Short
    }
