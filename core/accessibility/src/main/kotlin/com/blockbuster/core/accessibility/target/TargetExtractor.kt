package com.blockbuster.core.accessibility.target

import android.view.accessibility.AccessibilityEvent

/**
 * Best-effort extraction of the current URL or text from an accessibility
 * event. Implementations must not perform expensive work on the main thread.
 */
interface TargetExtractor {
    fun extract(event: AccessibilityEvent): String?
}
