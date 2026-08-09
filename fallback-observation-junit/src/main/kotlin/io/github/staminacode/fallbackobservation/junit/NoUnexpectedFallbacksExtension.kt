package io.github.staminacode.fallbackobservation.junit

import io.github.staminacode.fallbackobservation.FallbackEvent
import io.github.staminacode.fallbackobservation.FallbackObserver
import io.github.staminacode.fallbackobservation.FallbackObserverRegistration
import io.github.staminacode.fallbackobservation.FallbackObserverRegistry
import kotlin.jvm.optionals.getOrDefault
import org.junit.jupiter.api.Constants.PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionConfigurationException
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolutionException
import org.junit.jupiter.api.extension.ParameterResolver

/**
 * Fails a test when a fallback is executed through [observerRegistry].
 *
 * By default, this extension rejects a JUnit configuration that enables parallel execution. A
 * shared registry receives fallback events from every operation built with its associated factory,
 * and those events cannot be attributed reliably to individual concurrent tests. Set
 * [allowParallelExecution] to `true` only when the registry is isolated from other concurrently
 * executing tests.
 */
class NoUnexpectedFallbacksExtension(
    private val observerRegistry: FallbackObserverRegistry,
    private val allowParallelExecution: Boolean = false,
) : BeforeEachCallback, AfterEachCallback, ParameterResolver {

  override fun beforeEach(context: ExtensionContext) {
    requireSequentialExecution(context)

    val expectations = expectationsFor(context)
    val observer = CollectingFallbackObserver()
    val registration = observerRegistry.register(observer)
    context
        .getStore(NAMESPACE)
        .put(REGISTRATION_KEY, TestObservation(observer, registration, expectations))
  }

  override fun afterEach(context: ExtensionContext) {
    val observation =
        requireNotNull(
            context.getStore(NAMESPACE).remove(REGISTRATION_KEY, TestObservation::class.java)
        ) {
          "No fallback observation was registered for this test"
        }
    observation.registration.close()

    val expectedCounts = observation.expectations.counts()
    if (!matches(expectedCounts, observation.observer.events)) {
      throw FallbackVerificationError(expectedCounts, observation.observer.events)
    }
  }

  override fun supportsParameter(
      parameterContext: ParameterContext,
      extensionContext: ExtensionContext,
  ): Boolean = parameterContext.parameter.type == FallbackExpectations::class.java

  override fun resolveParameter(
      parameterContext: ParameterContext,
      extensionContext: ExtensionContext,
  ): Any =
      observationFor(extensionContext)?.expectations
          ?: throw ParameterResolutionException(
              "Fallback expectations are not available for this test"
          )

  private fun requireSequentialExecution(context: ExtensionContext) {
    val parallelExecutionEnabled =
        context
            .getConfigurationParameter(PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME)
            .map(String::toBoolean)
            .getOrDefault(false)

    if (parallelExecutionEnabled && !allowParallelExecution) {
      throw ExtensionConfigurationException(
          "NoUnexpectedFallbacksExtension cannot be used when JUnit parallel execution is enabled " +
              "because it observes a shared FallbackObserverRegistry. Set allowParallelExecution " +
              "to true only when this registry is isolated from all concurrently executing tests."
      )
    }
  }

  private fun expectationsFor(context: ExtensionContext): FallbackExpectations =
      FallbackExpectations().also { expectations ->
        context.requiredTestMethod.getAnnotationsByType(ExpectedFallback::class.java).forEach {
            expected ->
          try {
            expectations.expect(expected.value, expected.times)
          } catch (exception: IllegalArgumentException) {
            throw ExtensionConfigurationException(
                exception.message ?: "Invalid fallback expectation",
                exception,
            )
          } catch (exception: IllegalStateException) {
            throw ExtensionConfigurationException(
                exception.message ?: "Invalid fallback expectation",
                exception,
            )
          }
        }
      }

  private fun observationFor(context: ExtensionContext): TestObservation? =
      context.getStore(NAMESPACE).get(REGISTRATION_KEY, TestObservation::class.java)

  private class CollectingFallbackObserver : FallbackObserver {
    val events = mutableListOf<FallbackEvent>()

    override fun onFallback(event: FallbackEvent) {
      events += event
    }
  }

  private data class TestObservation(
      val observer: CollectingFallbackObserver,
      val registration: FallbackObserverRegistration,
      val expectations: FallbackExpectations,
  )

  private companion object {
    const val REGISTRATION_KEY = "fallback-observation-registration"
    val NAMESPACE = ExtensionContext.Namespace.create(NoUnexpectedFallbacksExtension::class.java)
  }
}

/** Indicates that the observed fallbacks did not match a test's expectations. */
class FallbackVerificationError(expectedCounts: Map<String, Int>, events: List<FallbackEvent>) :
    AssertionError(fallbackVerificationMessage(expectedCounts, events))

private fun matches(expectedCounts: Map<String, Int>, events: List<FallbackEvent>): Boolean =
    expectedCounts == events.groupingBy(FallbackEvent::caseName).eachCount()

private fun fallbackVerificationMessage(
    expectedCounts: Map<String, Int>,
    events: List<FallbackEvent>,
): String = buildString {
  append("Fallback verification failed:")
  val observedCounts = events.groupingBy(FallbackEvent::caseName).eachCount()

  (observedCounts.keys - expectedCounts.keys).forEach { caseName ->
    append("\n- Unexpected fallback '")
    append(caseName)
    append("' was executed ")
    append(observedCounts.getValue(caseName))
    append(" time(s).")
  }

  expectedCounts.forEach { (caseName, expectedCount) ->
    val observedCount = observedCounts[caseName] ?: 0
    if (expectedCount != observedCount) {
      append("\n- Expected fallback '")
      append(caseName)
      append("' to execute ")
      append(expectedCount)
      append(" time(s), but it executed ")
      append(observedCount)
      append(" time(s).")
    }
  }
}
