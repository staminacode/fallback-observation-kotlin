package io.github.staminacode.fallbackobservation

/**
 * This is used to keep track of the observers to be used in such a way that we don't have to
 * enforce a singleton from the lib.
 *
 * TODO: update this comment, which currently is more intended for the lib development and not for
 *   the use
 */
class FallbackObserverRegistry(
    observers: Collection<FallbackObserver> = listOf(FallbackObserver.LOGGING)
) {
  constructor(observer: FallbackObserver) : this(listOf(observer))

  internal fun onFallback(fallbackEvent: FallbackEvent) {
    observers.forEach { observer -> observer.onFallback(fallbackEvent) }
  }

  private val observers = mutableListOf(*observers.toTypedArray())
}
