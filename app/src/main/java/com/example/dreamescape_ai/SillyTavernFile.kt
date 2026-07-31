package com.example.dreamescape_ai

import org.json.JSONException
import org.json.JSONObject

/**
 * Naive SillyTavern file detection.
 *
 * The backend's `/api/v1/import/lorebook` accepts two SillyTavern JSON shapes:
 *  - World Info / lorebook files: a top-level `entries` object/array.
 *  - V2/V3 character cards (`spec: "chara_card_v2"`…), whose embedded
 *    `data.character_book.entries` the backend also reads.
 *
 * This is a *fast client-side guard* so we can fail early with a clear message
 * before uploading an obviously-wrong file. It does not validate the contents —
 * the backend re-parses authoritatively and returns precise candidates.
 */
sealed interface SillyTavernFileResult {
    /** Looks like a SillyTavern lorebook or character card. */
    object Valid : SillyTavernFileResult
    /** The bytes are not parseable JSON. */
    data class NotJson(val error: String) : SillyTavernFileResult
    /** Valid JSON, but none of the SillyTavern markers were found. */
    object NotSillyTavern : SillyTavernFileResult
}

object SillyTavernFile {

    private val CARD_SPECS = setOf("chara_card_v1", "chara_card_v2", "chara_card_v3")

    fun classify(bytes: ByteArray): SillyTavernFileResult {
        val root = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: JSONException) {
            return SillyTavernFileResult.NotJson(e.message ?: "Invalid JSON")
        }

        // Standalone lorebook / World Info file.
        if (root.has("entries")) return SillyTavernFileResult.Valid

        // Character card spec marker.
        val spec = root.optString("spec")
        if (spec.isNotEmpty() && spec.lowercase() in CARD_SPECS) {
            return SillyTavernFileResult.Valid
        }

        // Embedded lorebook inside a card, or a card-shaped data block.
        val data = root.optJSONObject("data")
        if (data != null) {
            val book = data.optJSONObject("character_book")
            if (book != null && book.has("entries")) return SillyTavernFileResult.Valid
            if (data.has("name") && data.has("description")) return SillyTavernFileResult.Valid
        }

        return SillyTavernFileResult.NotSillyTavern
    }
}
