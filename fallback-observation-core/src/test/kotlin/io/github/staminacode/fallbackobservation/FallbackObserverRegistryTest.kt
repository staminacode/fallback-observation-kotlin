package io.github.staminacode.fallbackobservation

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class FallbackObserverRegistryTest {

  @Test
  fun `records manual operation outcomes for the observers that support them`() {
    val fallbacks = mutableListOf<FallbackEvent>()
    val successes = mutableListOf<OperationSuccessEvent>()
    val errors = mutableListOf<OperationErrorEvent>()
    val registry =
        FallbackObserverRegistry(
            object : OperationObserver {
              override fun onFallback(event: FallbackEvent) {
                fallbacks += event
              }

              override fun onSuccess(event: OperationSuccessEvent) {
                successes += event
              }

              override fun onError(event: OperationErrorEvent) {
                errors += event
              }
            }
        )
    val fallbackException = IOException("Primary unavailable")
    val errorException = IllegalStateException("Unavailable")

    registry.recordSuccess("product.load")
    registry.recordFallback("product.load", fallbackException)
    registry.recordError("product.load", errorException)

    assertEquals(listOf(OperationSuccessEvent("product.load")), successes)
    assertEquals(listOf(FallbackEvent("product.load", fallbackException)), fallbacks)
    assertEquals(listOf(OperationErrorEvent("product.load", errorException)), errors)
  }

  @Test
  fun `records the original error when a manual fallback fails`() {
    val errors = mutableListOf<OperationErrorEvent>()
    val registry =
        FallbackObserverRegistry(
            object : OperationObserver {
              override fun onFallback(event: FallbackEvent) = Unit

              override fun onSuccess(event: OperationSuccessEvent) = Unit

              override fun onError(event: OperationErrorEvent) {
                errors += event
              }
            }
        )
    val primaryException = IOException("Primary unavailable")

    registry.recordFallbackFailure(
        caseName = "product.load",
        primaryException = primaryException,
        fallbackException = IllegalStateException("Cache unavailable"),
    )

    assertEquals(
        listOf(OperationErrorEvent("product.load", primaryException, fallbackFailed = true)),
        errors,
    )
  }
}
