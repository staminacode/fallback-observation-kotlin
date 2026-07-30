package io.github.staminacode.fallbackobservation

import kotlin.reflect.KClass

abstract class AbstractFallbackBuilder {
    private val handledExceptions = linkedSetOf<KClass<out Throwable>>()
    private val passThroughExceptions = linkedSetOf<KClass<out Throwable>>()
    private var fallbackObserver: FallbackObserver = FallbackObserver.DEFAULT

    inline fun <reified E : Throwable> handle() = handle(E::class)

    fun handle(exceptionType: KClass<out Throwable>) {
        handledExceptions += exceptionType
    }

    inline fun <reified E : Throwable> passThrough() = passThrough(E::class)

    fun passThrough(exceptionType: KClass<out Throwable>) {
        passThroughExceptions += exceptionType
    }

    fun observer(observer: FallbackObserver) {
        fallbackObserver = observer
    }

    internal fun buildRules(caseName: String): FallbackRules {
        require(caseName.isNotBlank()) { "Fallback case name cannot be blank" }
        require(handledExceptions.isNotEmpty()) {
            "At least one handled exception must be configured for fallback case '$caseName'"
        }
        val overlaps = handledExceptions intersect passThroughExceptions
        require(overlaps.isEmpty()) {
            "The same exception type cannot be both handled and passed through in fallback case '$caseName'"
        }
        return FallbackRules(caseName, handledExceptions.toSet(), passThroughExceptions.toSet(), fallbackObserver)
    }
}
