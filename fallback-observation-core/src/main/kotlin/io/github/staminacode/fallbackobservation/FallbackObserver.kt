package io.github.staminacode.fallbackobservation

import org.slf4j.LoggerFactory

/**
 * Describes a fallback that is about to be executed.
 *
 * @property caseName A stable, logical fallback name. When an observer exports this value as a
 *   metric tag, use a small, bounded set of names such as `product.load`. Do not include request
 *   IDs, user IDs, tenant IDs, or other unbounded values, as they create high-cardinality metrics.
 */
data class FallbackEvent(
    val caseName: String,
    val exception: Exception,
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

    /** Disables fallback observation explicitly. */
    val NO_OP = FallbackObserver {}
  }
}

private val logger = LoggerFactory.getLogger(FallbackObserver::class.java)
