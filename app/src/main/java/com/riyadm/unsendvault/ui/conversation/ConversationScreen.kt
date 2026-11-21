package com.riyadm.unsendvault.ui.conversation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.unsendvault.R
import com.riyadm.unsendvault.appContainer
import com.riyadm.unsendvault.apps.AppDirectory
import com.riyadm.unsendvault.core.model.ArchivedMessage
import com.riyadm.unsendvault.core.model.ContentKind
import com.riyadm.unsendvault.core.model.MessageStatus
import com.riyadm.unsendvault.data.avatar.AvatarStorage
import com.riyadm.unsendvault.reply.ReplyAvailability
import com.riyadm.unsendvault.reply.ReplyResult
import com.riyadm.unsendvault.ui.common.LocalScreenInsets
import com.riyadm.unsendvault.ui.common.TimeFormatter
import com.riyadm.unsendvault.ui.common.rememberTimeFormatter
import com.riyadm.unsendvault.ui.components.AppScaffold
import com.riyadm.unsendvault.ui.components.BackButton
import com.riyadm.unsendvault.ui.components.ConfirmDialog
import com.riyadm.unsendvault.ui.components.EmptyState
import com.riyadm.unsendvault.ui.components.MessageBadges
import com.riyadm.unsendvault.ui.components.messageBodyText
import java.io.File

object ConversationTestTags {
    const val LIST = "conversation_list"
    const val DELETE_SELECTED = "conversation_delete_selected"
    const val REPLY_FIELD = "conversation_reply_field"
    const val REPLY_SEND = "conversation_reply_send"
    const val REPLY_UNAVAILABLE = "conversation_reply_unavailable"
    fun message(id: Long) = "conversation_message_$id"
}

@Composable
fun ConversationRoute(
    viewModel: ConversationViewModel,
    onBack: (() -> Unit)?,
    onClosed: () -> Unit,
    onOpenMessage: (Long) -> Unit,
    /** Called with true while the chat is resumed on screen, false when it leaves (saved-message notifications). */
    onVisibleChanged: (Boolean) -> Unit = {},
    /** Opens the source app (in whichever profile has it) from the reply bar. Null hides that button. */
    appDirectory: AppDirectory? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var replyDraft by rememberSaveable { mutableStateOf("") }
    LifecycleResumeEffect(viewModel) {
        onVisibleChanged(true)
        viewModel.refreshReply()
        onPauseOrDispose { onVisibleChanged(false) }
    }
    val appLabel = state.conversation?.appLabel.orEmpty()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ConversationEvent.ConversationDeleted -> onClosed()
                is ConversationEvent.MessagesDeleted -> snackbar.showSnackbar(
                    resources.getQuantityString(R.plurals.messages_deleted, event.count, event.count),
                )
                is ConversationEvent.Reply -> when (event.result) {
                    ReplyResult.SENT -> {
                        replyDraft = ""
                        snackbar.showSnackbar(resources.getString(R.string.reply_sent, appLabel))
                    }
                    ReplyResult.UNAVAILABLE -> snackbar.showSnackbar(resources.getString(R.string.reply_gone, appLabel))
                    ReplyResult.FAILED -> snackbar.showSnackbar(resources.getString(R.string.reply_failed, appLabel))
                    ReplyResult.EMPTY -> Unit
                }
            }
        }
    }
    val packageName = state.conversation?.packageName
    val context = LocalContext.current
    val actualAppDir = appDirectory ?: remember(context) { try { context.appContainer.appDirectory } catch (_: Exception) { null } }
    val actualAvatarStorage = remember(context) { try { context.appContainer.avatarStorage } catch (_: Exception) { null } }

    val avatarFile by produceState<File?>(null, state.conversation, actualAvatarStorage) {
        val conv = state.conversation
        value = if (conv != null && actualAvatarStorage != null) {
            withContext(Dispatchers.IO) {
                actualAvatarStorage.avatarFile(conv.packageName, conv.conversationKey)
            }
        } else null
    }

    val appIcon by produceState<Drawable?>(null, state.conversation, actualAppDir) {
        val pkg = state.conversation?.packageName
        value = if (pkg != null && actualAppDir != null) {
            withContext(Dispatchers.IO) {
                actualAppDir.icon(pkg)
            }
        } else null
    }

    // The chat app may live only in a work or other profile; AppDirectory finds and opens it there too.
    val canOpenApp by produceState(false, packageName, actualAppDir) {
        value = packageName != null && actualAppDir != null && withContext(Dispatchers.IO) { actualAppDir.canLaunch(packageName) }
    }
    ConversationScreen(
        replyDraft = replyDraft,
        onReplyDraftChange = { replyDraft = it },
        onSendReply = { viewModel.sendReply(replyDraft) },
        onOpenSourceApp = if (canOpenApp && packageName != null && actualAppDir != null) {
            { actualAppDir.launch(packageName) }
        } else {
            null
        },
        state = state,
        snackbarHostState = snackbar,
        onBack = onBack,
        onOpenMessage = onOpenMessage,
        onToggleSelection = viewModel::toggleSelection,
        onSelectAll = viewModel::selectAll,
        onClearSelection = viewModel::clearSelection,
        onDeleteSelected = viewModel::deleteSelected,
        onDeleteConversation = viewModel::deleteConversation,
        avatarFile = avatarFile,
        appIcon = appIcon,
    )
}

@Composable
fun ConversationScreen(
    state: ConversationUiState,
    onBack: (() -> Unit)?,
    onOpenMessage: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDeleteConversation: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    replyDraft: String = "",
    onReplyDraftChange: (String) -> Unit = {},
    onSendReply: () -> Unit = {},
    onOpenSourceApp: (() -> Unit)? = null,
    avatarFile: File? = null,
    appIcon: Drawable? = null,
) {
    val formatter = rememberTimeFormatter()
    var confirmDeleteSelected by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteConversation by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val insets = LocalScreenInsets.current
    BackHandler(enabled = state.selecting, onBack = onClearSelection)

    val selfSender = stringResource(R.string.capture_self_sender)
    val contactSender = state.messages.firstOrNull {
        val s = it.sender
        s != null && s != selfSender && s != "You" && s != "أنت"
    }?.sender

    val rawTitle = state.conversation?.title.orEmpty()
    val title = if (rawTitle.isEmpty() || rawTitle == selfSender || rawTitle == "You" || rawTitle == "أنت") {
        contactSender ?: rawTitle.ifEmpty { stringResource(R.string.app_name) }
    } else {
        rawTitle
    }

    // Only recognize real phone numbers (must start with '+' or '00', or explicit international phone pattern)
    val phoneRegex = Regex("""(?:\+|00)\d{8,15}""")
    val phoneFromKey = state.conversation?.conversationKey?.let { key -> phoneRegex.find(key)?.value }
    val subtitle = if (phoneFromKey != null && phoneFromKey != title) {
        phoneFromKey
    } else {
        state.conversation?.appLabel.orEmpty()
    }

    AppScaffold(
        title = title,
        snackbarHostState = snackbarHostState,
        scrollBehavior = null,
        topBar = {
            if (state.selecting) {
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.selected_count, state.selection.size, state.selection.size)) },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                        }
                        IconButton(onClick = { confirmDeleteSelected = true }, modifier = Modifier.testTag(ConversationTestTags.DELETE_SELECTED)) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.action_delete_selected))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    windowInsets = insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ContactAvatarWithBadge(
                                avatarFile = avatarFile,
                                name = title,
                                appIcon = appIcon,
                                modifier = Modifier.size(42.dp),
                            )
                            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = { if (onBack != null) BackButton(onBack) },
                    actions = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete_conversation)) },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        confirmDeleteConversation = true
                                    },
                                )
                            }
                        }
                    },
                    windowInsets = insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
            }
        },
    ) { padding ->
      Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
        if (!state.loading && state.messages.isEmpty()) {
            EmptyState(
                Icons.Outlined.Forum,
                stringResource(R.string.conversation_empty_title),
                stringResource(R.string.conversation_empty_body),
                Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(ConversationTestTags.LIST),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(state.timeline, key = { it.key }, contentType = { it::class }) { item ->
                    when (item) {
                        is TimelineItem.DayHeader -> DayHeader(formatter.dayHeader(item.millis))
                        is TimelineItem.Message -> MessageBubble(
                            message = item.message,
                            showSender = state.conversation?.isGroup == true || item.message.sender != state.conversation?.title,
                            selected = item.message.id in state.selection,
                            formatter = formatter,
                            selfSender = selfSender,
                            onClick = {
                                if (state.selecting) onToggleSelection(item.message.id) else onOpenMessage(item.message.id)
                            },
                            onLongClick = { onToggleSelection(item.message.id) },
                        )
                    }
                }
            }
        }
        val conversation = state.conversation
        if (!state.selecting && conversation != null && state.reply != null) {
            ReplyBar(
                availability = state.reply,
                appLabel = conversation.appLabel,
                draft = replyDraft,
                sending = state.sendingReply,
                onDraftChange = onReplyDraftChange,
                onSend = onSendReply,
                onOpenSourceApp = onOpenSourceApp,
            )
        }
      }
    }

    if (confirmDeleteSelected) {
        val count = state.selection.size
        ConfirmDialog(
            title = pluralStringResource(R.plurals.delete_messages_title, count, count),
            body = stringResource(R.string.delete_messages_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDeleteSelected = false
                onDeleteSelected()
            },
            onDismiss = { confirmDeleteSelected = false },
        )
    }
    if (confirmDeleteConversation) {
        ConfirmDialog(
            title = stringResource(R.string.delete_conversation_title),
            body = stringResource(R.string.delete_conversation_body, title),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDeleteConversation = false
                onDeleteConversation()
            },
            onDismiss = { confirmDeleteConversation = false },
        )
    }
}

@Composable
private fun DayHeader(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp).semantics { heading() },
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ArchivedMessage,
    showSender: Boolean,
    selected: Boolean,
    formatter: TimeFormatter,
    selfSender: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isOutgoing = message.sender == selfSender || message.sender == "You" || message.sender == "أنت"
    val colors = MaterialTheme.colorScheme
    val selectLabel = stringResource(R.string.action_select)

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)
    }

    val bubbleColor = when {
        selected -> colors.secondaryContainer
        isOutgoing -> colors.primaryContainer
        else -> colors.surfaceContainerHigh
    }
    val contentColor = when {
        selected -> colors.onSecondaryContainer
        isOutgoing -> colors.onPrimaryContainer
        else -> colors.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ConversationTestTags.message(message.id)),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected && !isOutgoing) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.padding(end = 8.dp).size(22.dp),
            )
        }
        Surface(
            color = bubbleColor,
            contentColor = contentColor,
            shape = bubbleShape,
            modifier = Modifier
                .widthIn(min = 60.dp, max = 320.dp)
                .semantics { this.selected = selected }
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = selectLabel,
                    onLongClick = onLongClick,
                    onClick = onClick,
                ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val sender = message.sender
                if (!isOutgoing && showSender && sender != null) {
                    Text(
                        text = sender,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )
                }
                Text(
                    text = messageBodyText(message),
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = if (message.contentKind == ContentKind.TEXT) FontStyle.Normal else FontStyle.Italic,
                )
                MessageBadges(message)
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (message.isFavorite) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = stringResource(R.string.cd_favorite),
                            tint = colors.tertiary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = formatter.time(message.messageTimestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOutgoing) colors.onPrimaryContainer.copy(alpha = 0.75f) else colors.onSurfaceVariant,
                    )
                    if (isOutgoing) {
                        Icon(
                            Icons.Filled.Done,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        if (selected && isOutgoing) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.padding(start = 8.dp).size(22.dp),
            )
        }
    }
}

@Composable
private fun ContactAvatarWithBadge(
    avatarFile: File?,
    name: String,
    appIcon: Drawable?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (avatarFile != null && avatarFile.exists()) {
            val bitmap = remember(avatarFile) {
                try {
                    BitmapFactory.decodeFile(avatarFile.absolutePath)?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                InitialsAvatar(name = name, modifier = Modifier.fillMaxSize())
            }
        } else {
            InitialsAvatar(name = name, modifier = Modifier.fillMaxSize())
        }

        if (appIcon != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .size(17.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp),
                shadowElevation = 2.dp,
            ) {
                DrawableImage(
                    drawable = appIcon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(1.dp),
                )
            }
        }
    }
}

@Composable
private fun InitialsAvatar(name: String, modifier: Modifier = Modifier) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val avatarColors = remember {
        listOf(
            Color(0xFF7E57C2), // Purple (matching WimLow)
            Color(0xFF1E88E5), // Blue
            Color(0xFF00897B), // Teal
            Color(0xFFE53935), // Red
            Color(0xFFFB8C00), // Orange
            Color(0xFF00ACC1), // Cyan
            Color(0xFF5E35B1), // Deep purple
            Color(0xFF3949AB), // Indigo
        )
    }
    val colorIndex = remember(name) {
        val hash = name.hashCode()
        (hash and 0x7FFFFFFF) % avatarColors.size
    }
    Surface(
        shape = CircleShape,
        color = avatarColors[colorIndex],
        contentColor = Color.White,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DrawableImage(
    drawable: Drawable,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(drawable) {
        try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap.asImageBitmap()
            } else {
                val width = drawable.intrinsicWidth.coerceAtLeast(1)
                val height = drawable.intrinsicHeight.coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bmp.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun ReplyBar(
    availability: ReplyAvailability,
    appLabel: String,
    draft: String,
    sending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenSourceApp: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider()
        when (availability) {
            ReplyAvailability.AVAILABLE -> Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= MAX_REPLY_LENGTH) onDraftChange(it) },
                        placeholder = { Text(stringResource(R.string.reply_hint, appLabel)) },
                        maxLines = 4,
                        enabled = !sending,
                        modifier = Modifier.weight(1f).testTag(ConversationTestTags.REPLY_FIELD),
                    )
                    IconButton(
                        onClick = onSend,
                        enabled = draft.isNotBlank() && !sending,
                        modifier = Modifier.testTag(ConversationTestTags.REPLY_SEND),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.reply_send))
                    }
                }
                Text(
                    stringResource(R.string.reply_caption, appLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            ReplyAvailability.NO_NOTIFICATION, ReplyAvailability.LISTENER_OFF -> Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp).testTag(ConversationTestTags.REPLY_UNAVAILABLE),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (availability == ReplyAvailability.LISTENER_OFF) {
                        stringResource(R.string.reply_listener_off)
                    } else {
                        stringResource(R.string.reply_unavailable, appLabel)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (onOpenSourceApp != null) {
                    TextButton(onClick = onOpenSourceApp) { Text(stringResource(R.string.reply_open_app, appLabel)) }
                }
            }
        }
    }
}

private const val MAX_REPLY_LENGTH = 4_000
