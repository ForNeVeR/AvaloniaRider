package me.fornever.avaloniarider.previewer

import com.intellij.openapi.diagnostic.Logger
import me.fornever.avaloniarider.idea.settings.AvaloniaPreviewerTheme
import java.io.StringReader
import java.util.concurrent.CancellationException
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

internal class ThemeInjectionSettings(
    val isThemeSelectorAvailable: Boolean,
    val selectedTheme: AvaloniaPreviewerTheme,
    val themeApplicableTags: List<String>,
    val darkThemeStyle: String,
    val lightThemeStyle: String
)

private class InjectionMetadata(
    val firstElementLocalName: String,
    val insertionOffset: Int
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

    val metadata = readInjectionMetadata(originalXaml) ?: return originalXaml
    val tagList = settings.themeApplicableTags
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (metadata.firstElementLocalName !in tagList) {
        return originalXaml
    }

    val builder = StringBuilder(originalXaml.length + themeStyle.length + 2)
    builder.append(originalXaml, 0, metadata.insertionOffset)
    builder.append("\n")
    builder.append(themeStyle)
    builder.append("\n")
    builder.append(originalXaml, metadata.insertionOffset, originalXaml.length)
    return builder.toString()
}

private fun readInjectionMetadata(xaml: String): InjectionMetadata? {
    return try {
        val reader = createXmlStreamReader(xaml)
        var firstElementLocalName: String? = null
        var firstStartOffset: Int? = null

        fun buildMetadata(insertionOffset: Int): InjectionMetadata? {
            val localName = firstElementLocalName ?: return null
            if (insertionOffset !in 0..xaml.length) return null
            return InjectionMetadata(localName, insertionOffset)
        }

        while (reader.hasNext()) {
            val eventType = reader.next()
            val offset = reader.location.characterOffset
            if (offset < 0 || offset > xaml.length) return null

            if (firstStartOffset == null) {
                if (eventType == XMLStreamConstants.START_ELEMENT) {
                    firstElementLocalName = reader.localName
                    firstStartOffset = offset
                }
                continue
            }

            if (offset > firstStartOffset) {
                return buildMetadata(offset)
            }
        }

        if (firstStartOffset != null) {
            return buildMetadata(xaml.length)
        }

        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.warn(e)
        null
    }
}

private fun createXmlStreamReader(xaml: String): XMLStreamReader {
    val factory = XMLInputFactory.newFactory().apply {
        setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true)
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    }
    return factory.createXMLStreamReader(StringReader(xaml))
}

private val logger: Logger
    get() = Logger.getInstance("me.fornever.avaloniarider.previewer")
