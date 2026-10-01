package io.github.staminacode.fallbackobservation

/**
 * Creates fallback policies connected to [observerRegistry]. When [primaryOperationHook] is
 * supplied, each policy consults it before running its primary operation. The default `null`
 * preserves the usual exception-driven flow without invoking a hook.
 */
class FallbackFactory(
    private val observerRegistry: FallbackObserverRegistry,
    private val primaryOperationHook: PrimaryOperationHook? = null,
) {
  /**
   * Creates a [FallbackCase] with the supplied [name] and exception-handling configuration.
   *
   * Keep [name] stable and low-cardinality when a metric observer is used. It identifies a logical
   * operation, not an individual request, user, tenant, or resource.
   */
  fun fallbackCase(name: String, configure: FallbackCaseBuilder.() -> Unit): FallbackCase =
      FallbackCase(
          FallbackCaseBuilder(observerRegistry, primaryOperationHook)
              .apply(configure)
              .buildRules(name)
      )

  /**
   * Creates a [ResilientOperation] with its functions and exception-handling rules configured once.
   *
   * Keep [name] stable and low-cardinality when a metric observer is used. It identifies a logical
   * operation, not an individual request, user, tenant, or resource.
   */
  fun <I, O> resilientOperation(
      name: String,
      configure: ResilientOperationBuilder<I, O>.() -> Unit,
  ): ResilientOperation<I, O> =
      ResilientOperationBuilder<I, O>(observerRegistry, primaryOperationHook)
          .apply(configure)
          .build(name)
}
