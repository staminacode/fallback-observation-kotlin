package io.github.staminacode.fallbackobservation

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ObserverFailureTest {
  private val events = mutableListOf<Any>()
  private val failingObserver =
      object : OperationObserver {
        override fun onSuccess(event: OperationSuccessEvent) = error("Observer unavailable")

        override fun onFallback(event: FallbackEvent) = error("Observer unavailable")

        override fun onError(event: OperationErrorEvent) = error("Observer unavailable")
      }
  private val recordingObserver =
      object : OperationObserver {
        override fun onSuccess(event: OperationSuccessEvent) {
          events += event
        }

        override fun onFallback(event: FallbackEvent) {
          events += event
        }

        override fun onError(event: OperationErrorEvent) {
          events += event
        }
      }
  private val registry = FallbackObserverRegistry(listOf(failingObserver, recordingObserver))
  private val fallbackCase =
      FallbackFactory(registry).fallbackCase("product.load") {
        handle<IOException>()
      }

  @Test
  fun `observer failure preserves primary success and notifies the next observer`() {
    assertEquals(
        "primary",
        fallbackCase.withFallback({ "primary" }, { error("Unexpected fallback") }),
    )
    assertEquals(listOf<Any>(OperationSuccessEvent("product.load")), events)
  }

  @Test
  fun `observer failure does not prevent fallback execution or further notifications`() {
    val original = IOException("Primary unavailable")
    val result = fallbackCase.withFallback<String>({ throw original }, { "cached" })
    assertEquals("cached", result)
    assertEquals(listOf<Any>(FallbackEvent("product.load", original)), events)
  }

  @Test
  fun `observer failure preserves the unhandled exception and notifies the next observer`() {
    val original = IllegalArgumentException("Invalid input")
    assertSame(
        original,
        assertFailsWith<IllegalArgumentException> {
          fallbackCase.withFallback({ throw original }, { error("Unexpected fallback") })
        },
    )
    assertEquals(listOf<Any>(OperationErrorEvent("product.load", original)), events)
  }

  @Test
  fun `observer failure preserves the original exception when fallback fails`() {
    val original = IOException("Primary unavailable")
    assertSame(
        original,
        assertFailsWith<IOException> {
          fallbackCase.withFallback({ throw original }, { error("Cache unavailable") })
        },
    )
    assertEquals(
        listOf(
            FallbackEvent("product.load", original),
            OperationErrorEvent("product.load", original, fallbackFailed = true),
        ),
        events,
    )
  }
}
