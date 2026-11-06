package com.blockbuster.app.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockbuster.core.common.contract.BlockRulesRepository
import com.blockbuster.core.common.model.BlockRule
import com.blockbuster.core.common.model.BlockRuleDraft
import com.blockbuster.core.common.model.BlockRuleType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repository: BlockRulesRepository,
) : ViewModel() {

    val uiState: StateFlow<RulesUiState> = repository.observeAllRules()
        .map { rules -> RulesUiState(rules = rules, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RulesUiState(isLoading = true),
        )

    fun addRule(pattern: String, type: BlockRuleType) {
        val normalizedPattern = pattern.trim()
        if (normalizedPattern.isEmpty()) return

        viewModelScope.launch {
            repository.insertRule(
                BlockRuleDraft(
                    pattern = normalizedPattern,
                    type = type,
                ),
            )
        }
    }

    fun setRuleEnabled(rule: BlockRule, enabled: Boolean) {
        viewModelScope.launch {
            repository.updateRule(rule.copy(isEnabled = enabled))
        }
    }

    fun deleteRule(rule: BlockRule) {
        viewModelScope.launch {
            repository.deleteRule(rule.id)
        }
    }
}

data class RulesUiState(
    val rules: List<BlockRule> = emptyList(),
    val isLoading: Boolean = false,
)
