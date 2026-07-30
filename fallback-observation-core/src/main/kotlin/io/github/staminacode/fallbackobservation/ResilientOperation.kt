package io.github.staminacode.fallbackobservation

/**
 * A configured primary operation with a fallback strategy.
 *
 * Both functions are supplied when this object is built, then the operation can be invoked repeatedly with
 * different [I] values. Use [FallbackCase] when the operation and fallback functions should instead be
 * supplied at each call site.
 */
class ResilientOperation<I, O> internal constructor(
    private val rules: FallbackRules,
    private val operation: (I) -> O,
    private val fallback: (I, Throwable) -> O,
) {
    /** Executes the configured operation for [input], applying the configured fallback rules. */
    operator fun invoke(input: I): O = rules.execute({ operation(input) }) { fallback(input, it) }
}

class ResilientOperationBuilder<I, O> internal constructor() : AbstractFallbackBuilder() {
    private var configuredOperation: ((I) -> O)? = null
    private var configuredFallback: ((I, Throwable) -> O)? = null

    fun operation(operation: (I) -> O) {
        check(configuredOperation == null) { "Operation has already been configured" }
        configuredOperation = operation
    }

    fun fallback(fallback: (I, Throwable) -> O) {
        check(configuredFallback == null) { "Fallback has already been configured" }
        configuredFallback = fallback
    }

    internal fun build(name: String): ResilientOperation<I, O> = ResilientOperation(
        buildRules(name),
        requireNotNull(configuredOperation) { "An operation must be configured for resilient operation '$name'" },
        requireNotNull(configuredFallback) { "A fallback must be configured for resilient operation '$name'" },
    )
}

/** Creates a [ResilientOperation] with its functions and exception-handling rules configured once. */
fun <I, O> resilientOperation(
    name: String,
    configure: ResilientOperationBuilder<I, O>.() -> Unit,
): ResilientOperation<I, O> = ResilientOperationBuilder<I, O>().apply(configure).build(name)

operator fun <O> ResilientOperation<Unit, O>.invoke(): O = invoke(Unit)
