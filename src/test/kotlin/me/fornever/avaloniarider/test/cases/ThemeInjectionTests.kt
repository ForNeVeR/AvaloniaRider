package me.fornever.avaloniarider.test.cases

import me.fornever.avaloniarider.idea.settings.AvaloniaPreviewerTheme
import me.fornever.avaloniarider.previewer.ThemeInjectionSettings
import me.fornever.avaloniarider.previewer.injectThemeIfNeeded
import org.testng.annotations.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeInjectionTests {
    @Test
    fun injectThemeIfNeededHappyPath() {
        val original = "<UserControl><TextBlock /></UserControl>"
        val style = "<Design.DesignStyle>Light</Design.DesignStyle>"

        val actual = injectThemeIfNeeded(
            originalXaml = original,
            settings = ThemeInjectionSettings(
                isThemeSelectorAvailable = true,
                selectedTheme = AvaloniaPreviewerTheme.Light,
                themeApplicableTags = listOf("Window", "UserControl"),
                darkThemeStyle = "<Design.DesignStyle>Dark</Design.DesignStyle>",
                lightThemeStyle = style
            )
        )

        assertEquals(
            "<UserControl>\n$style\n<TextBlock /></UserControl>",
            actual
        )
    }

    @Test
    fun injectThemeIfNeededBrokenXmlReturnsOriginalMarkup() {
        val original = "<UserControl><TextBlock></UserControl>"

        val result = runCatching {
            injectThemeIfNeeded(
                originalXaml = original,
                settings = ThemeInjectionSettings(
                    isThemeSelectorAvailable = true,
                    selectedTheme = AvaloniaPreviewerTheme.Dark,
                    themeApplicableTags = listOf("Window", "UserControl"),
                    darkThemeStyle = "<Design.DesignStyle>Dark</Design.DesignStyle>",
                    lightThemeStyle = "<Design.DesignStyle>Light</Design.DesignStyle>"
                )
            )
        }

        assertTrue(result.isSuccess, "Expected broken XML input to be handled without throwing")
        assertEquals(original, result.getOrThrow())
    }

    @Test
    fun injectThemeIfNeededUnsupportedRootTagReturnsOriginalMarkup() {
        val original = "<Canvas><TextBlock /></Canvas>"

        val actual = injectThemeIfNeeded(
            originalXaml = original,
            settings = ThemeInjectionSettings(
                isThemeSelectorAvailable = true,
                selectedTheme = AvaloniaPreviewerTheme.Dark,
                themeApplicableTags = listOf("Window", "UserControl"),
                darkThemeStyle = "<Design.DesignStyle>Dark</Design.DesignStyle>",
                lightThemeStyle = "<Design.DesignStyle>Light</Design.DesignStyle>"
            )
        )

        assertEquals(original, actual)
    }

    @Test
    fun injectThemeIfNeededNoThemeReturnsOriginalMarkup() {
        val original = "<UserControl><TextBlock /></UserControl>"

        val actual = injectThemeIfNeeded(
            originalXaml = original,
            settings = ThemeInjectionSettings(
                isThemeSelectorAvailable = true,
                selectedTheme = AvaloniaPreviewerTheme.None,
                themeApplicableTags = listOf("Window", "UserControl"),
                darkThemeStyle = "<Design.DesignStyle>Dark</Design.DesignStyle>",
                lightThemeStyle = "<Design.DesignStyle>Light</Design.DesignStyle>"
            )
        )

        assertEquals(original, actual)
    }
}
