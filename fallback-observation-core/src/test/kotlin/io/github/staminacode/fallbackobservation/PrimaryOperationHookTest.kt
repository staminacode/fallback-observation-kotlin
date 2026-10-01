package io.github.staminacode.fallbackobservation

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class PrimaryOperationHookTest {
  @Test
  fun `injected exception invokes fallback with primary exception origin`() {
    val failure = IOException("Injected failure")
    val events = mutableListOf<FallbackEvent>()
    val factory =
        FallbackFactory(
            FallbackObserverRegistry { events += it },
        ) {
          throw failure
        }
    val operation =
        factory.resilientOperation<Unit, String>("product.load") {
          handle<IOException>()
          operation { error("Primary must not execute") }
          fallback { _, exception ->
            assertSame(failure, exception)
            "cached"
          }
        }
    assertEquals("cached", operation())
    assertEquals(listOf(FallbackEvent("product.load", failure)), events)
  }

  @Test
  fun `pass through takes precedence for injected exceptions`() {
    val failure = IOException("Injected failure")
    val policy =
        FallbackFactory(
                FallbackObserverRegistry(emptyList()),
                { throw failure },
            )
            .fallbackCase("product.load") {
              handle<Exception>()
              passThrough<IOException>()
            }
    assertSame(
        failure,
        assertFailsWith<IOException> {
          policy.withFallback(
              { error("Primary must not execute") },
              { error("Fallback must not execute") },
          )
        },
    )
  }

  @Test
  fun `hook latency can exhaust a deadline checked by the primary operation`() {
    var elapsedMillis = 0L
    val deadlineMillis = 100L
    val failure = IOException("Deadline exceeded")
    val policy =
        FallbackFactory(
                FallbackObserverRegistry(emptyList()),
                {
                  elapsedMillis += 150L
                  PrimaryOperationDecision.PROCEED
                },
            )
            .fallbackCase("product.load") { handle<IOException>() }
    assertEquals(
        "cached",
        policy.withFallback(
            { if (elapsedMillis >= deadlineMillis) throw failure else "primary" },
            {
              assertSame(failure, it)
              "cached"
            },
        ),
    )
  }

  @Test
  fun `default factory executes primary without bypass`() {
    val fallbackCase =
        FallbackFactory(FallbackObserverRegistry(emptyList())).fallbackCase("product.load") {
          handle<IOException>()
        }

    assertEquals("primary", fallbackCase.withFallback({ "primary" }, { "fallback" }))
  }

  @Test
  fun `bypass selects cases by name and skips only the selected primary operation`() {
    val checkedCases = mutableListOf<String>()
    val factory =
        FallbackFactory(
            FallbackObserverRegistry(emptyList()),
            { caseName ->
              checkedCases += caseName
              if (caseName == "product.load") PrimaryOperationDecision.EXECUTE_FALLBACK
              else PrimaryOperationDecision.PROCEED
            },
        )
    val selected = factory.fallbackCase("product.load") { handle<IOException>() }
    val unselected = factory.fallbackCase("product.save") { handle<IOException>() }
    var primaryCalls = 0

    val selectedResult =
        selected.withFallback(
            {
              primaryCalls++
              "primary"
            },
            { exception ->
              assertEquals(
                  "product.load",
                  assertIs<PrimaryOperationBypassedException>(exception).caseName,
              )
              "fallback"
            },
        )
    val unselectedResult =
        unselected.withFallback(
            {
              primaryCalls++
              "primary"
            },
            { "fallback" },
        )

    assertEquals("fallback", selectedResult)
    assertEquals("primary", unselectedResult)
    assertEquals(1, primaryCalls)
    assertEquals(listOf("product.load", "product.save"), checkedCases)
  }

  @Test
  fun `resilient operation uses the configured bypass before primary`() {
    val factory =
        FallbackFactory(
            FallbackObserverRegistry(emptyList()),
            { PrimaryOperationDecision.EXECUTE_FALLBACK },
        )
    val operation =
        factory.resilientOperation<Int, String>("product.load") {
          operation { error("Primary must not be invoked") }
          fallback { input, bypass ->
            assertIs<PrimaryOperationBypassedException>(bypass)
            "cached-$input"
          }
          handle<IOException>()
        }

    assertEquals("cached-42", operation(42))
  }

  @Test
  fun `declined bypass still handles a primary exception`() {
    val primaryFailure = IOException("Unavailable")
    val fallbackCase =
        FallbackFactory(
                FallbackObserverRegistry(emptyList()),
                { PrimaryOperationDecision.PROCEED },
            )
            .fallbackCase("product.load") { handle<IOException>() }

    assertEquals(
        "cached",
        fallbackCase.withFallback(
            { throw primaryFailure },
            { exception ->
              assertSame(primaryFailure, exception)
              "cached"
            },
        ),
    )
  }

  @Test
  fun `bypass notifies observers before fallback without reporting primary success`() {
    val events = mutableListOf<Any>()
    val observer =
        object : OperationObserver {
          override fun onFallback(event: FallbackEvent) {
            events += event
          }

          override fun onSuccess(event: OperationSuccessEvent) {
            events += event
          }

          override fun onError(event: OperationErrorEvent) {
            events += event
          }
        }
    val fallbackCase =
        FallbackFactory(
                FallbackObserverRegistry(observer),
                { PrimaryOperationDecision.EXECUTE_FALLBACK },
            )
            .fallbackCase("product.load") { handle<IOException>() }

    fallbackCase.withFallback(
        { error("Primary must not be invoked") },
        { exception ->
          val event = assertIs<FallbackEvent>(events.single())
          assertSame(exception, event.exception)
          assertEquals(FallbackOrigin.PRIMARY_BYPASS, event.origin)
          "cached"
        },
    )
    assertEquals(1, events.size)
  }

  @Test
  fun `failed bypassed fallback reports and throws its own exception`() {
    val errors = mutableListOf<OperationErrorEvent>()
    val observer =
        object : OperationObserver {
          override fun onFallback(event: FallbackEvent) = Unit

          override fun onSuccess(event: OperationSuccessEvent) = Unit

          override fun onError(event: OperationErrorEvent) {
            errors += event
          }
        }
    val fallbackCase =
        FallbackFactory(
                FallbackObserverRegistry(observer),
                { PrimaryOperationDecision.EXECUTE_FALLBACK },
            )
            .fallbackCase("product.load") { handle<IOException>() }
    val failure = IllegalStateException("Cache unavailable")

    assertSame(
        failure,
        assertFailsWith<IllegalStateException> {
          fallbackCase.withFallback({ error("Primary must not be invoked") }, { throw failure })
        },
    )
    assertEquals(
        listOf(OperationErrorEvent("product.load", failure, fallbackFailed = true)),
        errors,
    )
  }

  @Test
  fun `unhandled hook failure is reported and rethrown`() {
    val failure = IllegalArgumentException("Injected failure")
    val errors = mutableListOf<OperationErrorEvent>()
    val observer =
        object : OperationObserver {
          override fun onFallback(event: FallbackEvent) = Unit

          override fun onSuccess(event: OperationSuccessEvent) = Unit

          override fun onError(event: OperationErrorEvent) {
            errors += event
          }
        }
    val fallbackCase =
        FallbackFactory(
                FallbackObserverRegistry(observer),
                { throw failure },
            )
            .fallbackCase("product.load") { handle<IOException>() }

    assertSame(
        failure,
        assertFailsWith<IllegalArgumentException> {
          fallbackCase.withFallback(
              { error("Primary must not be invoked") },
              { error("Fallback must not be invoked") },
          )
        },
    )
    assertEquals(listOf(OperationErrorEvent("product.load", failure)), errors)
  }
}
