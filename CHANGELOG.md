Changelog
=========
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/) and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

When considering version compatibility (and thus incrementing the major version), we only consider the configuration file major user experience changes. The host IDE version is not considered a version compatibility factor (since otherwise we'd have to increase the major version pretty often and that would make no sense).

## [Unreleased]
### Changed
- **(Requirement update!)** Update the minimally supported Rider version to 2024.2 EAP 4.
- The plugin no longer requires JavaFX (and thus JavaFX plugin for Rider), since it now relies on JCEF.

## [1.3.2] - 2024-02-17
### Changed
- **(Requirement update!)** Update the minimally supported Rider version to 2024.1 EAP 5.

### Fixed
- [#326](https://github.com/ForNeVeR/AvaloniaRider/issues/326): Rider EAP 5 compatibility.

## [1.3.1] - 2024-01-26
### Changed
- **(Requirement update!)** Update the supported Rider version to 2024.1 EAP 2.

### Fixed
- Part of [#237](https://github.com/ForNeVeR/AvaloniaRider/issues/237): previewer now should correctly work for Mono if it's detected.

### Added
- Improve the diagnostics of cases when there are no locally runnable projects in the open solution.

## [1.3.0] - 2023-10-26
### Added
- [#263](https://github.com/ForNeVeR/AvaloniaRider/issues/263): better zoom level controls.
### Changed
- Upgrade the minimally supported version of Rider to 2023.3 EAP 4.
- Small compatibility changes for the new version of Rider.

## [1.2.0] - 2023-10-02
### Changed
- Upgrade the minimally supported version of Rider to 2023.3 EAP 1.
- [#270](https://github.com/ForNeVeR/AvaloniaRider/issues/270): do not print error messages during normal previewer termination.

### Added
- New setting to define previewer process working directory (see [#266](https://github.com/ForNeVeR/AvaloniaRider/pull/266) for details).

## [1.1.0] - 2023-07-15
### Changed
- Default templates are no longer generating the `InitializeComponent` method and `AttachDevTools` call. Those are handled by Avalonia source generators in the latest versions.

## [1.0.5] - 2023-06-30
### Fixed
- Correct DPI is now passed to the previewer process from the start.

### Changed
- The plugin is now only compatible with Rider 2023.2 EAP 6 and later.

## [1.0.4] - 2023-05-17
### Changed
- The plugin is now only compatible with Rider 2023.2 EAP 1 and later.

## [1.0.3] - 2023-01-11
### Changed
- Marked as compatible with Rider 2023.1 EAP builds.

## [1.0.2] - 2022-12-28
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
[1.0.2]: https://github.com/ForNeVeR/AvaloniaRider/compare/v1.0.1...v1.0.2
[1.0.3]: https://github.com/ForNeVeR/AvaloniaRider/compare/v1.0.2...v1.0.3
[1.0.4]: https://github.com/ForNeVeR/AvaloniaRider/compare/v1.0.3...v1.0.4
[1.0.5]: https://github.com/ForNeVeR/AvaloniaRider/compare/v1.0.4...v1.0.5
[1.1.0]: https://github.com/ForNeVeR/AvaloniaRider/compare/v1.0.5...v1.1.0
[1.2.0]: https://github.com/ForNeVeR/AvaloniaRider/compare/v1.1.0...v1.2.0
[1.3.0]: https://github.com/ForNeVeR/AvaloniaRider/compare/v1.2.0...v1.3.0
[1.3.1]: https://github.com/ForNeVeR/AvaloniaRider/compare/v1.3.0...v1.3.1
[1.3.2]: https://github.com/ForNeVeR/AvaloniaRider/compare/v1.3.1...v1.3.2
[Unreleased]: https://github.com/ForNeVeR/AvaloniaRider/compare/v1.3.2...HEAD
