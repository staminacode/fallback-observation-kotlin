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
