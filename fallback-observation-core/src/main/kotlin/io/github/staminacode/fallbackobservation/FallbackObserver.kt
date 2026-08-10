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

/** Describes a primary operation that completed successfully. */
data class OperationSuccessEvent(
    val caseName: String,
)

/**
 * Describes an error that escaped without a successful fallback result.
 *
 * @property fallbackFailed Whether a fallback was invoked but failed itself. In that case,
 *   [exception] is still the original exception from the primary operation; the fallback exception
 *   is recorded in the application log.
 */
data class OperationErrorEvent(
    val caseName: String,
    val exception: Exception,
    val fallbackFailed: Boolean = false,
)

/** Observes fallbacks immediately before their fallback function is executed. */
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
  }
}

/**
 * Observes successful primary operations as well as fallbacks.
 *
 * A fallback is reported through [onFallback], inherited from [FallbackObserver]. A primary
 * operation that succeeds is reported through [onSuccess]. Errors that escape without a fallback
 * result are not reported at this level; use [OperationObserver] when they are needed.
 */
interface FallbackAwareOperationObserver : FallbackObserver {
  fun onSuccess(event: OperationSuccessEvent)
}

/**
 * Observes every terminal outcome of an operation: primary success, fallback, or an escaped error.
 *
 * An error reported through [onError] is one for which no successful fallback result was produced.
 */
interface OperationObserver : FallbackAwareOperationObserver {
  fun onError(event: OperationErrorEvent)
}

private val logger = LoggerFactory.getLogger(FallbackObserver::class.java)
