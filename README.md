# CompletableFuture Cancellation Propagation Companion

IntelliJ-family plugin that flags a `.thenCompose(...)` chain whose
result future can be cancelled, but whose inner future -- constructed
directly inside the lambda -- never receives that cancellation, so the
orphaned work keeps running and consuming resources after the caller
believes the whole operation stopped.

## Why it exists

`CompletableFuture.thenCompose` never propagates cancellation into the
stage it returns -- calling `.cancel(true)` on the outer/chained future
only marks that future as cancelled; it does not touch the inner
`CompletableFuture` the lambda constructed and is still waiting on.
This is a real, repeatedly-reported gap: it shows up in GitHub issues
against Reactor Core, Failsafe, and OpenTelemetry's Java
instrumentation, each describing the same shape -- a caller cancels
what it thinks is the whole async operation, but a `supplyAsync`/
`runAsync` stage buried inside a `thenCompose` lambda keeps executing
to completion regardless. No dedicated IDE inspection for this pattern
was found on the Marketplace.

## Why built this way

- **Pure PSI, no dataflow engine.** The check looks for the exact
  shape that causes the leak (a `.thenCompose` lambda that directly
  returns a freshly-constructed `CompletableFuture`, plus a `.cancel(`
  call reachable on the variable the chain is assigned to) rather than
  attempting general reachability/dataflow analysis -- keeps false
  positives near zero at the cost of a narrower net.
- **Off-EDT safe, no network calls, no telemetry.** Runs entirely
  inside `LocalInspectionTool.checkFile`, same shape as every other
  inspection in this catalog.
- **Named forwarding pattern, not proven forwarding.** A `.whenComplete(`
  call found anywhere in the lambda's own text is treated as evidence
  that cancellation is already being forwarded by hand, and the
  warning is suppressed -- the same "match a known name, don't try to
  fully prove it" discipline used elsewhere in this catalog.

**v0.1 scope, stated honestly:** only the pattern where the lambda's
body directly returns another `CompletableFuture` constructed in the
*same method* (`CompletableFuture.supplyAsync(...)`, `.runAsync(...)`,
or `new CompletableFuture<>()`) is recognized -- both expression-form
and block-form (`x -> { ...; return ...; }`) lambdas are covered.
Cancellation forwarded through a call to an external helper method that
itself returns a `CompletableFuture` is not followed in this version.

## Usage

Install the plugin, open any Java file. The inspection runs
automatically and highlights the offending `.thenCompose(` call with a
warning explaining the gap and why it matters.

## Enterprise / Team Licensing

Need enterprise features, custom rules, or team licensing? Contact us
at **gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
