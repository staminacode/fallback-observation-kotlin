package io.github.staminacode.fallbackobservation

import kotlin.reflect.KClass

internal class FallbackRules(
    private val caseName: String,
    private val handledExceptions: Set<KClass<out Throwable>>,
    private val passThroughExceptions: Set<KClass<out Throwable>>,
    private val observer: FallbackObserver,
) {
    fun <O> execute(operation: () -> O, fallback: (Throwable) -> O): O =
        try {
            operation()
        } catch (exception: Throwable) {
            when {
                exception.matches(passThroughExceptions) -> throw exception
                exception.matches(handledExceptions) -> {
                    observer.onFallback(FallbackEvent(caseName, exception))
                    fallback(exception)
                }
                else -> throw exception
            }
        }

    private fun Throwable.matches(types: Set<KClass<out Throwable>>) =
        types.any { it.isInstance(this) }
}
