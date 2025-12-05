package com.example.refrotech

import android.net.Uri

data class DocItem(
    val id: String,                  // documentation document id
    val base64: String?,
    val fileName: String?,
    val localUri: Uri? = null,
    val originCollection: String = "",   // "requests" or "schedules"
    val parentId: String = ""            // requestId or scheduleId
)
