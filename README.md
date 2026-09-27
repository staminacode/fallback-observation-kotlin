# fallback-observation

`fallback-observation` provides small, framework-agnostic primitives for observing fallback execution in Kotlin applications. It does not implement retries, circuit breakers, or timeouts.

## Modules

- `fallback-observation-core` contains the fallback API and observer contracts.
- [`fallback-observation-junit`](fallback-observation-junit/README.md) verifies fallback execution in
  JUnit Jupiter tests.
- `fallback-observation-micrometer` provides a Micrometer-backed `FallbackObserver`.

## Create a factory

Create `FallbackCase` and `ResilientOperation` instances through a `FallbackFactory`. The factory
connects them to a shared `FallbackObserverRegistry`, where observers are configured once:

```kotlin
val observerRegistry = FallbackObserverRegistry(
    listOf(
        FallbackObserver.LOGGING,
        MicrometerFallbackObserver(meterRegistry),
    ),
)

val fallbackFactory = FallbackFactory(observerRegistry)
```

This configuration enables logging and Micrometer metrics for the examples below. Use
`FallbackObserverRegistry()` for logging only, or `FallbackObserverRegistry(emptyList())` for no
registered observers. Keep the registry and factory as long-lived application dependencies.

## `FallbackCase` vs. `ResilientOperation`

Both types apply the same exception rules and notify a `FallbackObserver` immediately before a fallback is executed. The difference is where the primary operation and fallback function are defined.

| Type | Define the operation and fallback | Best for |
| --- | --- | --- |
| `FallbackCase` | At the call site, through `withFallback` | A reusable exception-handling policy whose operations vary from call to call. It can wrap a call with any number of arguments. |
| `ResilientOperation` | Once, when the object is created | A stable, named operation that is invoked repeatedly with one input value of type `I`. |

`ResilientOperation<I, O>` deliberately models its primary operation and fallback as functions that receive a
single value of type `I`. This is a useful constraint: a focused input type keeps the operation's public API
small and discourages long parameter lists. If several values really belong together, model them as a dedicated
input type.

When wrapping an existing call that genuinely needs several independent parameters, use `FallbackCase` instead.
It acts as an observable replacement for a local `try`/`catch` and can wrap any expression. The trade-off is that
the primary operation and fallback remain at the call site, so the code is more verbose.

### Reuse configured instances

Create each `FallbackCase` or `ResilientOperation` once and reuse it for all executions of the same logical
operation. In an application, keep it in a long-lived `val`—for example, a singleton, service, or dependency
injection component—instead of rebuilding it at every call. Reuse keeps the configuration in one place and
avoids unnecessary allocation of builders and fallback rules.

### Same use case: one input

For a product lookup that needs only a `ProductId`, all three approaches are possible. A plain `try`/`catch`
keeps the control flow local, but it duplicates the logging and metric-recording concerns at every call site:

```kotlin
try {
    productClient.load(productId)
} catch (primaryException: IOException) {
    logger.warn("Executing fallback for case={}", "product.load", primaryException)

    Counter.builder("fallback.executions")
        .description("Number of fallback executions")
        .tag("fallback.case", "product.load")
        .tag("exception", primaryException.javaClass.name)
        .register(meterRegistry)
        .increment()

    productCache.load(productId)
}
```

#### Using `FallbackCase`

`FallbackCase` keeps the name and exception rules in a reusable policy connected to the factory's
registry. The primary and fallback functions are supplied at the call site, so the same policy can
be used with different implementations:

```kotlin
val productLoadPolicy = fallbackFactory.fallbackCase("product.load") {
    handle<IOException>()
}

val product = productLoadPolicy.withFallback(
    operation = { productClient.load(productId) },
    fallback = { productCache.load(productId) },
)
```

#### Using `ResilientOperation`

`ResilientOperation` captures both functions when it is created through the same factory. It behaves
like a function through Kotlin's `invoke` operator, making repeated calls with one input concise:

```kotlin
val loadProductWithFallback = fallbackFactory.resilientOperation<ProductId, Product>("product.load") {
    operation(productClient::load)
    fallback { productId, _ -> productCache.load(productId) }

    handle<IOException>()
}

val product = loadProductWithFallback(productId)
```

Use `ResilientOperation<Unit, Output>` for operations without input, then call it as `operation()`.

## Exception handling rules

- A matching `handle` rule executes the fallback.
- A matching `passThrough` rule rethrows the exception.
- `passThrough` takes precedence over `handle`, allowing a narrower exception to be excluded from a broader handled type.
- Exceptions that match neither rule are rethrown.

## Observability

`FallbackObserver` receives a `FallbackEvent` immediately before fallback execution. By default, the core module logs the fallback at warning level, including the triggering exception, through SLF4J. The library includes only `slf4j-api`; the consuming application chooses the logging provider (for example, Logback, Log4j2, or the JUL provider). To disable observation explicitly, create the registry with an empty observer collection.

Observers can opt into richer outcome information when they need it:

- `FallbackObserver` receives only executed fallbacks.
- `FallbackAwareOperationObserver` also receives successful primary operations.
- `OperationObserver` additionally receives errors that escape without a successful fallback result.

This keeps the default observation lightweight. For example, a fallback-rate observer can implement
`FallbackAwareOperationObserver`, while an observer that needs to calculate an overall failure rate
can implement `OperationObserver`. If a fallback function itself fails, the original primary
exception is reported with `fallbackFailed = true` and rethrown; the fallback exception is logged.

### Manual `try`/`catch`

Use the registry directly when a local `try`/`catch` is a better fit than a reusable policy. The
manual calls notify the same observers used by `FallbackCase` and `ResilientOperation`:

```kotlin
try {
    val product = productClient.load(productId)
    observerRegistry.recordSuccess("product.load")
    product
} catch (primaryException: IOException) {
    observerRegistry.recordFallback("product.load", primaryException)

    try {
        productCache.load(productId)
    } catch (fallbackException: Exception) {
        observerRegistry.recordFallbackFailure(
            caseName = "product.load",
            primaryException = primaryException,
            fallbackException = fallbackException,
        )
        throw primaryException
    }
}
```

If no fallback is applied, call `recordError(caseName, exception)` before rethrowing the exception.

### `FallbackObserverRegistry`

`FallbackObserverRegistry` owns the observers used by the `FallbackFactory` and all
`FallbackCase` and `ResilientOperation` instances created by that factory, as shown in
[Create a factory](#create-a-factory). Changes to the registry also apply to policies that have
already been created.

The Micrometer observer records a `fallback.executions` counter tagged with `fallback.case` and
`exception`.

Observers can also be registered temporarily. `register` returns an `AutoCloseable` handle that
removes only that observer when closed. This is useful for scoped integrations such as the JUnit
extension:

```kotlin
val auditRegistration = observerRegistry.register(auditObserver)
try {
    // Execute work observed by auditObserver.
} finally {
    auditRegistration.close()
}
```

### Metric cardinality

When a `FallbackObserver` exports `caseName` as a metric tag, it must come from a small, bounded set of stable
logical operation names, such as `product.load` or `cache.refresh`. Do not build a case name from request IDs,
user IDs, tenant IDs, product IDs, URLs, or exception messages. Those values create high-cardinality metrics,
which can increase monitoring cost and degrade query performance. Put that contextual information in logs or
traces instead.

## Testing

The [JUnit module](fallback-observation-junit/README.md) provides `NoUnexpectedFallbacksExtension` for
verifying the exact fallback cases and execution counts exercised by a test.

## License

Licensed under the [Apache License 2.0](LICENSE).
