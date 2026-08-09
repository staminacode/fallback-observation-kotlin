package io.github.staminacode.fallbackobservation.junit

import io.github.staminacode.fallbackobservation.FallbackFactory
import io.github.staminacode.fallbackobservation.FallbackObserver
import io.github.staminacode.fallbackobservation.FallbackObserverRegistry
import io.github.staminacode.fallbackobservation.invoke
import java.io.IOException
import kotlin.test.assertEquals
import org.junit.jupiter.api.Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.testkit.engine.EngineTestKit

class NoUnexpectedFallbacksExtensionTest {

  @Test
  fun `passes a test that does not execute a fallback`() {
    execute(PassingFixture::class.java).testEvents().assertStatistics { statistics ->
      statistics.started(1).succeeded(1)
    }
  }

  @Test
  fun `fails a test that executes an unexpected fallback`() {
    execute(UnexpectedFallbackFixture::class.java).testEvents().assertStatistics { statistics ->
      statistics.started(1).failed(1)
    }
  }

  @Test
  fun `rejects parallel execution by default`() {
    execute(ParallelExecutionFixture::class.java, parallelExecutionEnabled = true)
        .testEvents()
        .assertStatistics { statistics -> statistics.started(1).failed(1) }
  }

  @Test
  fun `allows parallel execution when explicitly configured`() {
    execute(ParallelExecutionAllowedFixture::class.java, parallelExecutionEnabled = true)
        .testEvents()
        .assertStatistics { statistics -> statistics.started(1).succeeded(1) }
  }

  private fun execute(testClass: Class<*>, parallelExecutionEnabled: Boolean = false) =
      EngineTestKit.engine("junit-jupiter")
          .selectors(selectClass(testClass))
          .configurationParameter(
              PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME,
              parallelExecutionEnabled.toString(),
          )
          .execute()

  @Tag("engine-testkit-fixture")
  class PassingFixture {
    companion object {
      private val registry = FallbackObserverRegistry(FallbackObserver.NO_OP)

      @JvmField
      @RegisterExtension
      val noUnexpectedFallbacks = NoUnexpectedFallbacksExtension(registry)
    }

    @Test
    fun succeeds() {
      assertEquals("primary", "primary")
    }
  }

  @Tag("engine-testkit-fixture")
  class UnexpectedFallbackFixture {
    companion object {
      private val registry = FallbackObserverRegistry(FallbackObserver.NO_OP)

      @JvmField
      @RegisterExtension
      val noUnexpectedFallbacks = NoUnexpectedFallbacksExtension(registry)

      val operation =
          FallbackFactory(registry).resilientOperation<Unit, String>("product.load") {
            operation { throw IOException("Unavailable") }
            fallback { _, _ -> "cached" }
            handle<IOException>()
          }
    }

    @Test
    fun executesFallback() {
      operation()
    }
  }

  @Tag("engine-testkit-fixture")
  class ParallelExecutionFixture {
    companion object {
      @JvmField
      @RegisterExtension
      val noUnexpectedFallbacks =
          NoUnexpectedFallbacksExtension(FallbackObserverRegistry(FallbackObserver.NO_OP))
    }

    @Test fun succeeds() = Unit
  }

  @Tag("engine-testkit-fixture")
  class ParallelExecutionAllowedFixture {
    companion object {
      @JvmField
      @RegisterExtension
      val noUnexpectedFallbacks =
          NoUnexpectedFallbacksExtension(
              observerRegistry = FallbackObserverRegistry(FallbackObserver.NO_OP),
              allowParallelExecution = true,
          )
    }

    @Test fun succeeds() = Unit
  }
}
