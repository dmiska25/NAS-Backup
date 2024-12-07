package com.example.nasbackup.utils

import java.io.File

fun listDirectories(directory: File?): List<File> {
    if (directory == null || !directory.isDirectory) return emptyList()
    return directory.listFiles()?.toList()?.sortedBy { it.name } ?: emptyList()
}
