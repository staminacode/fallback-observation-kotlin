package io.github.staminacode.fallbackobservation

import kotlin.reflect.KClass
import org.slf4j.LoggerFactory

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

    observerRegistry.onSuccess(OperationSuccessEvent(caseName))
    return result
  }

  private fun <O> recover(exception: Exception, fallback: (Exception) -> O): O =
      when {
        exception.matches(passThroughExceptions) -> rethrow(exception)
        exception.matches(handledExceptions) -> executeFallback(exception, fallback)
        else -> rethrow(exception)
      }

  private fun <O> executeFallback(exception: Exception, fallback: (Exception) -> O): O {
    observerRegistry.onFallback(FallbackEvent(caseName, exception))
    return try {
      fallback(exception)
    } catch (fallbackException: Exception) {
      logger.error(
          "Fallback failed for case={}; rethrowing the original exception",
          caseName,
          fallbackException,
      )
      observerRegistry.onError(OperationErrorEvent(caseName, exception, fallbackFailed = true))
      throw exception
    }
  }

  private fun rethrow(exception: Exception): Nothing {
    observerRegistry.onError(OperationErrorEvent(caseName, exception))
    throw exception
  }

  private fun Exception.matches(types: Set<KClass<out Exception>>) = types.any {
    it.isInstance(this)
  }
}

private val logger = LoggerFactory.getLogger(FallbackRules::class.java)
