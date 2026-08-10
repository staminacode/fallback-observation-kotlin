package io.github.staminacode.fallbackobservation

import java.util.concurrent.CopyOnWriteArrayList
import org.slf4j.LoggerFactory

/**
 * Holds the observers notified about fallback-related operation outcomes.
 *
 * A registry is typically shared by the [FallbackFactory] and all fallback policies created from
 * it. Observers can be registered temporarily, for example by a test extension.
 */
class FallbackObserverRegistry(
    observers: Collection<FallbackObserver> = listOf(FallbackObserver.LOGGING)
) {
  private val fallbackObservers = CopyOnWriteArrayList<FallbackObserver>()
  private val fallbackAwareOperationObservers =
      CopyOnWriteArrayList<FallbackAwareOperationObserver>()
  private val operationObservers = CopyOnWriteArrayList<OperationObserver>()

  init {
    observers.forEach(::add)
  }

  constructor(observer: FallbackObserver) : this(listOf(observer))

  /**
   * Registers [observer] and returns a handle that removes it when closed.
   *
   * The registration is safe to use while fallbacks are being executed.
   */
  fun register(observer: FallbackObserver): FallbackObserverRegistration {
    add(observer)
    return FallbackObserverRegistration { remove(observer) }
  }

  /** Records that a fallback is about to be executed for [caseName]. */
  fun recordFallback(caseName: String, exception: Exception) {
    requireCaseName(caseName)
    fallbackObservers.forEach { observer ->
      observer.onFallback(FallbackEvent(caseName, exception))
    }
  }

  /** Records a successful primary operation for observers interested in operation outcomes. */
  fun recordSuccess(caseName: String) {
    requireCaseName(caseName)
    fallbackAwareOperationObservers.forEach { observer ->
      observer.onSuccess(OperationSuccessEvent(caseName))
    }
  }

  /** Records an error that escaped without a successful fallback result. */
  fun recordError(caseName: String, exception: Exception) {
    requireCaseName(caseName)
    operationObservers.forEach { observer ->
      observer.onError(OperationErrorEvent(caseName, exception))
    }
  }

  /**
   * Records a fallback that failed while executing.
   *
   * The [fallbackException] is logged, while observers receive the original [primaryException] with
   * `fallbackFailed = true`.
   */
  fun recordFallbackFailure(
      caseName: String,
      primaryException: Exception,
      fallbackException: Exception,
  ) {
    requireCaseName(caseName)
    logger.error(
        "Fallback failed for case={}; rethrowing the original exception",
        caseName,
        fallbackException,
    )
    operationObservers.forEach { observer ->
      observer.onError(
          OperationErrorEvent(caseName, primaryException, fallbackFailed = true),
      )
    }
  }

  private fun add(observer: FallbackObserver) {
    fallbackObservers += observer
    if (observer is FallbackAwareOperationObserver) {
      fallbackAwareOperationObservers += observer
    }
    if (observer is OperationObserver) {
      operationObservers += observer
    }
  }

  private fun remove(observer: FallbackObserver) {
    fallbackObservers.remove(observer)
    if (observer is FallbackAwareOperationObserver) {
      fallbackAwareOperationObservers.remove(observer)
    }
    if (observer is OperationObserver) {
      operationObservers.remove(observer)
    }
  }

  private fun requireCaseName(caseName: String) {
    require(caseName.isNotBlank()) { "Fallback case name cannot be blank" }
  }

  private companion object {
    private val logger = LoggerFactory.getLogger(FallbackObserverRegistry::class.java)
  }
}

/** Removes a previously registered [FallbackObserver]. */
fun interface FallbackObserverRegistration : AutoCloseable {
  override fun close()
}
