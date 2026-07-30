package io.github.staminacode.fallbackobservation.micrometer

import io.github.staminacode.fallbackobservation.FallbackEvent
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class MicrometerFallbackObserverTest {
    @Test
    fun `increments a counter tagged with the fallback case and exception`() {
        val registry = SimpleMeterRegistry()
        val observer = MicrometerFallbackObserver(registry)

        observer.onFallback(
            FallbackEvent(
                caseName = "product.load",
                exception = IOException("Unavailable"),
            ),
        )

        val counter = registry.find("fallback.executions")
            .tag("fallback.case", "product.load")
            .tag("exception", IOException::class.qualifiedName!!)
            .counter()

        assertEquals(1.0, counter?.count())
    }
}
