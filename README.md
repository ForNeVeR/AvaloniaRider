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
- `attachedAvaloniaProperty`
- `avaloniaRoutedEvent`,
- `directProperty`,
- `styledProperty`.

### Plugin Settings
First, for each of the previewer editors you have a choice which project the editor should bind to. The previewer _needs_ some runnable project to work because it observes the UI of your components from the perspective of the project.

Navigate to **Settings → Languages & Frameworks → Avalonia** to see more solution-wide settings.

1. **Previewer method**: either the bitmap-based **AvaloniaRemote** (default) or the browser-based **Html**.
2. **Synchronize the current run configuration and selected project, when possible**: if enabled, then the plugin will try to switch the current project when you switch the active run configuration, if the plugin is able to figure out which project is used by the configuration.
3. **FPS limit** allows limiting the FPS (and thus the CPU usage) of the **AvaloniaRemote** previewer. By default, the previewer tries to provide smooth animation support, which might be unwanted at times.
4. **Previewer working directory** allows you to choose under which directory the previewer should execute:
   - **Defined by MSBuild** will use the `$(RunWorkingDirectory)` MSBuild property (or, generally, what Rider considers the default for the selected project);
   - **Solution directory** will use the directory containing the solution file.

   If you with to override the working directory, you may modify your project file as follows:
   ```xml
   <Project Sdk="Microsoft.NET.Sdk">
       <PropertyGroup>
           <!-- … -->
           <!-- Add this new line! -->
           <RunWorkingDirectory>H:\Your\Custom\Work\Directory</RunWorkingDirectory>
       </PropertyGroup>
       <!-- … -->
   </Project>
   ```

There are also some IDE-wide settings (not tied to a particular solution):
1. **Developer mode**: if enabled, then the plugin will show the **Debug** button in the previewer, allowing to run the previewer process under debugger (in cases when you want to debug preview-specific issues, e.g., blocks under `Design.IsDesignTime`).

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
