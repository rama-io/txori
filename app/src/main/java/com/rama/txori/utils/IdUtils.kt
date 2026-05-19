package com.rama.txori.utils

object IdUtils {
    fun toBase36Fixed(value: Long, length: Int = 11): String {
        return value.toString(36)
            .uppercase()
            .padStart(length, '0')
    }
}