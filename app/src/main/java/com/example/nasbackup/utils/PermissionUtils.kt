package com.example.nasbackup.utils

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
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
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
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
        val writePermission = context.checkSelfPermission(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        readPermission == PackageManager.PERMISSION_GRANTED &&
            writePermission == PackageManager.PERMISSION_GRANTED
    }
}
