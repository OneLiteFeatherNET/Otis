# Changelog

## [1.14.1](https://github.com/OneLiteFeatherNET/Otis/compare/v1.14.0...v1.14.1) (2026-06-20)


### Bug Fixes

* **logging:** improve JSON log structure for better field mapping ([2ef4586](https://github.com/OneLiteFeatherNET/Otis/commit/2ef4586acc792907b97920ba2bed5c7f4e256cbb))

## [1.14.0](https://github.com/OneLiteFeatherNET/Otis/compare/v1.13.5...v1.14.0) (2026-06-20)


### Features

* OpenTelemetry tracing + JSON logging for Grafana Loki ([#127](https://github.com/OneLiteFeatherNET/Otis/issues/127)) ([78b6b35](https://github.com/OneLiteFeatherNET/Otis/commit/78b6b35ea2680e8d1ca5533012c678f2959ef009))

## [1.13.5](https://github.com/OneLiteFeatherNET/Otis/compare/v1.13.4...v1.13.5) (2026-06-19)


### Bug Fixes

* **entity:** remove redundant `name` attribute from `@Table` annotation in `OtisPlayer` ([2267e0a](https://github.com/OneLiteFeatherNET/Otis/commit/2267e0aa4d9d12ba03cd5681ea339913edd4e3a3))

## [1.13.4](https://github.com/OneLiteFeatherNET/Otis/compare/v1.13.3...v1.13.4) (2026-06-19)


### Bug Fixes

* **build:** trim project version metadata from build script ([038296d](https://github.com/OneLiteFeatherNET/Otis/commit/038296d535ad152cf20c990714a671109d576207))

## [1.13.3](https://github.com/OneLiteFeatherNET/Otis/compare/v1.13.2...v1.13.3) (2026-06-18)


### Bug Fixes

* **ci:** use regctl login --skip-check and add manual docker dispatch ([df67534](https://github.com/OneLiteFeatherNET/Otis/commit/df67534de04c9ccf6830f6148941710b7fc7284b))


### Performance Improvements

* **ci:** larger docker blob chunks (90MB) and concurrent layer uploads ([b656392](https://github.com/OneLiteFeatherNET/Otis/commit/b65639286046d7f41c3f010d2e933d932a6c2d42))

## [1.13.2](https://github.com/OneLiteFeatherNET/Otis/compare/v1.13.1...v1.13.2) (2026-06-18)


### Bug Fixes

* **ci:** authenticate docker push via docker login ([f022e9a](https://github.com/OneLiteFeatherNET/Otis/commit/f022e9a9486ca4347c230f80e114ad83338f87ec))

## [1.13.1](https://github.com/OneLiteFeatherNET/Otis/compare/v1.13.0...v1.13.1) (2026-06-18)


### Bug Fixes

* **ci:** publish docker image via chunked blob upload ([8788784](https://github.com/OneLiteFeatherNET/Otis/commit/8788784ae9a2d6f220cc45e24125faf0d96b1c8e))

## [1.13.0](https://github.com/OneLiteFeatherNET/Otis/compare/v1.12.2...v1.13.0) (2026-06-18)


### Features

* enable release-please pipeline ([006a7cb](https://github.com/OneLiteFeatherNET/Otis/commit/006a7cb2dc4eea0868d39440e554000bf25b5250))
