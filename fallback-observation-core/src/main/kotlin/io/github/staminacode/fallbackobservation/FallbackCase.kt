package io.github.staminacode.fallbackobservation

/**
 * A reusable fallback policy whose primary operation and fallback function are supplied at each
 * call.
 *
 * Use this type when several call sites share the same name, exception rules, and observer, but
 * execute different operations. For an operation whose functions are configured once and invoked
 * repeatedly, use [ResilientOperation] instead.
 *
 * Create a case once and reuse it, typically as a long-lived `val` or singleton. Do not create a
 * new case for every call; its configuration is intended to be shared by all executions of the same
 * logical operation.
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

/**
 * Configures a reusable fallback policy.
 *
 * The case name must be stable and low-cardinality when exported as a metric tag. Use a logical
 * operation name such as `product.load`; never include request IDs, user IDs, tenant IDs, or other
 * unbounded values.
 */
class FallbackCaseBuilder internal constructor(observerRegistry: FallbackObserverRegistry) :
    AbstractFallbackBuilder(observerRegistry)
