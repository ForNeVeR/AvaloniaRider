Maintainership
==============

Release
-------

To release a new version, follow these steps.

1. Update the copyright year in the `LICENSE.md`, if required.
2. Choose the new version according to [Semantic Versioning][semver]. It should consist of three numbers (i.e. `1.0.0`).
3. Change the `pluginVersionBase` property of the `gradle.properties`.
4. Make sure there's a properly formed version entry in the `CHANGELOG.md`.
5. Merge these changes via a PR.
6. Push a tag named `v<VERSION>` to GitHub.

The new release will be published automatically.

Update Token
------------

To update the token used for publishing the plugin, follow these steps.

1. Go to [the Marketplace token update section][marketplace.tokens].
2. Drop the old token called `github.avalonia-rider`.
3. Create a new one with the same name.
4. Go to [the GitHub repository's Secrets page][github.secrets].
5. Update the `JETBRAINS_MARKETPLACE_TOKEN` with the new value.

[github.secrets]: https://github.com/ForNeVeR/AvaloniaRider/settings/secrets/actions
[marketplace.tokens]: https://plugins.jetbrains.com/author/me/tokens
[semver]: https://semver.org/spec/v2.0.0.html
