package com.blockbuster.core.accessibility.target

import android.view.accessibility.AccessibilityEvent
import javax.inject.Inject

/**
 * Extracts an URL-like candidate first and falls back to the first non-empty
 * event text. This is intentionally conservative: browser address bars are not
 * consistently exposed through accessibility, so failure to find a target must
 * result in no match rather than a false positive.
 */
class DefaultTargetExtractor @Inject constructor() : TargetExtractor {

    override fun extract(event: AccessibilityEvent): String? {
        val candidates = buildList {
            event.text.forEach { text ->
                text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            }
            event.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            runCatching { event.source?.text?.toString()?.trim() }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let(::add)
        }

        if (candidates.isEmpty()) return null

        val url = candidates.firstNotNullOfOrNull { candidate ->
            URL_REGEX.find(candidate)?.value
        }
        return (url ?: candidates.first()).take(MAX_TARGET_LENGTH)
    }

    private companion object {
        const val MAX_TARGET_LENGTH = 2_048
        val URL_REGEX = Regex("""(?i)(https?://[^\s]+|www\.[^\s]+)""")
    }
}
