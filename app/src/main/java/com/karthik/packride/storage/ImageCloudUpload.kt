package com.karthik.packride.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.auth.FirebaseAuth
import java.io.ByteArrayOutputStream

/**
 * Image upload to Firebase Storage — mirrors gpx/GpxCloudUpload.kt's
 * upload-then-fetch-download-url pattern, and the EXACT bucket-path convention
 * iOS's FirebaseManager.uploadProfileImage / RideFeedManager.postRide use (read
 * straight from PackRide/FirebaseManager.swift and RideFeedManager.swift), so a
 * photo written by either platform is visible on both:
 *   - Avatar:     users/{uid}/avatar.jpg    (iOS: Storage.reference().child("users/\(uid)/avatar.jpg"))
 *   - Feed photo: feedPhotos/{postId}.jpg   (iOS: Storage.reference().child("feedPhotos/\(postID).jpg"))
 *
 * Aug 31, 2026 -- fixed from an earlier posts/{postId}/photo.jpg path that
 * didn't match iOS at all; a photo posted from one platform would have been
 * invisible (404) when the feed was viewed on the other.
 *
 * Downscales to a max 1600px edge and recompresses to JPEG @ 75% quality
 * before upload — same ballpark as iOS's UIGraphicsImageRenderer resize +
 * jpegData(compressionQuality: 0.75) pass in ProfileView/RideFeedView, so a
 * phone photo doesn't ship multi-MB over the wire.
 */
object ImageCloudUpload {
    private const val MAX_DIMENSION = 1600
    private const val JPEG_QUALITY = 75

    fun uploadAvatar(context: Context, uid: String, imageUri: Uri, onResult: (Result<String>) -> Unit) {
        upload(context, imageUri, "users/$uid/avatar.jpg", onResult)
    }

    fun uploadBanner(context: Context, uid: String, imageUri: Uri, onResult: (Result<String>) -> Unit) {
        upload(context, imageUri, "users/$uid/banner.jpg", onResult)
    }

    fun uploadFeedPhoto(context: Context, postId: String, imageUri: Uri, onResult: (Result<String>) -> Unit) {
        upload(context, imageUri, "feedPhotos/$postId.jpg", onResult, FirebaseAuth.getInstance().currentUser?.uid)
    }

    private fun upload(context: Context, imageUri: Uri, path: String, onResult: (Result<String>) -> Unit, ownerUid: String? = null) {
        val bytes = try {
            compressImage(context, imageUri)
        } catch (e: Exception) {
            onResult(Result.failure(e))
            return
        }
        if (bytes == null) {
            onResult(Result.failure(IllegalStateException("Couldn't read the selected image.")))
            return
        }
        val ref = FirebaseStorage.getInstance().reference.child(path)
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").apply {
            ownerUid?.let { setCustomMetadata("ownerUid", it) }
        }.build()
        ref.putBytes(bytes, metadata)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                ref.downloadUrl
            }
            .addOnSuccessListener { uri -> onResult(Result.success(uri.toString())) }
            .addOnFailureListener { e -> onResult(Result.failure(e)) }
    }

    /** Decodes with a downsample pass first (avoids OOM on huge camera photos), then a final scale + JPEG re-encode. */
    private fun compressImage(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        var sampleSize = 1
        while (srcW / (sampleSize * 2) >= MAX_DIMENSION && srcH / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: return null

        val scale = minOf(1f, MAX_DIMENSION.toFloat() / maxOf(decoded.width, decoded.height))
        val bitmap = if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            decoded.recycle()
            scaled
        } else decoded

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}
