package com.riyadm.unsendvault.ui.favorites

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.unsendvault.core.model.ArchivedMessage
import com.riyadm.unsendvault.core.model.Conversation
import com.riyadm.unsendvault.core.model.VaultSettings
import com.riyadm.unsendvault.data.SettingsRepository
import com.riyadm.unsendvault.data.VaultRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FavoritesTab { CONVERSATIONS, MESSAGES }

@Immutable
data class FavoritesUiState(
    val loading: Boolean = true,
    val selectedTab: FavoritesTab = FavoritesTab.CONVERSATIONS,
    val conversations: List<Conversation> = emptyList(),
    val messages: List<ArchivedMessage> = emptyList(),
)

class FavoritesViewModel(
    private val vault: VaultRepository,
    private val settings: SettingsRepository? = null,
) : ViewModel() {

    private val selectedTab = MutableStateFlow(FavoritesTab.CONVERSATIONS)
    private val favoriteMessages = vault.observeFavorites(LIMIT)
    private val allConversations = vault.observeConversations()
    private val settingsFlow = settings?.settings ?: flowOf(VaultSettings())

    val state: StateFlow<FavoritesUiState> = combine(
        selectedTab,
        favoriteMessages,
        allConversations,
        settingsFlow,
    ) { tab, msgs, convs, s ->
        val favoriteConvs = convs.filter { it.id in s.favoriteConversationIds }
        FavoritesUiState(
            loading = false,
            selectedTab = tab,
            conversations = favoriteConvs,
            messages = msgs,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState())

    fun selectTab(tab: FavoritesTab) {
        selectedTab.value = tab
    }

    fun removeFavoriteMessage(id: Long) {
        viewModelScope.launch { vault.setFavorite(id, false) }
    }

    fun removeFavorite(id: Long) = removeFavoriteMessage(id)

    fun removeFavoriteConversation(conversationId: Long) {
        viewModelScope.launch {
            settings?.update { s ->
                s.copy(favoriteConversationIds = s.favoriteConversationIds - conversationId)
            }
        }
    }

    companion object {
        const val LIMIT = 1_000
    }
}
