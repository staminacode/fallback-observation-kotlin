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
    val fallbackCase =
        fallbackFactory.fallbackCase("product.load") {
          handle<IOException>()
          // observer { events += it } TODO: fix this
        }
    val exception = IOException("Unavailable")
    fallbackCase.withFallback({ throw exception }, { "cached" })
    assertEquals("product.load", events.single().caseName)
    assertSame(exception, events.single().exception)
  }

  private open class ProcessingException : RuntimeException()

  private class AuthenticationException : ProcessingException()
}
