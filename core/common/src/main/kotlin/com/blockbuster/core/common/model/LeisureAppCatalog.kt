package com.blockbuster.core.common.model

/**
 * Curated leisure packages used by Focus Mode. Package-level matching is the
 * only reliable signal across OEM variants.
 */
object LeisureAppCatalog {
    val packages: Set<String> = setOf(
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.instagram.android",
        "com.google.android.youtube",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.twitter.android",
        "com.x.android",
        "com.netflix.mediaclient",
        "tv.twitch.android.app",
        "com.spotify.music",
        "com.pinterest",
    )

    fun isLeisure(packageName: String?): Boolean =
        packageName?.trim()?.lowercase() in packages
}
