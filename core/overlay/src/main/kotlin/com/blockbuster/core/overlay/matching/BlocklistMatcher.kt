package com.blockbuster.core.overlay.matching

import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.common.model.BlocklistEntry
import com.blockbuster.core.common.model.BlocklistTargetType
import com.blockbuster.core.common.model.BlocklistType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Evaluates user-defined blacklists and whitelists against an accessibility
 * detection. Whitelist precedence is handled by the caller before any rule
 * evaluation.
 */
@Singleton
class BlocklistMatcher @Inject constructor() {

    fun isWhitelisted(
        packageName: String?,
        target: String?,
        className: String?,
        entries: List<BlocklistEntry>,
    ): Boolean = entries
        .asSequence()
        .filter { entry -> entry.listType == BlocklistType.WHITELIST }
        .any { entry -> matches(entry, packageName, target, className) }

    fun blacklistRules(entries: List<BlocklistEntry>): List<BlockRule> = entries
        .asSequence()
        .filter { entry -> entry.listType == BlocklistType.BLACKLIST }
        .map { entry ->
            BlockRule(
                id = SYNTHETIC_ID_BASE - entry.id,
                pattern = entry.value,
                type = when (entry.targetType) {
                    BlocklistTargetType.APP -> BlockRuleType.PACKAGE
                    BlocklistTargetType.WEBSITE -> BlockRuleType.DOMAIN
                    BlocklistTargetType.KEYWORD -> BlockRuleType.KEYWORD
                },
                isEnabled = true,
                createdAt = entry.createdAt,
            )
        }
        .toList()

    private fun matches(
        entry: BlocklistEntry,
        packageName: String?,
        target: String?,
        className: String?,
    ): Boolean {
        val normalizedPackage = packageName?.trim()?.lowercase()
        val rawTarget = target?.trim().orEmpty()
        val lowerTarget = rawTarget.lowercase()
        val lowerClass = className?.trim()?.lowercase().orEmpty()

        return when (entry.targetType) {
            BlocklistTargetType.APP ->
                normalizedPackage != null &&
                    normalizedPackage == entry.value.trim().lowercase()

            BlocklistTargetType.WEBSITE -> {
                val entryHost = normalizeHost(entry.value) ?: return false
                val targetHost = normalizeHost(rawTarget) ?: return false
                targetHost == entryHost || targetHost.endsWith(".$entryHost")
            }

            BlocklistTargetType.KEYWORD -> {
                val keyword = entry.value.trim().lowercase()
                keyword.isNotEmpty() &&
                    (lowerTarget.contains(keyword) || lowerClass.contains(keyword))
            }
        }
    }

    private fun normalizeHost(value: String): String? {
        val candidate = URL_REGEX.find(value)?.value ?: value
        val withoutScheme = candidate.substringAfter("://", candidate)
        val host = withoutScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .trim()
            .lowercase()
            .removePrefix("www.")
            .trimEnd('.')
        return host.takeIf { it.isNotBlank() && !it.contains(' ') }
    }

    private companion object {
        const val SYNTHETIC_ID_BASE = -1_000_000L
        val URL_REGEX = Regex("""(?i)(https?://[^\s]+|www\.[^\s]+)""")
    }
}
