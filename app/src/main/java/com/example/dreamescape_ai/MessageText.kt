package com.example.dreamescape_ai

import org.json.JSONObject

/**
 * The model wraps every reply in a fenced JSON block:
 *
 * ```json
 * { "text": "..." }
 * ```
 *
 * This extracts the inner `text` so the chat renders plain prose instead of the
 * raw envelope. Anything that isn't that shape — user messages, legacy/mock
 * content, or a malformed reply — is returned verbatim so it renders as-is.
 */
fun extractModelMessageText(raw: String): String {
    val candidate = stripJsonCodeFence(raw.trim())
    val json = runCatching { JSONObject(candidate) }.getOrNull() ?: return raw
    if (!json.has("text")) return raw
    val text = json.opt("text")
    return text as? String ?: raw
}

/**
 * Truncates [text] to the first [max] characters, appending "..." when anything
 * is cut off. Used for chat list message previews.
 */
fun truncateForPreview(text: String, max: Int = 100): String =
    if (text.length <= max) text else text.take(max) + "..."

/**
 * If [text] is wrapped in a ``` (optionally ```json) code fence, returns the
 * content between the fences; otherwise returns [text] unchanged.
 */
private fun stripJsonCodeFence(text: String): String {
    val fenceStart = text.indexOf("```")
    if (fenceStart < 0) return text
    val firstNewline = text.indexOf('\n', fenceStart)
    if (firstNewline < 0) return text
    val closing = text.lastIndexOf("```")
    if (closing <= firstNewline) return text
    return text.substring(firstNewline + 1, closing).trim()
}
