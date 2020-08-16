# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]
### Changed
- ([#1](https://github.com/athos/pogonos/pull/1)) Fix `VerifyError` on some environments
  - This change removes the `java.io.Closeable` implementation from Pogonos readers. Users need to use `pogonos.protocols/IReader#close()` instead.

## [0.1.0] - 2020-06-18
- First release

[Unreleased]: https://github.com/athos/pogonos/compare/0.1.0...HEAD
[0.1.0]: https://github.com/athos/pogonos/releases/0.1.0
