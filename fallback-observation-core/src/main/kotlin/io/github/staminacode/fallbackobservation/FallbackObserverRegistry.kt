package io.github.staminacode.fallbackobservation

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Holds the observers notified when a fallback is executed.
 *
 * A registry is typically shared by the [FallbackFactory] and all fallback policies created from
 * it. Observers can be registered temporarily, for example by a test extension.
 */
class FallbackObserverRegistry(
    observers: Collection<FallbackObserver> = listOf(FallbackObserver.LOGGING)
) {
  constructor(observer: FallbackObserver) : this(listOf(observer))

  /**
   * Registers [observer] and returns a handle that removes it when closed.
   *
   * The registration is safe to use while fallbacks are being executed.
   */
  fun register(observer: FallbackObserver): FallbackObserverRegistration {
    observers += observer
    return FallbackObserverRegistration { observers.remove(observer) }
  }

  internal fun onFallback(fallbackEvent: FallbackEvent) {
    observers.forEach { observer -> observer.onFallback(fallbackEvent) }
  }

  private val observers = CopyOnWriteArrayList(observers)
}

/** Removes a previously registered [FallbackObserver]. */
fun interface FallbackObserverRegistration : AutoCloseable {
  override fun close()
}
