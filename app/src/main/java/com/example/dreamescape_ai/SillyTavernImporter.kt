package com.example.dreamescape_ai

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.openapitools.client.infrastructure.ApiClient
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.infrastructure.Serializer
import org.openapitools.client.models.ApiResponseImportLorebookResultDTO
import org.openapitools.client.models.ApiResponseImportPreviewDTO
import java.io.File
import java.io.IOException

/**
 * Hand-built multipart calls for the SillyTavern import endpoints.
 *
 * The generated [org.openapitools.client.apis.ImportApi] declares the `file`
 * part as `kotlin.String` (the OAS 3.1 spec uses `contentMediaType` instead of
 * `format: binary`, so the generator never emits a binary part) — the same quirk
 * as [MediaUploader]. So, like media uploads, we build the multipart body
 * ourselves, reuse the authed [ApiClient.defaultClient] (JWT is added by
 * [com.example.dreamescape_ai.auth.JwtAuthInterceptor]), and parse the JSON reply
 * with the shared [Serializer.moshi].
 */
object SillyTavernImporter {

    private const val JSON = "application/json"
    private const val PREVIEW_PATH = "/api/v1/import/lorebook/preview"
    private const val IMPORT_PATH = "/api/v1/import/lorebook"

    /** Parse-only preview: returns the candidate characters & scenes in [file]. */
    fun preview(file: File): ApiResponseImportPreviewDTO {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(JSON.toMediaTypeOrNull()))
            .build()
        val request = Request.Builder()
            .url("${DreamescapeApplication.BACKEND_BASE_URL}$PREVIEW_PATH")
            .post(body)
            .build()
        return execute(request, ApiResponseImportPreviewDTO::class.java)
    }

    /**
     * Import the entries whose keys are in [selectedKeys]. Pass [linkScenes] = false
     * to keep characters and scenes independent (the user links them later).
     */
    fun importLorebook(
        file: File,
        selectedKeys: List<String>,
        isPublic: Boolean,
        importImages: Boolean,
        linkScenes: Boolean = false,
    ): ApiResponseImportLorebookResultDTO {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("is_public", isPublic.toString())
            .addFormDataPart("import_images", importImages.toString())
            .addFormDataPart("link_scenes", linkScenes.toString())
        // One repeated form part per key; FastAPI collects these into a list.
        for (key in selectedKeys) {
            builder.addFormDataPart("selected_keys", key)
        }
        builder.addFormDataPart("file", file.name, file.asRequestBody(JSON.toMediaTypeOrNull()))

        val request = Request.Builder()
            .url("${DreamescapeApplication.BACKEND_BASE_URL}$IMPORT_PATH")
            .post(builder.build())
            .build()
        return execute(request, ApiResponseImportLorebookResultDTO::class.java)
    }

    /** Copies a picked content [uri] into a cache file suitable for upload. */
    fun copyToFile(context: Context, uri: Uri): File {
        val file = File.createTempFile("st_import_", ".json", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Cannot read selected file: $uri")
        return file
    }

    private fun <T> execute(request: Request, type: Class<T>): T {
        ApiClient.defaultClient.newCall(request).execute().use { response ->
            val raw = response.body?.string()
            if (!response.isSuccessful) {
                throw ClientException(
                    "SillyTavern import failed: HTTP ${response.code} ${raw.orEmpty()}",
                    response.code
                )
            }
            return Serializer.moshi.adapter(type).fromJson(raw)
                ?: throw IOException("Failed to parse import response")
        }
    }
}
