package com.blockbuster.core.overlay.matching

import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlockRuleType
import com.blockbuster.core.overlay.BlockMatch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultBlockRuleMatcher @Inject constructor() : BlockRuleMatcher {

    override fun findMatch(
        target: String?,
        rules: List<BlockRule>,
        detectedAtEpochMillis: Long,
        packageName: String?,
        className: String?,
    ): BlockMatch? {
        val rawTarget = target?.trim().orEmpty()
        val normalizedPackage = packageName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val normalizedClassName = className?.trim()?.takeIf { it.isNotEmpty() }
        if (rawTarget.isEmpty() && normalizedPackage == null && normalizedClassName == null) {
            return null
        }

        val targetHost = normalizeHost(rawTarget)
        val lowerTarget = rawTarget.lowercase()
        val keywordHaystack = listOfNotNull(
            rawTarget.takeIf { it.isNotEmpty() },
            normalizedClassName,
        ).joinToString(" ").lowercase()

        return rules.asSequence()
            .filter { rule -> rule.isEnabled && rule.pattern.isNotBlank() }
            .sortedBy { rule -> rule.id }
            .firstNotNullOfOrNull { rule ->
                when (rule.type) {
                    BlockRuleType.DOMAIN -> {
                        if (rawTarget.isEmpty()) {
                            null
                        } else {
                            val patternHost = normalizeHost(rule.pattern)
                            if (patternHost != null &&
                                targetHost != null &&
                                (targetHost == patternHost || targetHost.endsWith(".$patternHost"))
                            ) {
                                BlockMatch(
                                    rule = rule,
                                    target = rawTarget,
                                    normalizedTarget = targetHost,
                                    detectedAtEpochMillis = detectedAtEpochMillis,
                                )
                            } else {
                                null
                            }
                        }
                    }

                    BlockRuleType.PACKAGE -> {
                        val packagePattern = rule.pattern.trim().lowercase()
                        if (normalizedPackage != null &&
                            (normalizedPackage == packagePattern ||
                                normalizedPackage.startsWith("$packagePattern."))
                        ) {
                            BlockMatch(
                                rule = rule,
                                target = normalizedPackage,
                                normalizedTarget = normalizedPackage,
                                detectedAtEpochMillis = detectedAtEpochMillis,
                            )
                        } else {
                            null
                        }
                    }

                    BlockRuleType.KEYWORD,
                    BlockRuleType.KEYWORD_SHORTS,
                    BlockRuleType.KEYWORD_REELS,
                    -> {
                        if (keywordHaystack.contains(rule.pattern.lowercase())) {
                            BlockMatch(
                                rule = rule,
                                target = rawTarget.ifEmpty {
                                    normalizedClassName ?: normalizedPackage.orEmpty()
                                },
                                normalizedTarget = keywordHaystack,
                                detectedAtEpochMillis = detectedAtEpochMillis,
                            )
                        } else {
                            null
                        }
                    }
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
        val URL_REGEX = Regex("""(?i)(https?://[^\s]+|www\.[^\s]+)""")
    }
}
