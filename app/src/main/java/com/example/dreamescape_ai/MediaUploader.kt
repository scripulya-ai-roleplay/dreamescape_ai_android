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
import org.openapitools.client.models.ApiResponseMediaAssetDTO
import org.openapitools.client.models.MediaEntityType
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Uploads a media asset with a hand-built multipart/form-data request.
 *
 * The generated [org.openapitools.client.apis.MediaApi.uploadMediaApiV1MediaPost]
 * takes `file: kotlin.String` (the spec declares the field with OAS 3.1
 * `contentMediaType` rather than `format: binary`, so the generator emits a
 * String), and the shared multipart infra only sends `File` bodies as binary
 * parts — a String would be serialized as plain text. To actually upload bytes
 * we build the multipart body ourselves, reuse the authed
 * [ApiClient.defaultClient], and parse the JSON reply with the shared
 * [Serializer.moshi].
 */
object MediaUploader {

    fun upload(
        file: File,
        mimeType: String,
        entityType: MediaEntityType,
        entityId: UUID,
        isPublic: Boolean?
    ): ApiResponseMediaAssetDTO {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("entity_type", entityType.value)
            .addFormDataPart("entity_id", entityId.toString())
            .apply { if (isPublic != null) addFormDataPart("is_public", isPublic.toString()) }
            .addFormDataPart("file", file.name, file.asRequestBody(mimeType.toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url("${DreamescapeApplication.BACKEND_BASE_URL}/api/v1/media/")
            .post(body)
            .build()

        ApiClient.defaultClient.newCall(request).execute().use { response ->
            val raw = response.body?.string()
            if (!response.isSuccessful) {
                throw ClientException(
                    "Media upload failed: HTTP ${response.code} ${raw.orEmpty()}",
                    response.code
                )
            }
            return Serializer.moshi.adapter(ApiResponseMediaAssetDTO::class.java).fromJson(raw)
                ?: throw IOException("Failed to parse media upload response")
        }
    }

    /**
     * Convenience overload for a content [Uri] (e.g. from the Photo Picker): copies
     * the stream into a cache temp file, uploads it, then deletes the temp file.
     */
    fun uploadUri(
        context: Context,
        uri: Uri,
        entityType: MediaEntityType,
        entityId: UUID,
        isPublic: Boolean?
    ): ApiResponseMediaAssetDTO {
        val mimeType = mimeTypeOf(context, uri) ?: "image/jpeg"
        val file = copyUriToCacheFile(context, uri, mimeType)
        return try {
            upload(file, mimeType, entityType, entityId, isPublic)
        } finally {
            file.delete()
        }
    }

    private fun mimeTypeOf(context: Context, uri: Uri): String? =
        runCatching { context.contentResolver.getType(uri) }.getOrNull()

    private fun copyUriToCacheFile(context: Context, uri: Uri, mimeType: String): File {
        val ext = when {
            mimeType.contains("png", ignoreCase = true) -> ".png"
            mimeType.contains("webp", ignoreCase = true) -> ".webp"
            mimeType.contains("gif", ignoreCase = true) -> ".gif"
            else -> ".jpg"
        }
        val file = File.createTempFile("upload_", ext, context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Cannot read selected image: $uri")
        return file
    }
}
