package io.github.staminacode.fallbackobservation

/**
 * A reusable fallback policy whose primary operation and fallback function are supplied at each call.
 *
 * Use this type when several call sites share the same name, exception rules, and observer, but execute
 * different operations. For an operation whose functions are configured once and invoked repeatedly, use
 * [ResilientOperation] instead.
 */
class FallbackCase internal constructor(private val rules: FallbackRules) {
    /**
     * Executes [operation] and invokes [fallback] when the configured rules handle its exception.
     *
     * Pass-through and unhandled exceptions are rethrown.
     */
    fun <O> withFallback(operation: () -> O, fallback: (Exception) -> O): O =
        rules.execute(operation, fallback)
}

class FallbackCaseBuilder internal constructor() : AbstractFallbackBuilder()

/** Creates a [FallbackCase] with the supplied [name] and exception-handling configuration. */
fun fallbackCase(name: String, configure: FallbackCaseBuilder.() -> Unit): FallbackCase =
    FallbackCase(FallbackCaseBuilder().apply(configure).buildRules(name))
