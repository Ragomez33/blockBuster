package com.blockbuster.app.ui.blocklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockbuster.app.R
import com.blockbuster.core.common.contract.BlocklistRepository
import com.blockbuster.core.common.model.BlocklistEntry
import com.blockbuster.core.common.model.BlocklistEntryDraft
import com.blockbuster.core.common.model.BlocklistTargetType
import com.blockbuster.core.common.model.BlocklistType
import com.blockbuster.core.common.result.AppError
import com.blockbuster.core.common.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BlocklistViewModel @Inject constructor(
    private val repository: BlocklistRepository,
) : ViewModel() {

    private val selectedList = MutableStateFlow(BlocklistType.BLACKLIST)
    private val selectedTarget = MutableStateFlow(BlocklistTargetType.APP)
    private val actionError = MutableStateFlow<Int?>(null)

    private val entries = combine(selectedList, selectedTarget) { list, target ->
        list to target
    }.flatMapLatest { (list, target) ->
        repository.observeEntries(listType = list, targetType = target)
    }

    val uiState: StateFlow<BlocklistUiState> = combine(
        entries,
        selectedList,
        selectedTarget,
        actionError,
    ) { currentEntries, list, target, error ->
        BlocklistUiState(
            entries = currentEntries,
            selectedListType = list,
            selectedTargetType = target,
            actionError = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BlocklistUiState(),
    )

    fun selectListType(listType: BlocklistType) {
        selectedList.value = listType
    }

    fun selectTargetType(targetType: BlocklistTargetType) {
        selectedTarget.value = targetType
    }

    fun addEntry(value: String) {
        val list = selectedList.value
        val target = selectedTarget.value
        viewModelScope.launch {
            when (val result = repository.addEntry(
                BlocklistEntryDraft(
                    value = value,
                    targetType = target,
                    listType = list,
                ),
            )) {
                is AppResult.Success -> actionError.value = null
                is AppResult.Failure -> actionError.value = result.error.toMessage()
            }
        }
    }

    fun deleteEntry(entry: BlocklistEntry) {
        viewModelScope.launch {
            when (val result = repository.deleteEntry(entry.id)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> actionError.value = result.error.toMessage()
            }
        }
    }

    fun consumeError() {
        actionError.value = null
    }

    private fun AppError.toMessage(): Int = when (this) {
        is AppError.Conflict -> R.string.blocklist_duplicate
        is AppError.NotFound -> R.string.blocklist_delete_error
        is AppError.Permission -> R.string.blocklist_save_error
        is AppError.Storage -> R.string.blocklist_save_error
        is AppError.Unexpected -> R.string.blocklist_invalid_value
    }
}

data class BlocklistUiState(
    val entries: List<BlocklistEntry> = emptyList(),
    val selectedListType: BlocklistType = BlocklistType.BLACKLIST,
    val selectedTargetType: BlocklistTargetType = BlocklistTargetType.APP,
    val actionError: Int? = null,
)
