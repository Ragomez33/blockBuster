package com.blockbuster.core.overlay

/**
 * Detects paste-like insertions by comparing the old and new text. A single
 * change that inserts more than three characters is treated as a paste.
 */
object FrictionInput {
    fun insertedCharacterCount(oldText: String, newText: String): Int {
        val maxPrefix = minOf(oldText.length, newText.length)
        var prefix = 0
        while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) {
            prefix += 1
        }

        val maxSuffix = minOf(oldText.length - prefix, newText.length - prefix)
        var suffix = 0
        while (suffix < maxSuffix &&
            oldText[oldText.lastIndex - suffix] == newText[newText.lastIndex - suffix]
        ) {
            suffix += 1
        }

        return (newText.length - prefix - suffix).coerceAtLeast(0)
    }

    fun isPaste(oldText: String, newText: String): Boolean =
        insertedCharacterCount(oldText, newText) > MAX_MANUAL_INSERTION

    const val MAX_MANUAL_INSERTION = 3
}
