package com.blockbuster.core.database.preset

import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.common.model.ProtectionPreset

/**
 * One internal rule owned by a master preset.
 */
data class PresetRuleDefinition(
    val pattern: String,
    val type: BlockRuleType,
)

/**
 * Static catalog of the ready-to-use master protection profiles.
 *
 * Preset rules are not stored in Room. They are synthesized as immutable
 * domain rules whenever the preset is enabled, which keeps the catalog
 * versionable and avoids duplicating hundreds of rows per installation.
 */
object ProtectionPresetCatalog {

    private const val PRESET_ID_STRIDE = 10_000L

    fun rulesFor(preset: ProtectionPreset): List<PresetRuleDefinition> = when (preset) {
        ProtectionPreset.ADULT_CONTENT -> adultContent
        ProtectionPreset.SHORT_VIDEOS -> shortVideos
        ProtectionPreset.VISUAL_SEARCH -> visualSearch
    }

    fun ruleCount(preset: ProtectionPreset): Int = rulesFor(preset).size

    /**
     * Builds the active rules for the enabled presets. IDs are stable and
     * negative so they never collide with Room-generated identifiers.
     */
    fun activeRules(enabled: Set<ProtectionPreset>): List<BlockRule> =
        ProtectionPreset.entries
            .filter { preset -> preset in enabled }
            .flatMap { preset ->
                rulesFor(preset).mapIndexed { index, definition ->
                    BlockRule(
                        id = syntheticId(preset, index),
                        pattern = definition.pattern,
                        type = definition.type,
                        isEnabled = true,
                        createdAt = 0L,
                    )
                }
            }

    fun syntheticId(preset: ProtectionPreset, index: Int): Long =
        -((preset.ordinal + 1) * PRESET_ID_STRIDE + index)

    private val adultContent: List<PresetRuleDefinition> =
        domains(
            "pornhub.com",
            "xvideos.com",
            "xnxx.com",
            "xhamster.com",
            "redtube.com",
            "youporn.com",
            "onlyfans.com",
            "chaturbate.com",
            "stripchat.com",
            "livejasmin.com",
            "brazzers.com",
            "spankbang.com",
            "youjizz.com",
            "beeg.com",
            "adultfriendfinder.com",
        ) + keywords(
            "porn",
            "xxx",
            "nsfw",
            "explicit content",
            "adult content",
            "hentai",
            "camgirl",
            "escort",
        )

    private val shortVideos: List<PresetRuleDefinition> =
        packages(
            // TikTok global and regional package signatures.
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
        ) + domains(
            "tiktok.com",
        ) + keywords(
            "reels",
            "shorts",
            "instagram.com/reels",
            "youtube.com/shorts",
            "facebook.com/reel",
            "tiktok",
        )

    private val visualSearch: List<PresetRuleDefinition> =
        domains(
            "images.google.com",
            "lens.google.com",
            "tineye.com",
        ) + keywords(
            "google.com/imghp",
            "google.com/search?tbm=isch",
            "images.google.",
            "lens.google.com",
            "bing.com/images",
            "yandex.com/images",
            "pinterest.com/search",
            "tineye.com",
            "visual search",
        )

    private fun domains(vararg patterns: String): List<PresetRuleDefinition> =
        patterns.map { pattern -> PresetRuleDefinition(pattern, BlockRuleType.DOMAIN) }

    private fun packages(vararg patterns: String): List<PresetRuleDefinition> =
        patterns.map { pattern -> PresetRuleDefinition(pattern, BlockRuleType.PACKAGE) }

    private fun keywords(vararg patterns: String): List<PresetRuleDefinition> =
        patterns.map { pattern -> PresetRuleDefinition(pattern, BlockRuleType.KEYWORD) }
}
