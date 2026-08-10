package io.github.staminacode.fallbackobservation

import kotlin.reflect.KClass

internal class FallbackRules(
    private val caseName: String,
    private val handledExceptions: Set<KClass<out Exception>>,
    private val passThroughExceptions: Set<KClass<out Exception>>,
    private val observerRegistry: FallbackObserverRegistry,
) {
  fun <O> execute(operation: () -> O, fallback: (Exception) -> O): O {
    val result =
        try {
          operation()
        } catch (exception: Exception) {
          return recover(exception, fallback)
        }

    observerRegistry.recordSuccess(caseName)
    return result
  }

  private fun <O> recover(exception: Exception, fallback: (Exception) -> O): O =
      when {
        exception.matches(passThroughExceptions) -> rethrow(exception)
        exception.matches(handledExceptions) -> executeFallback(exception, fallback)
        else -> rethrow(exception)
      }

  private fun <O> executeFallback(exception: Exception, fallback: (Exception) -> O): O {
    observerRegistry.recordFallback(caseName, exception)
    return try {
      fallback(exception)
    } catch (fallbackException: Exception) {
      observerRegistry.recordFallbackFailure(caseName, exception, fallbackException)
      throw exception
    }
  }

  private fun rethrow(exception: Exception): Nothing {
    observerRegistry.recordError(caseName, exception)
    throw exception
  }

  private fun Exception.matches(types: Set<KClass<out Exception>>) = types.any {
    it.isInstance(this)
  }
}
