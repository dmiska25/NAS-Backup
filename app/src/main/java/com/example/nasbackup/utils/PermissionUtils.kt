package com.example.nasbackup.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat

/**
 * Arbitrary request code for notification permission.
 * Must match whatever logic you handle in onRequestPermissionsResult.
 */
const val REQUEST_CODE_NOTIFICATIONS = 1002

/**
 * Checks whether the app already has POST_NOTIFICATIONS permission (Android 13+).
 * If running on Android 12 or lower, this always returns true (no permission needed).
 */
fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        true // No notification permission required before Android 13
    } else {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Requests the POST_NOTIFICATIONS permission if on Android 13+.
 * If below Android 13, immediately calls onResult(true).
 * If you need to handle the final acceptance/denial, do so in the Activity’s
 * onRequestPermissionsResult with REQUEST_CODE_NOTIFICATIONS.
 */
fun requestNotificationPermission(
    context: Context,
    onResult: ((granted: Boolean) -> Unit)? = null
): Boolean {
    println("Checking notification permission...")
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // No permission needed
        onResult?.let { onResult(true) }
        return true
    } else {
        println("Requesting notification permission...")
        val activity = context as? ComponentActivity
        if (activity != null) {
            println("Requesting notification permission from activity...")
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_NOTIFICATIONS
            )

            val granted = checkNotificationPermission(context)
            println("Notification permission granted: $granted")
            onResult?.let { onResult(granted) }
            return granted
        } else {
            // We can’t request permissions without an Activity
            onResult?.let { onResult(false) }
            return false
        }
    }
}

fun requestStoragePermission(context: Context, onResult: (Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent =
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            context.startActivity(intent)
            // Simulate success since the user needs to grant this manually.
            onResult(true)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Unable to open settings. Please grant permission manually.",
                Toast.LENGTH_LONG
            ).show()
            onResult(false)
        }
    } else {
        val activity = context as? ComponentActivity
        activity?.let {
            ActivityCompat.requestPermissions(
                it,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                1001
            )
            onResult(true) // Assume success if permissions are requested
        } ?: run {
            onResult(false)
        }
    }
}

fun checkStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        val readPermission = context.checkSelfPermission(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        val writePermission = context.checkSelfPermission(
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        readPermission == PackageManager.PERMISSION_GRANTED &&
            writePermission == PackageManager.PERMISSION_GRANTED
    }
}
