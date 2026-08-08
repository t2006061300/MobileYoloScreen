package com.example.mobileyoloscreen

data class Detection(
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    val score: Float, val classId: Int, val label: String
)
