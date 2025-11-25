package com.example.refrotech

import android.net.Uri

data class DocItem(
    val id: String,
    val base64: String?,
    val fileName: String?,
    val localUri: Uri? = null
)
