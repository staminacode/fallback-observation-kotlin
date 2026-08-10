package io.github.staminacode.fallbackobservation

import java.util.concurrent.CopyOnWriteArrayList

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

  internal fun onFallback(fallbackEvent: FallbackEvent) {
    fallbackObservers.forEach { observer -> observer.onFallback(fallbackEvent) }
  }

  internal fun onSuccess(successEvent: OperationSuccessEvent) {
    fallbackAwareOperationObservers.forEach { observer -> observer.onSuccess(successEvent) }
  }

  internal fun onError(errorEvent: OperationErrorEvent) {
    operationObservers.forEach { observer -> observer.onError(errorEvent) }
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
}

/** Removes a previously registered [FallbackObserver]. */
fun interface FallbackObserverRegistration : AutoCloseable {
  override fun close()
}
