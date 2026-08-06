package io.github.staminacode.fallbackobservation

import kotlin.reflect.KClass

abstract class AbstractFallbackBuilder(private val observerRegistry: FallbackObserverRegistry) {
  private val handledExceptions = linkedSetOf<KClass<out Exception>>()
  private val passThroughExceptions = linkedSetOf<KClass<out Exception>>()

  inline fun <reified E : Exception> handle() = handle(E::class)

  fun handle(exceptionType: KClass<out Exception>) {
    handledExceptions += exceptionType
  }

  inline fun <reified E : Exception> passThrough() = passThrough(E::class)

  fun passThrough(exceptionType: KClass<out Exception>) {
    passThroughExceptions += exceptionType
  }

  internal fun buildRules(caseName: String): FallbackRules {
    require(caseName.isNotBlank()) { "Fallback case name cannot be blank" }
    require(handledExceptions.isNotEmpty()) {
      "At least one handled exception must be configured for fallback case '$caseName'"
    }
    val overlaps = handledExceptions intersect passThroughExceptions
    require(overlaps.isEmpty()) {
      "The same exception type cannot be both handled and passed through in fallback case '$caseName'"
    }
    return FallbackRules(
        caseName,
        handledExceptions.toSet(),
        passThroughExceptions.toSet(),
        observerRegistry,
    )
  }
}
