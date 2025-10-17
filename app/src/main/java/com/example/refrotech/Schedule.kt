package com.example.refrotech

data class Schedule(
    val customerName: String,
    val time: String,
    val technician: String,
    val scheduleId: String = ""
)
