package io.github.staminacode.fallbackobservation

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class FallbackCaseTest {

  private val fallbackRegistry = FallbackObserverRegistry()
  private val fallbackFactory = FallbackFactory(fallbackRegistry)

  @Test
  fun `executes operation when it succeeds`() {
    val fallbackCase = fallbackFactory.fallbackCase("product.load") { handle<IOException>() }
    assertEquals("primary", fallbackCase.withFallback({ "primary" }, { "fallback" }))
  }

  @Test
  fun `executes fallback for handled exception`() {
    val fallbackCase = fallbackFactory.fallbackCase("product.load") { handle<IOException>() }
    assertEquals(
        "cached",
        fallbackCase.withFallback({ throw IOException("Unavailable") }, { "cached" }),
    )
  }

  @Test
  fun `rethrows unhandled exception`() {
    val exception = IllegalArgumentException("Invalid product")
    val fallbackCase = fallbackFactory.fallbackCase("product.load") { handle<IOException>() }
    assertSame(
        exception,
        assertFailsWith {
          fallbackCase.withFallback(
              { throw exception },
              { error("Fallback must not be invoked") },
          )
        },
    )
  }

  @Test
  fun `pass through takes precedence over handled parent`() {
    val exception = AuthenticationException()
    val fallbackCase =
        fallbackFactory.fallbackCase("product.load") {
          handle<ProcessingException>()
          passThrough<AuthenticationException>()
        }
    assertSame(
        exception,
        assertFailsWith {
          fallbackCase.withFallback(
              { throw exception },
              { error("Fallback must not be invoked") },
          )
        },
    )
  }

  @Test
  fun `notifies observer before executing fallback`() {
    val events = mutableListOf<FallbackEvent>()
    val customFallbackRegistry = FallbackObserverRegistry({ events += it })
    val customFallbackFactory = FallbackFactory(customFallbackRegistry)
    val fallbackCase =
        customFallbackFactory.fallbackCase("product.load") {
          handle<IOException>()
        }
    val exception = IOException("Unavailable")
    fallbackCase.withFallback({ throw exception }, { "cached" })
    assertEquals("product.load", events.single().caseName)
    assertSame(exception, events.single().exception)
  }

  @Test
  fun `notifies fallback aware observers when the primary operation succeeds`() {
    val successes = mutableListOf<OperationSuccessEvent>()
    val registry =
        FallbackObserverRegistry(
            object : FallbackAwareOperationObserver {
              override fun onFallback(event: FallbackEvent) = Unit

              override fun onSuccess(event: OperationSuccessEvent) {
                successes += event
              }
            }
        )
    val fallbackCase =
        FallbackFactory(registry).fallbackCase("product.load") { handle<IOException>() }

    fallbackCase.withFallback({ "primary" }, { "fallback" })

    assertEquals(listOf(OperationSuccessEvent("product.load")), successes)
  }

  @Test
  fun `notifies operation observers when an error escapes without fallback`() {
    val errors = mutableListOf<OperationErrorEvent>()
    val registry = FallbackObserverRegistry(operationObserver(errors))
    val fallbackCase =
        FallbackFactory(registry).fallbackCase("product.load") { handle<IOException>() }
    val exception = IllegalArgumentException("Invalid product")

    assertSame(
        exception,
        assertFailsWith {
          fallbackCase.withFallback(
              { throw exception },
              { error("Fallback must not be invoked") },
          )
        },
    )

    assertEquals(listOf(OperationErrorEvent("product.load", exception)), errors)
  }

  @Test
  fun `logs a fallback error and reports the original error when the fallback function fails`() {
    val fallbacks = mutableListOf<FallbackEvent>()
    val errors = mutableListOf<OperationErrorEvent>()
    val registry =
        FallbackObserverRegistry(
            object : OperationObserver {
              override fun onFallback(event: FallbackEvent) {
                fallbacks += event
              }

              override fun onSuccess(event: OperationSuccessEvent) = Unit

              override fun onError(event: OperationErrorEvent) {
                errors += event
              }
            }
        )
    val fallbackCase =
        FallbackFactory(registry).fallbackCase("product.load") { handle<IOException>() }
    val primaryException = IOException("Primary unavailable")
    val fallbackException = IllegalStateException("Cache unavailable")

    assertSame(
        primaryException,
        assertFailsWith {
          fallbackCase.withFallback(
              { throw primaryException },
              { throw fallbackException },
          )
        },
    )

    assertEquals(1, fallbacks.size)
    assertEquals(
        listOf(OperationErrorEvent("product.load", primaryException, fallbackFailed = true)),
        errors,
    )
  }

  private fun operationObserver(errors: MutableList<OperationErrorEvent>) =
      object : OperationObserver {
        override fun onFallback(event: FallbackEvent) = Unit

        override fun onSuccess(event: OperationSuccessEvent) = Unit

        override fun onError(event: OperationErrorEvent) {
          errors += event
        }
      }

  private open class ProcessingException : RuntimeException()

  private class AuthenticationException : ProcessingException()
}
