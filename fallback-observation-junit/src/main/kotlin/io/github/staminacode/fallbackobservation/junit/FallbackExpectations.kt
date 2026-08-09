package io.github.staminacode.fallbackobservation.junit

/**
 * Declares fallback cases expected during the current test invocation.
 *
 * Use this parameter in parameterized tests when the expected fallback cases depend on the test
 * arguments. Each call to [expect] requires the case to execute exactly [times] times.
 */
class FallbackExpectations internal constructor() {
  private val expectedCounts = linkedMapOf<String, Int>()

  fun expect(caseName: String, times: Int = 1) {
    require(caseName.isNotBlank()) { "Expected fallback case name cannot be blank" }
    require(times > 0) { "Expected fallback count must be positive" }
    check(caseName !in expectedCounts) {
      "An expectation for fallback case '$caseName' has already been declared"
    }
    expectedCounts[caseName] = times
  }

  internal fun counts(): Map<String, Int> = expectedCounts.toMap()
}
