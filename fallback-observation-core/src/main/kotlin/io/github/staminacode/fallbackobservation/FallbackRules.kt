package io.github.staminacode.fallbackobservation

import kotlin.reflect.KClass

internal class FallbackRules(
    private val caseName: String,
    private val handledExceptions: Set<KClass<out Exception>>,
    private val passThroughExceptions: Set<KClass<out Exception>>,
    private val observer: FallbackObserver,
) {
    fun <O> execute(operation: () -> O, fallback: (Exception) -> O): O =
        try {
            operation()
        } catch (exception: Exception) {
            when {
                exception.matches(passThroughExceptions) -> throw exception
                exception.matches(handledExceptions) -> {
                    observer.onFallback(FallbackEvent(caseName, exception))
                    fallback(exception)
                }
                else -> throw exception
            }
        }

    private fun Exception.matches(types: Set<KClass<out Exception>>) =
        types.any { it.isInstance(this) }
}
