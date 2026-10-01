package io.github.staminacode.fallbackobservation

/**
 * Runs before each primary operation created by a factory configured with this hook.
 *
 * Return [PrimaryOperationDecision.PROCEED] for normal execution or
 * [PrimaryOperationDecision.EXECUTE_FALLBACK] to skip the primary operation regardless of its
 * exception rules. The stable case name supports selection by feature flags or test scenarios.
 *
 * Exceptions thrown by this hook follow the same `handle` and `passThrough` rules as exceptions
 * from the primary operation, allowing controlled failure injection. The primary operation is not
 * called after the hook throws.
 *
 * The hook runs synchronously on the calling thread. An intentional delay adds invocation latency
 * and can exercise deadline checks that cover the invocation or run afterward in the primary
 * operation. It does not simulate time spent inside a client call that has not started yet.
 * Implementations should return promptly unless intentionally injecting latency.
 */
fun interface PrimaryOperationHook {
  /** Selects the execution path for [caseName], optionally injecting latency or an exception. */
  fun beforePrimary(caseName: String): PrimaryOperationDecision
}

/** Selects whether to execute the primary operation or go directly to its fallback. */
enum class PrimaryOperationDecision {
  PROCEED,
  EXECUTE_FALLBACK,
}

/**
 * Passed to the fallback when the hook selects direct fallback execution without a primary error.
 */
class PrimaryOperationBypassedException(val caseName: String) :
    Exception("Primary operation bypassed for fallback case '$caseName'")
