package com.healthcompanion.app.ui.state

/**
 * The four states every data-backed screen needs to distinguish.
 *
 * Before this existed the app collapsed every outcome into a blank space: a
 * revoked permission, a `SecurityException` and "the user genuinely has no
 * records" all rendered as the same "No data" line. `Empty` means the read
 * genuinely succeeded and found nothing; `Error` means the read itself failed
 * and offers a retry.
 */
sealed interface UiState<out T> {
    /** Read in progress. */
    data object Loading : UiState<Nothing>

    /** Read succeeded and produced data. */
    data class Content<out T>(val data: T) : UiState<T>

    /** Read succeeded and found nothing. [reason] is user-facing copy. */
    data class Empty(val reason: String) : UiState<Nothing>

    /** Read failed. [cause] is for logging; [retry] re-runs it. */
    data class Error(val cause: Throwable, val retry: () -> Unit) : UiState<Nothing>

    val isLoading: Boolean get() = this is Loading

    fun dataOrNull(): T? = (this as? Content)?.data
}

/**
 * Permission state, tracked as counts rather than a boolean.
 *
 * `granted.isNotEmpty()` reported "connected" after a single grant out of ~30,
 * which made a partial grant indistinguishable from a complete one.
 */
data class PermissionState(
    val grantedCount: Int,
    val requestedCount: Int
) {
    val isFullyGranted: Boolean get() = requestedCount > 0 && grantedCount >= requestedCount
    val isPartiallyGranted: Boolean get() = grantedCount > 0 && !isFullyGranted
    val isNoneGranted: Boolean get() = grantedCount == 0

    /** Whether any real Health Connect data can be read at all. */
    val canReadAnything: Boolean get() = grantedCount > 0

    companion object {
        val Unknown = PermissionState(0, 0)
    }
}

/** Availability of Health Connect itself, which is separate from permissions. */
enum class HealthConnectAvailability {
    AVAILABLE,
    NOT_INSTALLED,
    UPDATE_REQUIRED,
    UNKNOWN
}
