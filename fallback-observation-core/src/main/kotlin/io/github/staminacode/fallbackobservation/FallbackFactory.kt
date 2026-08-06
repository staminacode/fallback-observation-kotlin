package io.github.staminacode.fallbackobservation

class FallbackFactory(private val observerRegistry: FallbackObserverRegistry) {
  /**
   * Creates a [FallbackCase] with the supplied [name] and exception-handling configuration.
   *
   * Keep [name] stable and low-cardinality when a metric observer is used. It identifies a logical
   * operation, not an individual request, user, tenant, or resource.
   */
  fun fallbackCase(name: String, configure: FallbackCaseBuilder.() -> Unit): FallbackCase =
      FallbackCase(FallbackCaseBuilder(observerRegistry).apply(configure).buildRules(name))

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
      ResilientOperationBuilder<I, O>(observerRegistry).apply(configure).build(name)
}
