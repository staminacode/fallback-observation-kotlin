package io.github.staminacode.fallbackobservation

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class ResilientOperationTest {
  private val fallbackRegistry = FallbackObserverRegistry()
  private val fallbackFactory = FallbackFactory(fallbackRegistry)

  @Test
  fun `can be invoked as a function`() {
    val operation =
        fallbackFactory.resilientOperation<Int, String>("number.load") {
          operation { throw IOException("Unavailable: $it") }
          fallback { number, _ -> "fallback-$number" }
          handle<IOException>()
        }
    assertEquals("fallback-42", operation(42))
  }

  @Test
  fun `supports Unit input`() {
    val operation =
        fallbackFactory.resilientOperation<Unit, String>("cache.refresh") {
          operation { throw IOException("Unavailable") }
          fallback { _, _ -> "current-cache" }
          handle<IOException>()
        }
    assertEquals("current-cache", operation())
  }
}
