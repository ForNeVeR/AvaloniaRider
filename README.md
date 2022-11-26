# AvaloniaRider [![JetBrains Plugins Repository](https://img.shields.io/jetbrains/plugin/v/14839.svg?label=rider%20&colorB=0A7BBB&style=flat-square)](https://plugins.jetbrains.com/plugin/14839)

Install
-------

### Development releases (may be unstable)

To install a development release (automatically published for every push into a development branch), first add the custom plugin repository into your IDE (see [the documentation][ide.channels] on how to do that).

**Custom plugin repository address:**
`https://plugins.jetbrains.com/plugins/dev/14839`

Then, install the plugin as usual via the IDE plugin settings screen.

How to Use
----------

### Previewer

Make sure your Avalonia project is built, open a XAML file, and you'll see a XAML preview in Rider.

![Preview Screenshot][preview-screenshot]

It's possible to interact with the previewer using the mouse, and zoom with `Ctrl+Scroll Wheel`.

### File templates

Right-click a directory or an Avalonia-enabled project, and choose among the available file templates:
- Avalonia User Control
- Avalonia Templated control
- Avalonia Window
- Avalonia Resource Dictionary
- Avalonia Styles

### Live templates

Type in editor to use [Live templates][live-templates]:
- `directProperty`
- `styledProperty`
- `attachedAvaloniaProperty`

Documentation
-------------

- [Changelog][docs.changelog]
- [Contributor Guide][docs.contributing]
- [License (MIT)][docs.license]

[docs.changelog]: CHANGELOG.md
[docs.contributing]: CONTRIBUTING.md
[docs.license]: LICENSE.md
[ide.channels]: https://www.jetbrains.com/help/idea/managing-plugins.html#repos
[live-templates]: https://www.jetbrains.com/help/rider/Using_Live_Templates.html
[preview-screenshot]: ./docs/preview-screenshot.png
