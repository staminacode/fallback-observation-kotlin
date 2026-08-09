package io.github.staminacode.fallbackobservation.junit

/**
 * Declares the exact number of times a fallback case must be executed during a test.
 *
 * The annotation can be repeated to declare expectations for multiple fallback cases.
 */
@MustBeDocumented
@Repeatable
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class ExpectedFallback(
    val value: String,
    val times: Int = 1,
)
