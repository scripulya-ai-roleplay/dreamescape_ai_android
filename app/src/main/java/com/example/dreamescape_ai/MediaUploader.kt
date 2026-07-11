package com.example.dreamescape_ai

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
}
