package io.github.staminacode.fallbackobservation

import org.slf4j.LoggerFactory

/**
 * Describes a fallback that is about to be executed.
 *
 * @property caseName A stable, logical fallback name. When an observer exports this value as a
 *   metric tag, use a small, bounded set of names such as `product.load`. Do not include request
 *   IDs, user IDs, tenant IDs, or other unbounded values, as they create high-cardinality metrics.
 * @property exception The primary exception, or [PrimaryOperationBypassedException] when [origin]
 *   is [FallbackOrigin.PRIMARY_BYPASS].
 */
data class FallbackEvent(
    val caseName: String,
    val exception: Exception,
    val origin: FallbackOrigin = FallbackOrigin.PRIMARY_EXCEPTION,
)

/** Indicates whether a fallback was caused by a primary exception or an explicit bypass. */
enum class FallbackOrigin {
  PRIMARY_EXCEPTION,
  PRIMARY_BYPASS,
}

/** Describes a primary operation that completed successfully. */
data class OperationSuccessEvent(
    val caseName: String,
)

/**
 * Describes an error that escaped without a successful fallback result.
 *
 * @property fallbackFailed Whether a fallback was invoked but failed itself. In that case,
 *   [exception] is the original primary exception when one exists, or the fallback exception when
 *   the primary operation was bypassed.
 */
data class OperationErrorEvent(
    val caseName: String,
    val exception: Exception,
    val fallbackFailed: Boolean = false,
)

/**
 * Observes fallbacks immediately before their fallback function is executed.
 *
 * Callbacks execute synchronously on the calling thread and contribute directly to the operation's
 * latency. Keep callbacks fast; delegate slow or blocking work to asynchronous processing and
 * return promptly without waiting for it to finish. The observer is responsible for managing that
 * processing, including failures that occur after the callback returns.
 *
 * When invoked through [FallbackObserverRegistry], any [Exception] escaping a synchronous callback
 * is caught and logged without changing the operation's outcome or preventing other observers from
 * receiving the event. JVM [Error] instances are not intercepted. Failures in asynchronous work
 * remain the observer's responsibility.
 */
fun interface FallbackObserver {
  fun onFallback(event: FallbackEvent)

  companion object {
    /** Logs each fallback at warning level, including the exception that triggered it. */
    val LOGGING = FallbackObserver { event ->
      if (event.origin == FallbackOrigin.PRIMARY_BYPASS) {
        logger.warn(
            "Executing fallback after bypassing primary operation for case={}",
            event.caseName,
        )
      } else {
        logger.warn(
            "Executing fallback for case={}",
            event.caseName,
            event.exception,
        )
      }
    }
  }
}

/**
 * Observes successful primary operations as well as fallbacks.
 *
 * A fallback is reported through [onFallback], inherited from [FallbackObserver]. A primary
 * operation that succeeds is reported through [onSuccess]. Errors that escape without a fallback
 * result are not reported at this level; use [OperationObserver] when they are needed.
 *
 * The callback execution, exception isolation, and asynchronous processing contract described in
 * [FallbackObserver] also applies to [onSuccess].
 */
interface FallbackAwareOperationObserver : FallbackObserver {
  fun onSuccess(event: OperationSuccessEvent)
}

/**
 * Observes every terminal outcome of an operation: primary success, fallback, or an escaped error.
 *
 * An error reported through [onError] is one for which no successful fallback result was produced.
 *
 * The callback execution, exception isolation, and asynchronous processing contract described in
 * [FallbackObserver] also applies to [onError].
 */
interface OperationObserver : FallbackAwareOperationObserver {
  fun onError(event: OperationErrorEvent)
}

private val logger = LoggerFactory.getLogger(FallbackObserver::class.java)
