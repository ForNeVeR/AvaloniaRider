# AvaloniaRider [![JetBrains Plugins Repository](https://img.shields.io/jetbrains/plugin/v/14839.svg?label=rider%20&colorB=0A7BBB&style=flat-square)][marketplace]

Install
-------

### Stable Release

Either search for Avalonia on the **Plugins** settings page or [visit the Plugin Marketplace][marketplace].

### Development Release

To install a development release (automatically published for every push into a development branch), first add the custom plugin repository into your IDE (see [the documentation][ide.channels] on how to do that).

**Custom plugin repository address:**
`https://plugins.jetbrains.com/plugins/dev/14839`

Then, install the plugin as usual via the IDE plugin settings screen.

How to Use
----------

### Previewer

Make sure your Avalonia project is built, open an XAML file, and you'll see an XAML preview in Rider.

![Preview Screenshot][preview-screenshot]

It's possible to interact with the previewer using the mouse, and zoom with `Ctrl+Scroll Wheel`.

There's a separate button to **debug** the previewer process for the current file, but it's hidden by default. To enable that feature, go to the **Avalonia** settings page in Rider and enable the **Developer mode** checkbox.

### File templates

Right-click a directory or an Avalonia-enabled project and choose among the available file templates:
- Avalonia User Control
- Avalonia Templated control
- Avalonia Window
- Avalonia Resource Dictionary
- Avalonia Styles

### Live templates

Type in an editor to use [Live templates][live-templates]:
- `directProperty`,
- `styledProperty`,
- `attachedAvaloniaProperty`.

Documentation
-------------

- [Changelog][docs.changelog]
- [Contributor Guide][docs.contributing]
- [License (MIT)][docs.license]
- [Maintainership][docs.maintainership]

[docs.changelog]: CHANGELOG.md
[docs.contributing]: CONTRIBUTING.md
[docs.license]: LICENSE.md
[docs.maintainership]: MAINTAINERSHIP.md
[ide.channels]: https://www.jetbrains.com/help/idea/managing-plugins.html#repos
[live-templates]: https://www.jetbrains.com/help/rider/Using_Live_Templates.html
[preview-screenshot]: ./docs/preview-screenshot.png
[marketplace]: https://plugins.jetbrains.com/plugin/14839-avaloniarider/
