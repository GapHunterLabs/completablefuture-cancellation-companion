<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# CompletableFuture Cancellation Propagation Companion Changelog

## [Unreleased]

## [0.1.0]

### Added

- New inspection: flags a `.thenCompose(...)` chain whose result future
  can be cancelled elsewhere in the same method, where the lambda
  directly constructs another `CompletableFuture` (via
  `supplyAsync`/`runAsync`/`new CompletableFuture<>()`) that never
  receives that cancellation -- the orphaned inner work keeps running
  after the caller believes the whole operation stopped. Covers both
  expression-form and block-form lambdas; suppressed when a
  `.whenComplete(` call is present as evidence of manual forwarding.

[Unreleased]: https://github.com/GapHunterLabs/completablefuture-cancellation-companion/compare/0.1.0...HEAD
[0.1.0]: https://github.com/GapHunterLabs/completablefuture-cancellation-companion/commits/0.1.0
