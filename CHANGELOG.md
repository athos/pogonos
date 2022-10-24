# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

### Added
- ([#20](https://github.com/athos/pogonos/pull/20)) Babashka support

### Fixed
- ([#17](https://github.com/athos/pogonos/pull/17)) Allow non-map values as top-level data for spec testing

## [0.2.0] - 2022-04-18
### Added
- ([#6](https://github.com/athos/pogonos/pull/6)) New API to check template syntax
- ([#10](https://github.com/athos/pogonos/pull/10)) API for use as `-X` program or `-T` CLI tool

### Changed
- ([#14](https://github.com/athos/pogonos/pull/14)) `pogonos.reader/blank-trailing?` now returns a falsy value when the reader reaches the end of the input source

### Fixed
- ([#9](https://github.com/athos/pogonos/issues/9)) Detailed error messages won't be shown if line has no trailing line break
- ([#15](https://github.com/athos/pogonos/pull/15)) Fix incomplete error messages for comment tags

## [0.1.1] - 2020-08-20
### Changed
- ([#1](https://github.com/athos/pogonos/pull/1)) Fix `VerifyError` on some environments
  - This change removed the `java.io.Closeable` implementation from Pogonos readers. Users need to use `pogonos.protocols/IReader#close()` instead.
- ([#2](https://github.com/athos/pogonos/pull/2)) Fix shadow-cljs warnings that complain about marker protocol
  - This change replaced the `Invisible` protocol with a new one: `IVisibility`

## [0.1.0] - 2020-06-18
- First release

[Unreleased]: https://github.com/athos/pogonos/compare/0.2.0...HEAD
[0.2.0]: https://github.com/athos/pogonos/compare/0.1.1...0.2.0
[0.1.1]: https://github.com/athos/pogonos/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/athos/pogonos/releases/0.1.0
