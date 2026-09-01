# Demo data for screenshots

`OrderService.java` — `placeOrderUnsafe` flagged (the `thenCompose`
lambda constructs a new `CompletableFuture` via `supplyAsync` that
never receives the later `chain.cancel(true)`); `placeOrderSafe` not
flagged (the inner future is captured and forwarded via
`.whenComplete(`).

## How to get the screenshot

1. `./gradlew runIde` from `completablefuture-cancellation-companion`,
   open this `demo/` folder as the project.
2. Full Screen, open `OrderService.java` — a warning should appear on
   `placeOrderUnsafe`'s `thenCompose(` call but not on
   `placeOrderSafe`'s.
3. Screenshot with both methods visible, save into
   `completablefuture-cancellation-companion/docs/screenshots/`. Close
   the sandbox.
