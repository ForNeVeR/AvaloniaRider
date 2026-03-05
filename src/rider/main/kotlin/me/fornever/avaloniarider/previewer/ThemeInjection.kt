package me.fornever.avaloniarider.previewer

import me.fornever.avaloniarider.idea.settings.AvaloniaPreviewerTheme
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

internal data class ThemeInjectionSettings(
    val isThemeSelectorAvailable: Boolean,
    val selectedTheme: AvaloniaPreviewerTheme,
    val themeApplicableTags: List<String>,
    val darkThemeStyle: String,
    val lightThemeStyle: String
)

internal fun injectThemeIfNeeded(
    originalXaml: String,
    settings: ThemeInjectionSettings
): String {
    if (!settings.isThemeSelectorAvailable) return originalXaml

    val themeStyle = when (settings.selectedTheme) {
        AvaloniaPreviewerTheme.Light -> settings.lightThemeStyle
        AvaloniaPreviewerTheme.Dark -> settings.darkThemeStyle
        AvaloniaPreviewerTheme.None -> return originalXaml
    }

    val rootTagName = parseRootTagName(originalXaml) ?: return originalXaml
    val tagList = settings.themeApplicableTags.map { it.trim() }.filter { it.isNotEmpty() }
    val rootTagShortName = rootTagName.substringAfter(':')
    if (rootTagName !in tagList && rootTagShortName !in tagList) {
        return originalXaml
    }

    val firstTagEnd = findRootOpeningTagEnd(originalXaml, rootTagName) ?: return originalXaml
    return buildString {
        append(originalXaml.substring(0, firstTagEnd + 1))
        append("\n")
        append(themeStyle)
        append("\n")
        append(originalXaml.substring(firstTagEnd + 1))
    }
}

private fun parseRootTagName(xaml: String): String? {
    return try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val builder = factory.newDocumentBuilder()
        builder.setErrorHandler(object : ErrorHandler {
            override fun warning(exception: SAXParseException?) {}
            override fun error(exception: SAXParseException?) {}
            override fun fatalError(exception: SAXParseException?) {}
        })
        val document = builder.parse(InputSource(StringReader(xaml)))
        document.documentElement?.tagName
    } catch (_: Exception) {
        null
    }
}

private fun findRootOpeningTagEnd(xaml: String, rootTagName: String): Int? {
    var searchIndex = 0
    while (searchIndex < xaml.length) {
        val tagStart = xaml.indexOf('<', searchIndex)
        if (tagStart == -1 || tagStart + 1 >= xaml.length) return null

        val marker = xaml[tagStart + 1]
        if (marker == '?' || marker == '!' || marker == '/') {
            searchIndex = tagStart + 1
            continue
        }

        val nameEnd = xaml.indexOfAny(charArrayOf(' ', '\n', '\r', '\t', '>', '/'), tagStart + 1)
        if (nameEnd == -1) return null

        val tagName = xaml.substring(tagStart + 1, nameEnd)
        if (tagName == rootTagName) {
            return xaml.indexOf('>', nameEnd).takeIf { it != -1 }
        }

        searchIndex = nameEnd
    }

    return null
}
