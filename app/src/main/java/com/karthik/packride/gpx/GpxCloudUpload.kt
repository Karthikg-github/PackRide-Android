package com.karthik.packride.gpx

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.io.File

/**
 * GPX upload to Firebase Storage — mirrors iOS syncRideToCloud.
 * Path: users/{uid}/rides/{rideId}.gpx (matches iOS's
 * `Storage.storage().reference().child("users/\(uid)/rides/\(ride.id).gpx")`
 * in RideHistoryView.swift exactly — the remote object name is the ride's
 * id, not the local GPX file's own name, since iOS's delete path
 * reconstructs this same path formulaically from the ride id).
 *
 * Aug 31, 2026 — cross-platform path parity fix: this previously wrote to
 * "rides/{uid}/{localFile.name}" (wrong root segment order, and keyed by
 * the local GPX filename rather than the ride id) — a different Storage
 * object than iOS ever reads or writes, and one iOS's own hardcoded-path
 * delete would never find either.
 *
 * onResult: Result.success(downloadUrl) or Result.failure(error)
 */
object GpxCloudUpload {
    fun upload(localFile: File, rideId: String, onResult: (Result<String>) -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            onResult(Result.failure(IllegalStateException("Not signed in")))
            return
        }
        if (!localFile.exists()) {
            onResult(Result.failure(IllegalStateException("GPX file missing")))
            return
        }
        val ref = FirebaseStorage.getInstance()
            .reference
            .child("users")
            .child(uid)
            .child("rides")
            .child("$rideId.gpx")
        ref.putFile(Uri.fromFile(localFile))
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                ref.downloadUrl
            }
            .addOnSuccessListener { uri -> onResult(Result.success(uri.toString())) }
            .addOnFailureListener { e -> onResult(Result.failure(e)) }
    }
}
