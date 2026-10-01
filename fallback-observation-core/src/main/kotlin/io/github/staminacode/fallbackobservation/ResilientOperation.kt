package io.github.staminacode.fallbackobservation

/**
 * A configured primary operation with a fallback strategy.
 *
 * Both functions are supplied when this object is built, then the operation can be invoked
 * repeatedly with different [I] values. Use [FallbackCase] when the operation and fallback
 * functions should instead be supplied at each call site.
 *
 * Create an operation once and reuse it, typically as a long-lived `val` or singleton. Do not
 * create a new operation for every invocation; its configuration is intended to be shared by all
 * executions of the same logical operation.
 */
class ResilientOperation<I, O>
internal constructor(
    private val rules: FallbackRules,
    private val operation: (I) -> O,
    private val fallback: (I, Exception) -> O,
) {
  /** Executes the configured operation for [input], applying the configured fallback rules. */
  operator fun invoke(input: I): O = rules.execute({ operation(input) }) { fallback(input, it) }
}

class ResilientOperationBuilder<I, O>
internal constructor(
    observerRegistry: FallbackObserverRegistry,
    primaryOperationHook: PrimaryOperationHook?,
) : AbstractFallbackBuilder(observerRegistry, primaryOperationHook) {
  private var configuredOperation: ((I) -> O)? = null
  private var configuredFallback: ((I, Exception) -> O)? = null

  fun operation(operation: (I) -> O) {
    check(configuredOperation == null) { "Operation has already been configured" }
    configuredOperation = operation
  }

  fun fallback(fallback: (I, Exception) -> O) {
    check(configuredFallback == null) { "Fallback has already been configured" }
    configuredFallback = fallback
  }

  internal fun build(name: String): ResilientOperation<I, O> =
      ResilientOperation(
          buildRules(name),
          requireNotNull(configuredOperation) {
            "An operation must be configured for resilient operation '$name'"
          },
          requireNotNull(configuredFallback) {
            "A fallback must be configured for resilient operation '$name'"
          },
      )
}

operator fun <O> ResilientOperation<Unit, O>.invoke(): O = invoke(Unit)
