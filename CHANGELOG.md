Changelog
=========
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/) and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Fixed
- [#254](https://github.com/ForNeVeR/AvaloniaRider/issues/254): Error on startup involving `PreviewerUsageLogger`

## [1.0.1] - 2022-12-07
### Changed
- The plugin is now only compatible with Rider 2022.3.1.

### Fixed
- [#245: Restart previewer button causes the log to appear](https://github.com/ForNeVeR/AvaloniaRider/issues/245).
- [#247](https://github.com/ForNeVeR/AvaloniaRider/issues/247): remove a warning in the IDE logs about a previewer toolbar creation.
- All the previewer toolbar actions are now available during IntelliJ indexing.

## [1.0.0] - 2022-11-26
The initial plugin release. The plugin provides the following features for JetBrains Rider 2022.3 EAP7 and later:
- Avalonia Previewer embedded into the IDE editor,
- file templates for Avalonia `.xaml` files,
- live templates for Avalonia properties.

[1.0.0]: https://github.com/ForNeVeR/AvaloniaRider/releases/tag/v1.0.0
[1.0.1]: https://github.com/ForNeVeR/AvaloniaRider/compare/v1.0.0...v1.0.1
[Unreleased]: https://github.com/ForNeVeR/AvaloniaRider/compare/v1.0.1...HEAD
