package io.github.staminacode.fallbackobservation

class CompositeFallbackObserver(observers: Iterable<FallbackObserver>) : FallbackObserver {
    private val observers = observers.toList()

    constructor(vararg observers: FallbackObserver) : this(observers.asIterable())

    override fun onFallback(event: FallbackEvent) = observers.forEach { it.onFallback(event) }
}
