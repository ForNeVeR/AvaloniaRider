package me.fornever.avaloniarider.test.cases

import me.fornever.avaloniarider.idea.settings.AvaloniaPreviewerTheme
import me.fornever.avaloniarider.previewer.ThemeInjectionSettings
import me.fornever.avaloniarider.previewer.injectThemeIfNeeded
import me.fornever.avaloniarider.previewer.mapInjectedLine
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ThemeInjectionTests {
    private val multilineOriginal = "<UserControl>\n  <TextBlock />\n  <Button />\n</UserControl>"
    private val multilineInjected = injectThemeIfNeeded(
        originalXaml = multilineOriginal,
        settings = ThemeInjectionSettings(
            isThemeSelectorAvailable = true,
            selectedTheme = AvaloniaPreviewerTheme.Light,
            themeApplicableTags = listOf("UserControl"),
            darkThemeStyle = "",
            lightThemeStyle = "<Design.DesignStyle>\n  Light\n</Design.DesignStyle>"
        )
    )

    private fun lineOf(text: String, xaml: String) =
        xaml.lines().indexOfFirst { it.contains(text) } + 1

    @Test
    fun mapInjectedLineKeepsLinesBeforeInjection() {
        assertEquals(1, mapInjectedLine(multilineOriginal, multilineInjected, 1))
    }

    @Test
    fun mapInjectedLineShiftsLinesAfterInjection() {
        for (element in listOf("<TextBlock", "<Button", "</UserControl")) {
            assertEquals(
                lineOf(element, multilineOriginal),
                mapInjectedLine(multilineOriginal, multilineInjected, lineOf(element, multilineInjected))
            )
        }
    }

    @Test
    fun mapInjectedLineDoesNotChangeLinesWithoutInjection() {
        assertEquals(3, mapInjectedLine(multilineOriginal, multilineOriginal, 3))
    }

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
    fun injectThemeIfNeededBrokenXmlAfterMetadataCanStillInject() {
        val original = "<UserControl><TextBlock></UserControl>"
        val style = "<Design.DesignStyle>Dark</Design.DesignStyle>"

        val result = injectThemeIfNeeded(
            originalXaml = original,
            settings = ThemeInjectionSettings(
                isThemeSelectorAvailable = true,
                selectedTheme = AvaloniaPreviewerTheme.Dark,
                themeApplicableTags = listOf("Window", "UserControl"),
                darkThemeStyle = style,
                lightThemeStyle = "<Design.DesignStyle>Light</Design.DesignStyle>"
            )
        )

        assertEquals("<UserControl>\n$style\n<TextBlock></UserControl>", result)
    }

    @Test
    fun injectThemeIfNeededBrokenXmlBeforeMetadataReturnsOriginalMarkup() {
        val original = "<UserControl"

        val result = injectThemeIfNeeded(
            originalXaml = original,
            settings = ThemeInjectionSettings(
                isThemeSelectorAvailable = true,
                selectedTheme = AvaloniaPreviewerTheme.Dark,
                themeApplicableTags = listOf("Window", "UserControl"),
                darkThemeStyle = "<Design.DesignStyle>Dark</Design.DesignStyle>",
                lightThemeStyle = "<Design.DesignStyle>Light</Design.DesignStyle>"
            )
        )

        assertEquals(original, result)
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

    @Test
    fun injectThemeIfNeededPreservesDocumentFormatting() {
        val original = """
            <?xml version="1.0" encoding="utf-8"?>
            <UserControl   xmlns="https://github.com/avaloniaui"
                           xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
                <Grid Margin=" 10 , 20 ">
                    <TextBlock Text="Hi" />
                </Grid>
            </UserControl>
        """.trimIndent()
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

        val rootTagStart = original.indexOf("<UserControl")
        val firstTagEnd = original.indexOf('>', rootTagStart)
        val expected = buildString {
            append(original.substring(0, firstTagEnd + 1))
            append("\n")
            append(style)
            append("\n")
            append(original.substring(firstTagEnd + 1))
        }

        assertEquals(expected, actual)
    }

    @Test
    fun injectThemeIfNeededSpacedTagReturnsOriginalMarkup() {
        val original = "< UserControl ><TextBlock /></ UserControl >"

        val result = injectThemeIfNeeded(
            originalXaml = original,
            settings = ThemeInjectionSettings(
                isThemeSelectorAvailable = true,
                selectedTheme = AvaloniaPreviewerTheme.Dark,
                themeApplicableTags = listOf("Window", "UserControl"),
                darkThemeStyle = "<Design.DesignStyle>Dark</Design.DesignStyle>",
                lightThemeStyle = "<Design.DesignStyle>Light</Design.DesignStyle>"
            )
        )

        assertEquals(original, result)
    }

    @Test
    fun injectThemeIfNeededAttributeWithEscapedAnglesInjects() {
        val original = "<UserControl attr=\"foo bar &lt;&gt;\"><TextBlock /></UserControl>"
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
            "<UserControl attr=\"foo bar &lt;&gt;\">\n$style\n<TextBlock /></UserControl>",
            actual
        )
    }
}
