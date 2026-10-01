package io.github.staminacode.fallbackobservation.micrometer

import io.github.staminacode.fallbackobservation.FallbackEvent
import io.github.staminacode.fallbackobservation.FallbackObserver
import io.github.staminacode.fallbackobservation.FallbackOrigin
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.ConcurrentHashMap

class MicrometerFallbackObserver(
    private val meterRegistry: MeterRegistry,
    private val metricName: String = "fallback.executions",
) : FallbackObserver {
  private val counters = ConcurrentHashMap<CounterKey, Counter>()

  override fun onFallback(event: FallbackEvent) {
    val key =
        CounterKey(
            event.caseName,
            if (event.origin == FallbackOrigin.PRIMARY_BYPASS) {
              "none"
            } else {
              event.exception::class.qualifiedName ?: event.exception.javaClass.name
            },
            event.origin.name,
        )
    counters
        .computeIfAbsent(key) {
          Counter.builder(metricName)
              .description("Number of fallback executions")
              .tag("fallback.case", key.caseName)
              .tag("exception", key.exceptionName)
              .tag("origin", key.origin)
              .register(meterRegistry)
        }
        .increment()
  }

  private data class CounterKey(val caseName: String, val exceptionName: String, val origin: String)
}
