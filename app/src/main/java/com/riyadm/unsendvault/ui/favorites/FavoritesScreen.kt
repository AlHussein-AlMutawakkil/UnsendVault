package com.riyadm.unsendvault.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.unsendvault.R
import com.riyadm.unsendvault.ui.common.rememberTimeFormatter
import com.riyadm.unsendvault.ui.components.AppScaffold
import com.riyadm.unsendvault.ui.components.EmptyState
import com.riyadm.unsendvault.ui.components.MessageRowConfig
import com.riyadm.unsendvault.ui.components.MessageRowContent
import com.riyadm.unsendvault.ui.home.ConversationRow

@Composable
fun FavoritesRoute(
    viewModel: FavoritesViewModel,
    onOpenMessage: (Long) -> Unit,
    onOpenConversation: (Long) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FavoritesScreen(
        state = state,
        onSelectTab = viewModel::selectTab,
        onOpenMessage = onOpenMessage,
        onOpenConversation = onOpenConversation,
        onRemoveFavoriteMessage = viewModel::removeFavoriteMessage,
        onRemoveFavoriteConversation = viewModel::removeFavoriteConversation,
    )
}

@Composable
fun FavoritesScreen(
    state: FavoritesUiState,
    onOpenMessage: (Long) -> Unit,
    onRemoveFavorite: (Long) -> Unit,
) {
    FavoritesScreen(
        state = state,
        onSelectTab = {},
        onOpenMessage = onOpenMessage,
        onOpenConversation = {},
        onRemoveFavoriteMessage = onRemoveFavorite,
        onRemoveFavoriteConversation = {},
    )
}

@Composable
fun FavoritesScreen(
    state: FavoritesUiState,
    onSelectTab: (FavoritesTab) -> Unit,
    onOpenMessage: (Long) -> Unit,
    onOpenConversation: (Long) -> Unit,
    onRemoveFavoriteMessage: (Long) -> Unit,
    onRemoveFavoriteConversation: (Long) -> Unit,
) {
    val formatter = rememberTimeFormatter()
    val removeLabel = stringResource(R.string.action_remove_favorite)
    AppScaffold(title = stringResource(R.string.favorites_title)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = state.selectedTab.ordinal) {
                Tab(
                    selected = state.selectedTab == FavoritesTab.CONVERSATIONS,
                    onClick = { onSelectTab(FavoritesTab.CONVERSATIONS) },
                    text = { Text("المحادثات") },
                )
                Tab(
                    selected = state.selectedTab == FavoritesTab.MESSAGES,
                    onClick = { onSelectTab(FavoritesTab.MESSAGES) },
                    text = { Text("الرسائل") },
                )
            }

            when (state.selectedTab) {
                FavoritesTab.CONVERSATIONS -> {
                    if (!state.loading && state.conversations.isEmpty()) {
                        EmptyState(
                            Icons.Outlined.StarBorder,
                            "لا توجد محادثات مفضلة",
                            "أضف محادثات إلى المفضلة من خيارات المحادثة (الثلاث نقاط) للوصول السريع إليها",
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize().testTag("favorites_conversations_list")) {
                            items(state.conversations, key = { it.id }, contentType = { "conversation" }) { conversation ->
                                ConversationRow(
                                    conversation = conversation,
                                    timeLabel = formatter.short(conversation.lastMessageAt),
                                    selected = false,
                                    onClick = { onOpenConversation(conversation.id) },
                                )
                                HorizontalDivider(Modifier.padding(start = 72.dp))
                            }
                        }
                    }
                }
                FavoritesTab.MESSAGES -> {
                    if (!state.loading && state.messages.isEmpty()) {
                        EmptyState(
                            Icons.Outlined.StarBorder,
                            stringResource(R.string.favorites_empty_title),
                            stringResource(R.string.favorites_empty_body),
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize().testTag("favorites_list")) {
                            items(state.messages, key = { it.id }, contentType = { "message" }) { message ->
                                MessageRowContent(
                                    message = message,
                                    timeLabel = formatter.short(message.messageTimestamp),
                                    config = MessageRowConfig(),
                                    modifier = Modifier
                                        .clickable(role = Role.Button) { onOpenMessage(message.id) }
                                        .semantics {
                                            customActions = listOf(CustomAccessibilityAction(removeLabel) { onRemoveFavoriteMessage(message.id); true })
                                        },
                                )
                                HorizontalDivider(Modifier.padding(start = 72.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
