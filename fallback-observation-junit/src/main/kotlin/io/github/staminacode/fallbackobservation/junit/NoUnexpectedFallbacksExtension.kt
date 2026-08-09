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
) : BeforeEachCallback, AfterEachCallback {

  override fun beforeEach(context: ExtensionContext) {
    requireSequentialExecution(context)

    val observer = CollectingFallbackObserver()
    val registration = observerRegistry.register(observer)
    context.getStore(NAMESPACE).put(REGISTRATION_KEY, TestObservation(observer, registration))
  }

  override fun afterEach(context: ExtensionContext) {
    val observation =
        requireNotNull(
            context.getStore(NAMESPACE).remove(REGISTRATION_KEY, TestObservation::class.java)
        ) {
          "No fallback observation was registered for this test"
        }
    observation.registration.close()

    if (observation.observer.events.isNotEmpty()) {
      throw UnexpectedFallbacksError(observation.observer.events)
    }
  }

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

  private class CollectingFallbackObserver : FallbackObserver {
    val events = mutableListOf<FallbackEvent>()

    override fun onFallback(event: FallbackEvent) {
      events += event
    }
  }

  private data class TestObservation(
      val observer: CollectingFallbackObserver,
      val registration: FallbackObserverRegistration,
  )

  private companion object {
    const val REGISTRATION_KEY = "fallback-observation-registration"
    val NAMESPACE = ExtensionContext.Namespace.create(NoUnexpectedFallbacksExtension::class.java)
  }
}

/** Indicates that one or more unexpected fallbacks were executed during a test. */
class UnexpectedFallbacksError(events: List<FallbackEvent>) :
    AssertionError(unexpectedFallbacksMessage(events))

private fun unexpectedFallbacksMessage(events: List<FallbackEvent>): String = buildString {
  append("Unexpected fallbacks were executed:")
  events.forEach { event ->
    append("\n- case=").append(event.caseName)
    append(", exception=").append(event.exception::class.qualifiedName)
    event.exception.message?.let { append(", message=").append(it) }
  }
}
