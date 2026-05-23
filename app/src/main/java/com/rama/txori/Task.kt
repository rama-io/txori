package com.rama.txori

data class Task(
    val id: Long = 0,
    val stepId: Long = 0,
    var label: String,
    var duration: String = "000000",
    var restDuration: String = "000000"
)
