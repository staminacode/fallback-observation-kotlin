# fallback-observation-junit

`fallback-observation-junit` verifies that a JUnit Jupiter test executes exactly the fallback cases it
declares. It observes the same `FallbackObserverRegistry` used to create the application's
`FallbackCase` and `ResilientOperation` instances.

## Dependency

```kotlin
dependencies {
    testImplementation("io.github.staminacode:fallback-observation-junit:<version>")
}
```

The module brings the JUnit Jupiter API transitively. Configure a JUnit Jupiter engine in the test
runtime as usual.

## Registering the extension

The registry is an application dependency, so register a configured extension instance with
`@RegisterExtension`:

```kotlin
class ProductServiceTest {
    private val registry = FallbackObserverRegistry(FallbackObserver.NO_OP)
    private val fallbackFactory = FallbackFactory(registry)

    @JvmField
    @RegisterExtension
    val noUnexpectedFallbacks = NoUnexpectedFallbacksExtension(registry)

    private val service = ProductService(client, fallbackFactory)
}
```

Use the same registry that backs the `FallbackFactory` supplied to the object under test. The
extension registers a temporary observer before each test and removes it afterwards.

## Expected fallback counts

By default, any fallback makes the test fail. Declare every fallback that the test is expected to
exercise with `@ExpectedFallback`:

```kotlin
@Test
@ExpectedFallback("product.load")
fun `returns a cached product when loading fails`() {
    service.load(productId)
}
```

An expectation without `times` requires exactly one execution. The annotation is repeatable, and
`times` declares an exact count:

```kotlin
@Test
@ExpectedFallback("product.load")
@ExpectedFallback("cache.refresh", times = 2)
fun `refreshes the cache after loading a product`() {
    // ...
}
```

The extension fails the test when a fallback is unexpected, expected but not executed, or executed
more or fewer times than declared.

## Dynamic expectations

`FallbackExpectations` is injected into a test method. Use it when the expected cases depend on a
test invocation's arguments, such as in a parameterized test:

```kotlin
@ParameterizedTest
@MethodSource("scenarios")
fun `handles each scenario`(
    scenario: Scenario,
    expectations: FallbackExpectations,
) {
    if (scenario.clientUnavailable) {
        expectations.expect("product.load")
    }

    service.load(scenario.productId)
}
```

Programmatic expectations and `@ExpectedFallback` declarations are combined. Every expected case
must be declared only once, and its count must be positive.

## Parallel execution

`NoUnexpectedFallbacksExtension` rejects JUnit parallel execution by default. A shared registry
receives events from every fallback policy created with its factory, so a fallback cannot be
attributed reliably to one concurrent test.

Set `allowParallelExecution = true` only when your test infrastructure guarantees that the registry
is isolated from all other concurrently running tests.

```kotlin
@JvmField
@RegisterExtension
val noUnexpectedFallbacks =
    NoUnexpectedFallbacksExtension(
        observerRegistry = registry,
        allowParallelExecution = true,
    )
```
