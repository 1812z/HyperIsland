package io.github.hyperisland

import android.util.Log
import io.github.hyperisland.utils.AbxXmlDecoder
import io.github.hyperisland.utils.RootShell
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap

data class NotificationChannelRecord(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int,
)

object NotificationChannelReader {
    private const val TAG = "HyperIsland[ChannelReader]"

    fun readChannels(packageName: String): List<NotificationChannelRecord>? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return emptyList()
        val xml = readNotificationPolicyXml()
        if (xml.isEmpty()) return null
        val sanitized = sanitizeInvalidXml(xml)
        val strict = runCatching { parseTextXmlChannels(sanitized, packageName) }
            .onFailure { e -> Log.e(TAG, "strict parse failed for $packageName: ${e.message}", e) }
            .getOrDefault(emptyList())
        if (strict.isNotEmpty()) return strict

        val fallback = runCatching { parseTextXmlChannelsFallback(sanitized, packageName) }
            .onFailure { e -> Log.e(TAG, "fallback parse failed for $packageName: ${e.message}", e) }
            .getOrNull()
        return fallback ?: emptyList()
    }

    private data class PackageFragment(
        val content: String,
        val hasClosingTag: Boolean,
    )

    private fun parseTextXmlChannelsFallback(
        xml: String,
        targetPkg: String,
    ): List<NotificationChannelRecord>? {
        val fragment = extractTargetPackageFragment(xml, targetPkg) ?: return null
        val parser = android.util.Xml.newPullParser()
        val wrappedXml = buildString {
            append("<root>")
            append(fragment.content)
            if (!fragment.hasClosingTag) append("</package>")
            append("</root>")
        }

        return runCatching {
            parser.setInput(StringReader(wrappedXml))
            val channelsById = LinkedHashMap<String, NotificationChannelRecord>()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "channel") {
                    buildChannel(
                        id = parser.getAttributeValue(null, "id"),
                        name = parser.getAttributeValue(null, "name"),
                        description = parser.getAttributeValue(null, "desc"),
                        importance = parser.getAttributeValue(null, "importance"),
                        importanceInt = parser.getAttributeValue(null, "importance-int"),
                    )?.let { channelsById.putIfAbsent(it.id, it) }
                }
                event = parser.next()
            }
            channelsById.values.toList()
        }.getOrNull()
    }

    private fun extractTargetPackageFragment(xml: String, targetPkg: String): PackageFragment? {
        val pattern = Regex("""<package\b[^>]*\bname\s*=\s*(["'])${Regex.escape(targetPkg)}\1[^>]*>""")
        val startMatch = pattern.find(xml) ?: return null
        val startIndex = startMatch.range.first
        if (startMatch.value.trimEnd().endsWith("/>")) {
            return PackageFragment(content = startMatch.value, hasClosingTag = true)
        }

        val closingTag = "</package>"
        val closingIndex = xml.indexOf(closingTag, startIndex)
        if (closingIndex >= 0) {
            return PackageFragment(
                content = xml.substring(startIndex, closingIndex + closingTag.length),
                hasClosingTag = true,
            )
        }
        val nextPackageIndex = xml.indexOf("<package", startIndex + startMatch.value.length)
        return if (nextPackageIndex >= 0) {
            PackageFragment(
                content = xml.substring(startIndex, nextPackageIndex),
                hasClosingTag = false,
            )
        } else {
            PackageFragment(content = xml.substring(startIndex), hasClosingTag = false)
        }
    }

    private fun readNotificationPolicyXml(): String {
        val result = RootShell.run("cat /data/system/notification_policy.xml")
        if (result.exitCode != 0) return ""
        val bytes = result.stdout
        if (bytes.isEmpty()) return ""
        if (AbxXmlDecoder.isAbx(bytes)) {
            return try {
                AbxXmlDecoder.decode(bytes)
            } catch (e: Exception) {
                Log.e(TAG, "AbxXmlDecoder failed: ${e.message}", e)
                ""
            }
        }
        return try {
            bytes.toString(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "decode text xml failed: ${e.message}", e)
            ""
        }
    }

    private fun parseTextXmlChannels(xml: String, targetPkg: String): List<NotificationChannelRecord> {
        val parser = android.util.Xml.newPullParser()
        parser.setInput(StringReader(xml))

        val channelsById = LinkedHashMap<String, NotificationChannelRecord>()
        var inTargetPkg = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "package" -> {
                            val pkg = parser.getAttributeValue(null, "name") ?: ""
                            inTargetPkg = pkg == targetPkg
                        }

                        "channel" -> {
                            if (!inTargetPkg) {
                                event = parser.next()
                                continue
                            }
                            buildChannel(
                                id = parser.getAttributeValue(null, "id"),
                                name = parser.getAttributeValue(null, "name"),
                                description = parser.getAttributeValue(null, "desc"),
                                importance = parser.getAttributeValue(null, "importance"),
                                importanceInt = parser.getAttributeValue(null, "importance-int"),
                            )?.let { channel ->
                                channelsById.putIfAbsent(channel.id, channel)
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "package" && inTargetPkg) {
                        if (channelsById.isNotEmpty()) {
                            return channelsById.values.toList()
                        }
                        // 某些 ROM 会有多个同名 package 条目，前一个可能不带 channel，继续向后查找。
                        inTargetPkg = false
                    }
                }
            }
            event = parser.next()
        }

        return channelsById.values.toList()
    }

    private fun buildChannel(
        id: String?,
        name: String?,
        description: String?,
        importance: String?,
        importanceInt: String?,
    ): NotificationChannelRecord? {
        val channelId = id?.takeIf { it.isNotBlank() } ?: return null
        return NotificationChannelRecord(
            id = channelId,
            name = name ?: channelId,
            description = description ?: "",
            importance = (importance ?: importanceInt)?.toIntOrNull() ?: 3,
        )
    }

    private fun sanitizeInvalidXml(xml: String): String {
        val sanitizedEntities = Regex("""&#(x[0-9A-Fa-f]+|\d+);""").replace(xml) { match ->
            val raw = match.groupValues[1]
            val codePoint = if (raw.startsWith("x", ignoreCase = true)) {
                raw.substring(1).toIntOrNull(16)
            } else {
                raw.toIntOrNull()
            }
            if (codePoint != null && !isValidXmlCodePoint(codePoint)) "" else match.value
        }

        return buildString(sanitizedEntities.length) {
            sanitizedEntities.forEach { ch ->
                if (isValidXmlCodePoint(ch.code)) append(ch)
            }
        }
    }

    private fun isValidXmlCodePoint(codePoint: Int): Boolean {
        return codePoint == 0x9 ||
            codePoint == 0xA ||
            codePoint == 0xD ||
            codePoint in 0x20..0xD7FF ||
            codePoint in 0xE000..0xFFFD ||
            codePoint in 0x10000..0x10FFFF
    }
}
