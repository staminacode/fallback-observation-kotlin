package io.github.staminacode.fallbackobservation

import org.slf4j.LoggerFactory

data class FallbackEvent(
    val caseName: String,
    val exception: Throwable,
)

fun interface FallbackObserver {
    fun onFallback(event: FallbackEvent)

    companion object {
        /** Logs each fallback at warning level, including the exception that triggered it. */
        val LOGGING = FallbackObserver { event ->
            logger.warn(
                "Executing fallback for case={}",
                event.caseName,
                event.exception,
            )
        }

        /** The default observer used when no observer is explicitly configured. */
        val DEFAULT = LOGGING

        /** Disables fallback observation explicitly. */
        val NO_OP = FallbackObserver { }
    }
}

private val logger = LoggerFactory.getLogger(FallbackObserver::class.java)
