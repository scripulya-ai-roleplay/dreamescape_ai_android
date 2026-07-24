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
 * Derives the visible text for a model reply that is still streaming in.
 *
 * The agent emits the same `{"text": ...}` envelope (optionally in a ```json fence)
 * token by token, so mid-stream the buffer is incomplete JSON — [extractModelMessageText]
 * can't parse it yet. This unwraps the inner text value incrementally:
 *  - once `"text":` and its opening quote have arrived, the value's content is
 *    unescaped up to the closing quote (or the buffer's end, while still streaming);
 *  - while only the JSON scaffolding (`{`, fence, `"text":` …) has arrived, nothing
 *    is shown so the raw scaffolding never leaks into the bubble;
 *  - if the stream is plain prose with no envelope, it is returned verbatim.
 *
 * Anything still partial self-corrects the instant the authoritative message lands.
 */
fun streamingDisplayText(raw: String): String {
    val key = "\"text\""
    val keyIndex = raw.indexOf(key)
    return when {
        keyIndex >= 0 -> unwrapTextValue(raw, keyIndex + key.length)
        raw.trimStart().startsWith("{") || raw.trimStart().startsWith("```") -> ""
        else -> raw
    }
}

/**
 * Unescapes the string value beginning after the `"text"` key in [raw], starting the
 * scan at [from]. Returns "" until the opening quote of the value has arrived, and
 * stops at the first unescaped closing quote (the JSON is complete) or at the buffer
 * end (still streaming). Unknown escape sequences are kept verbatim.
 */
private fun unwrapTextValue(raw: String, from: Int): String {
    var i = from
    while (i < raw.length && (raw[i].isWhitespace() || raw[i] == ':' || raw[i] == '=')) i++
    if (i >= raw.length || raw[i] != '"') return ""
    i++ // past the opening quote
    val out = StringBuilder()
    var escaped = false
    while (i < raw.length) {
        val c = raw[i]
        if (escaped) {
            out.append(unescapeChar(c))
            escaped = false
        } else when {
            c == '\\' -> escaped = true
            c == '"' -> return out.toString() // closing quote reached
            else -> out.append(c)
        }
        i++
    }
    return out.toString() // no closing quote yet — still streaming
}

private fun unescapeChar(c: Char): Char = when (c) {
    '"' -> '"'
    '\\' -> '\\'
    'n' -> '\n'
    't' -> '\t'
    'r' -> '\r'
    else -> c
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
