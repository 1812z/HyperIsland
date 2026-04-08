package io.github.hyperisland.core.data

import android.os.Build
import android.util.Log
import io.github.hyperisland.utils.AbxXmlDecoder
import io.github.hyperisland.utils.RootShell
import java.util.LinkedHashMap

internal class NotificationChannelRepository(
    private val tag: String = "HyperIsland",
) {
    private data class StrictParseResult(
        val channels: List<Map<String, Any?>>,
        val enteredTargetPackage: Boolean,
        val completedTargetPackage: Boolean,
    )

    private data class PackageFragment(
        val content: String,
        val endReason: String,
        val hasClosingTag: Boolean,
    )

    private data class FallbackParseResult(
        val channels: List<Map<String, Any?>>,
        val source: String,
    )

    fun getNotificationChannelsForPackage(pkg: String): List<Map<String, Any?>>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        return tryGetChannelsFromPolicyFile(pkg)
    }

    private fun tryGetChannelsFromPolicyFile(pkg: String): List<Map<String, Any?>>? {
        val xml = try {
            convertAbxPolicyToXml()
        } catch (e: Exception) {
            Log.e(tag, "convertAbxPolicyToXml failed for $pkg: ${e.message}", e)
            return null
        }

        if (xml.isEmpty()) {
            Log.w(tag, "convertAbxPolicyToXml: empty (ROOT?)")
            return null
        }

        val sanitizedXml = sanitizeInvalidXml(xml)
        Log.d(tag, "policy xml: ${xml.length} chars, sanitized=${sanitizedXml.length} chars, targetPkg=$pkg")

        return try {
            val strictResult = try {
                parseTextXmlChannels(sanitizedXml, pkg)
            } catch (e: Exception) {
                Log.e(tag, "strict parse failed for $pkg: ${e.message}")
                null
            }

            if (strictResult != null) {
                Log.d(
                    tag,
                    "strict parse state: targetPkg=$pkg entered=${strictResult.enteredTargetPackage} completed=${strictResult.completedTargetPackage} count=${strictResult.channels.size}"
                )
                if (strictResult.completedTargetPackage) {
                    logChannelSource(pkg, "strict", strictResult.channels.size)
                    return strictResult.channels
                }
            }

            if (strictResult == null) {
                Log.d(tag, "fallback parse start: targetPkg=$pkg reason=strict-error")
                val fallbackResult = parseTextXmlChannelsFallback(sanitizedXml, pkg)
                if (fallbackResult != null) {
                    logChannelSource(pkg, fallbackResult.source, fallbackResult.channels.size)
                    return fallbackResult.channels
                }
            }

            logChannelSource(pkg, "empty", 0)
            emptyList()
        } catch (e: Exception) {
            Log.e(tag, "tryGetChannelsFromPolicyFile parse flow failed for $pkg: ${e.message}", e)
            logChannelSource(pkg, "empty", 0)
            emptyList()
        }
    }

    private fun convertAbxPolicyToXml(): String {
        cleanupLegacyPolicyTempFiles()

        val policyBytes = try {
            readNotificationPolicyBytes()
        } catch (e: Exception) {
            Log.e(tag, "readNotificationPolicyBytes failed: ${e.message}", e)
            cleanupLegacyPolicyTempFiles()
            return ""
        }

        if (policyBytes.isEmpty()) {
            cleanupLegacyPolicyTempFiles()
            return ""
        }

        return try {
            val xml = AbxXmlDecoder.decode(policyBytes)
            Log.d(tag, "local abx2xml ok: abx=${policyBytes.size} bytes, xml=${xml.length} chars")
            xml
        } catch (e: Exception) {
            Log.e(tag, "AbxXmlDecoder failed: ${e.message}", e)
            ""
        } finally {
            cleanupLegacyPolicyTempFiles()
        }
    }

    private fun readNotificationPolicyBytes(): ByteArray {
        val input = "/data/system/notification_policy.xml"
        val result = RootShell.run("cat $input")
        if (result.exitCode != 0) {
            Log.d(
                tag,
                "notification_policy read failed: exit=${result.exitCode}, bytes=${result.stdout.size}, stderr=${result.stderr.take(120)}"
            )
            return byteArrayOf()
        }

        if (!AbxXmlDecoder.isAbx(result.stdout)) {
            Log.d(tag, "notification_policy read failed: expected ABX, got ${result.stdout.size} bytes")
            return byteArrayOf()
        }

        Log.d(tag, "notification_policy read ok: ${result.stdout.size} bytes")
        return result.stdout
    }

    private fun cleanupLegacyPolicyTempFiles() {
        val tempFiles = listOf(
            "/data/local/tmp/.hyp_policy.xml",
            "/data/local/tmp/.hyp_policy_snapshot.abx",
        )
        try {
            val result = RootShell.run("rm -f ${tempFiles.joinToString(separator = " ")} 2>/dev/null")
            if (result.exitCode != 0) {
                Log.d(tag, "policy temp cleanup failed: exit=${result.exitCode}, stderr=${result.stderr.take(120)}")
            }
        } catch (e: Exception) {
            Log.d(tag, "policy temp cleanup failed: ${e.message}")
        }
    }

    private fun parseTextXmlChannels(xml: String, targetPkg: String): StrictParseResult {
        val result = mutableListOf<Map<String, Any?>>()
        val parser = android.util.Xml.newPullParser()
        parser.setInput(java.io.StringReader(xml))

        var inTarget = false
        var enteredTarget = false
        var ev = parser.eventType
        while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (ev) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                    "package" -> {
                        val packageName = parser.getAttributeValue(null, "name")
                        if (packageName == targetPkg) {
                            inTarget = true
                            enteredTarget = true
                            Log.d(tag, "strict parse entered target package: $targetPkg")
                        }
                    }

                    "channel" -> if (inTarget) {
                        buildChannelMap(
                            id = parser.getAttributeValue(null, "id"),
                            name = parser.getAttributeValue(null, "name"),
                            description = parser.getAttributeValue(null, "desc"),
                            importance = parser.getAttributeValue(null, "importance"),
                            importanceInt = parser.getAttributeValue(null, "importance-int"),
                        )?.let(result::add)
                    }
                }

                org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.name == "package" && inTarget) {
                    if (result.isNotEmpty()) {
                        Log.d(tag, "strict parse completed target package: $targetPkg, count=${result.size}")
                        return StrictParseResult(
                            channels = result,
                            enteredTargetPackage = enteredTarget,
                            completedTargetPackage = true,
                        )
                    }

                    Log.d(tag, "strict parse: $targetPkg entry had no channels, continuing search")
                    inTarget = false
                }
            }
            ev = parser.next()
        }

        return StrictParseResult(
            channels = result,
            enteredTargetPackage = enteredTarget,
            completedTargetPackage = false,
        )
    }

    private fun parseTextXmlChannelsFallback(xml: String, targetPkg: String): FallbackParseResult? {
        val fragment = extractTargetPackageFragment(xml, targetPkg) ?: run {
            Log.d(tag, "fallback fragment not found: targetPkg=$targetPkg")
            return null
        }

        Log.d(
            tag,
            "fallback fragment found: targetPkg=$targetPkg endReason=${fragment.endReason} hasClosingTag=${fragment.hasClosingTag} length=${fragment.content.length}"
        )

        val fragmentChannels = tryParseChannelsFromFragment(fragment) ?: return null
        Log.d(tag, "fallback fragment parser result: targetPkg=$targetPkg count=${fragmentChannels.size}")
        return FallbackParseResult(fragmentChannels, "fallback-fragment")
    }

    private fun extractTargetPackageFragment(xml: String, targetPkg: String): PackageFragment? {
        val pattern = Regex("""<package\b[^>]*\bname\s*=\s*(["'])${Regex.escape(targetPkg)}\1[^>]*>""")
        val startMatch = pattern.find(xml) ?: return null
        val startIndex = startMatch.range.first
        if (startMatch.value.trimEnd().endsWith("/>")) {
            return PackageFragment(
                content = startMatch.value,
                endReason = "self-closing",
                hasClosingTag = true,
            )
        }

        val closingTag = "</package>"
        val closingIndex = xml.indexOf(closingTag, startIndex)
        if (closingIndex >= 0) {
            return PackageFragment(
                content = xml.substring(startIndex, closingIndex + closingTag.length),
                endReason = "closing-tag",
                hasClosingTag = true,
            )
        }

        val nextPackageIndex = xml.indexOf("<package", startIndex + startMatch.value.length)
        if (nextPackageIndex >= 0) {
            return PackageFragment(
                content = xml.substring(startIndex, nextPackageIndex),
                endReason = "next-package",
                hasClosingTag = false,
            )
        }

        return PackageFragment(
            content = xml.substring(startIndex),
            endReason = "eof",
            hasClosingTag = false,
        )
    }

    private fun tryParseChannelsFromFragment(fragment: PackageFragment): List<Map<String, Any?>>? {
        val parser = android.util.Xml.newPullParser()
        val wrappedXml = buildString {
            append("<root>")
            append(fragment.content)
            if (!fragment.hasClosingTag) append("</package>")
            append("</root>")
        }

        return try {
            parser.setInput(java.io.StringReader(wrappedXml))
            val channelsById = LinkedHashMap<String, Map<String, Any?>>()
            var ev = parser.eventType
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (ev == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "channel") {
                    buildChannelMap(
                        id = parser.getAttributeValue(null, "id"),
                        name = parser.getAttributeValue(null, "name"),
                        description = parser.getAttributeValue(null, "desc"),
                        importance = parser.getAttributeValue(null, "importance"),
                        importanceInt = parser.getAttributeValue(null, "importance-int"),
                    )?.let { channel ->
                        channelsById.putIfAbsent(channel["id"] as String, channel)
                    }
                }
                ev = parser.next()
            }
            channelsById.values.toList()
        } catch (e: Exception) {
            Log.d(tag, "fallback fragment parser failed: ${e.message}")
            null
        }
    }

    private fun sanitizeInvalidXml(xml: String): String {
        var removedEntityRefs = 0
        val sanitizedEntities = Regex("""&#(x[0-9A-Fa-f]+|\d+);""").replace(xml) { match ->
            val raw = match.groupValues[1]
            val codePoint = if (raw.startsWith("x", ignoreCase = true)) {
                raw.substring(1).toIntOrNull(16)
            } else {
                raw.toIntOrNull()
            }

            if (codePoint != null && !isValidXmlCodePoint(codePoint)) {
                removedEntityRefs += 1
                ""
            } else {
                match.value
            }
        }

        var removedRawChars = 0
        val sanitizedText = buildString(sanitizedEntities.length) {
            sanitizedEntities.forEach { ch ->
                if (isValidXmlChar(ch)) {
                    append(ch)
                } else {
                    removedRawChars += 1
                }
            }
        }

        if (removedEntityRefs > 0 || removedRawChars > 0) {
            Log.w(
                tag,
                "sanitizeInvalidXml removed invalid content: entityRefs=$removedEntityRefs rawChars=$removedRawChars"
            )
        }
        return sanitizedText
    }

    private fun isValidXmlCodePoint(codePoint: Int): Boolean {
        return codePoint == 0x9 ||
            codePoint == 0xA ||
            codePoint == 0xD ||
            codePoint in 0x20..0xD7FF ||
            codePoint in 0xE000..0xFFFD ||
            codePoint in 0x10000..0x10FFFF
    }

    private fun isValidXmlChar(ch: Char): Boolean {
        return isValidXmlCodePoint(ch.code)
    }

    private fun buildChannelMap(
        id: String?,
        name: String?,
        description: String?,
        importance: String?,
        importanceInt: String?,
    ): Map<String, Any?>? {
        val channelId = id?.takeIf { it.isNotEmpty() } ?: return null
        return mapOf(
            "id" to channelId,
            "name" to (name ?: channelId),
            "description" to (description ?: ""),
            "importance" to ((importance ?: importanceInt)?.toIntOrNull() ?: 3),
        )
    }

    private fun logChannelSource(pkg: String, source: String, count: Int) {
        Log.d(tag, "text XML result: targetPkg=$pkg source=$source count=$count")
    }
}
