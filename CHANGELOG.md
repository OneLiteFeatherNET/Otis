# Changelog

## [1.16.0](https://github.com/OneLiteFeatherNET/Otis/compare/v1.15.0...v1.16.0) (2026-06-22)


### Features

* **discovery:** add Micronaut Kubernetes service discovery client ([569dacc](https://github.com/OneLiteFeatherNET/Otis/commit/569dacc6f028ab0f5d5d9e339eac3ca607d5c9c9))
* enable Kubernetes service discovery client ([#142](https://github.com/OneLiteFeatherNET/Otis/issues/142)) ([569dacc](https://github.com/OneLiteFeatherNET/Otis/commit/569dacc6f028ab0f5d5d9e339eac3ca607d5c9c9))

## [1.15.0](https://github.com/OneLiteFeatherNET/Otis/compare/v1.14.1...v1.15.0) (2026-06-22)


### Features

* **ci:** switch release-please to Maven (java) flow with snapshots ([#132](https://github.com/OneLiteFeatherNET/Otis/issues/132)) ([cb4fb45](https://github.com/OneLiteFeatherNET/Otis/commit/cb4fb452b2c2ba1e024dbce078d4dba49f904c41))
* enable /health and /prometheus management endpoints ([#139](https://github.com/OneLiteFeatherNET/Otis/issues/139)) ([cccddf5](https://github.com/OneLiteFeatherNET/Otis/commit/cccddf5343f21d989b8b02128a3e61c934a29442))


### Bug Fixes

* **deps:** update logstash-logback-encoder to v9 ([#140](https://github.com/OneLiteFeatherNET/Otis/issues/140)) ([7eba836](https://github.com/OneLiteFeatherNET/Otis/commit/7eba8365afccc9286de61626c57b7b7377c9ec36))

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
