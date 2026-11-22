package com.riyadm.unsendvault.ui.conversation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riyadm.unsendvault.R
import com.riyadm.unsendvault.appContainer
import com.riyadm.unsendvault.apps.AppDirectory
import com.riyadm.unsendvault.core.model.ContentKind
import com.riyadm.unsendvault.core.model.EncryptionMode
import com.riyadm.unsendvault.crypto.message.SteganographyEngine
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val context = LocalContext.current

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
                is ConversationEvent.LaunchMessengerFallback -> {
                    try {
                        val clip = ClipData.newPlainText("Encrypted Message", event.text)
                        val cm = context.getSystemService<ClipboardManager>()
                        cm?.setPrimaryClip(clip)
                    } catch (_: Exception) {}

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, event.text)
                        setPackage(event.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val launched = try {
                        context.startActivity(intent)
                        true
                    } catch (_: Exception) {
                        try {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(event.packageName)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                                true
                            } else false
                        } catch (_: Exception) { false }
                    }
                    if (launched) {
                        replyDraft = ""
                        snackbar.showSnackbar("تم نسخ الرسالة وفتح $appLabel لإرسالها مباشرة")
                    } else {
                        snackbar.showSnackbar("تم نسخ الرسالة المشفرة، تعذر فتح التطبيق تلقائياً")
                    }
                }
                is ConversationEvent.KeyMissingWarning -> {
                    val modeName = if (event.mode == EncryptionMode.STEGANO) "المموّه" else "الصريح"
                    snackbar.showSnackbar("يرجى تعيين مفتاح تشفير (خاص أو عام) أولاً لاستخدام التشفير $modeName")
                }
            }
        }
    }
    val packageName = state.conversation?.packageName
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
        onSetEncryptionMode = viewModel::setEncryptionMode,
        onSetConversationEncryptionMode = viewModel::setConversationEncryptionMode,
        onCustomCoverTextChange = viewModel::setCustomCoverText,
        onSetConversationKey = viewModel::setConversationKey,
        onGenerateStrongKey = viewModel::generateStrongKey,
        onToggleConversationFavorite = viewModel::toggleConversationFavorite,
        onCycleEncryptionMode = viewModel::cycleEncryptionMode,
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
    onSetEncryptionMode: (EncryptionMode) -> Unit = {},
    onSetConversationEncryptionMode: (EncryptionMode?) -> Unit = {},
    onCustomCoverTextChange: (String) -> Unit = {},
    onSetConversationKey: (String?) -> Unit = {},
    onGenerateStrongKey: () -> String = { "" },
    onToggleConversationFavorite: () -> Unit = {},
    onCycleEncryptionMode: () -> Unit = {},
) {
    val formatter = rememberTimeFormatter()
    var confirmDeleteSelected by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteConversation by rememberSaveable { mutableStateOf(false) }
    var showKeyDialog by rememberSaveable { mutableStateOf(false) }
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
                                    text = { Text(if (state.isConversationFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة") },
                                    leadingIcon = {
                                        Icon(
                                            if (state.isConversationFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                            contentDescription = null,
                                            tint = if (state.isConversationFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onToggleConversationFavorite()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("إعداد التشفير") },
                                    leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        showKeyDialog = true
                                    },
                                )
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
                                item = item,
                                showSender = state.conversation?.isGroup == true || item.message.sender != state.conversation?.title,
                                selected = item.message.id in state.selection,
                                formatter = formatter,
                                selfSender = selfSender,
                                onClick = {
                                    if (state.selecting) onToggleSelection(item.message.id) else onOpenMessage(item.message.id)
                                },
                                onLongClick = { onToggleSelection(item.message.id) },
                                onOpenKeyDialog = { showKeyDialog = true },
                            )
                        }
                    }
                }
            }
            val conversation = state.conversation
            if (!state.selecting && conversation != null) {
                WimLowReplyBar(
                    appLabel = conversation.appLabel,
                    draft = replyDraft,
                    sending = state.sendingReply,
                    onDraftChange = onReplyDraftChange,
                    onSend = onSendReply,
                    encryptionMode = state.encryptionMode,
                    conversationEncryptionMode = state.conversationEncryptionMode,
                    onSetEncryptionMode = onSetEncryptionMode,
                    onSetConversationEncryptionMode = onSetConversationEncryptionMode,
                    hasPrivateKey = state.hasPrivateKey,
                    isKeyConfigured = state.isKeyConfigured,
                    customCoverText = state.customCoverText,
                    onCustomCoverTextChange = onCustomCoverTextChange,
                    onOpenKeyDialog = { showKeyDialog = true },
                    onOpenSourceApp = onOpenSourceApp,
                    appIcon = appIcon,
                    onCycleEncryptionMode = onCycleEncryptionMode,
                )
            }
        }
    }

    if (showKeyDialog) {
        ConversationKeyDialog(
            conversationTitle = title,
            privateKey = state.conversationKey,
            globalKey = state.globalKey,
            currentMode = state.encryptionMode,
            savedMode = state.conversationEncryptionMode,
            onSave = { key, mode ->
                onSetConversationKey(key)
                onSetConversationEncryptionMode(mode)
            },
            onGenerateKey = onGenerateStrongKey,
            onDismiss = { showKeyDialog = false },
        )
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
private fun ConversationKeyDialog(
    conversationTitle: String,
    privateKey: String?,
    globalKey: String,
    currentMode: EncryptionMode,
    savedMode: EncryptionMode?,
    onSave: (String?, EncryptionMode?) -> Unit,
    onGenerateKey: () -> String,
    onDismiss: () -> Unit,
) {
    var keyText by remember(privateKey) { mutableStateOf(privateKey.orEmpty()) }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedMode by remember(savedMode) { mutableStateOf(savedMode) }
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات التشفير لـ $conversationTitle") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Key source status card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "المفتاح الفعّال حالياً:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = when {
                                !privateKey.isNullOrBlank() -> "مفتاح خاص بهذه المحادثة (له الأولوية)"
                                globalKey.isNotBlank() -> "المفتاح العام للتطبيق"
                                else -> "لا يوجد مفتاح مضبوط"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (privateKey.isNullOrBlank() && globalKey.isBlank()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }

                // Per-contact encryption mode selector
                Text(
                    text = "نوع التشفير الافتراضي لهذه الجهة:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val modeOptions = listOf(
                        null to "افتراضي التطبيق",
                        EncryptionMode.NONE to "بدون تشفير دائماً",
                        EncryptionMode.EXPLICIT to "تشفير صريح دائماً",
                        EncryptionMode.STEGANO to "تشفير مموّه دائماً",
                    )
                    modeOptions.forEach { (mode, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .selectable(
                                    selected = selectedMode == mode,
                                    role = Role.RadioButton,
                                    onClick = { selectedMode = mode },
                                )
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedMode == mode, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "المفتاح الخاص بهذه الجهة (اختياري):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("المفتاح الخاص") },
                    placeholder = { Text(if (globalKey.isNotBlank()) "المفتاح العام مفعل تلقائياً" else "أدخل مفتاح التشفير") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "إخفاء" else "إظهار",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { keyText = onGenerateKey() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("توليد عشوائي")
                    }
                    if (keyText.isNotBlank()) {
                        TextButton(
                            onClick = { clipboardManager.setText(AnnotatedString(keyText)) },
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("نسخ")
                        }
                    }
                }

                if (!privateKey.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            keyText = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("حذف المفتاح الخاص (استخدام العام)", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(keyText.trim().takeIf { it.isNotBlank() }, selectedMode)
                    onDismiss()
                },
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
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
    item: TimelineItem.Message,
    showSender: Boolean,
    selected: Boolean,
    formatter: TimeFormatter,
    selfSender: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenKeyDialog: () -> Unit,
) {
    val message = item.message
    val crypto = item.cryptoInfo
    val isOutgoing = message.sender == selfSender || message.sender == "You" || message.sender == "أنت"
    val colors = MaterialTheme.colorScheme
    val selectLabel = stringResource(R.string.action_select)
    var showCoverText by remember { mutableStateOf(false) }

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
                verticalArrangement = Arrangement.spacedBy(4.dp),
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

                if (crypto != null) {
                    if (crypto.isDecrypted) {
                        // Decrypted message with clean security badge (NO lock emoji)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    if (crypto.mode == EncryptionMode.STEGANO) showCoverText = !showCoverText
                                }
                                .padding(vertical = 1.dp),
                        ) {
                            Icon(
                                imageVector = if (crypto.mode == EncryptionMode.STEGANO) Icons.Filled.AutoFixHigh else Icons.Outlined.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (crypto.mode == EncryptionMode.STEGANO) colors.tertiary else colors.primary,
                            )
                            Text(
                                text = if (crypto.mode == EncryptionMode.STEGANO) "رسالة مموّهة (انقر لرؤية الغطاء)" else "رسالة مشفرة",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (crypto.mode == EncryptionMode.STEGANO) colors.tertiary else colors.primary,
                            )
                        }

                        if (showCoverText && !crypto.coverText.isNullOrBlank()) {
                            Surface(
                                color = colors.surface.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            ) {
                                Text(
                                    text = "النص الظاهر بالخارج: \"${crypto.coverText}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier.padding(4.dp),
                                )
                            }
                        }

                        Text(
                            text = crypto.decryptedText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        // Failed to decrypt with current keys (NO lock emoji in text)
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpenKeyDialog() }
                                .padding(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = colors.error,
                                )
                                Text(
                                    text = "رسالة مشفرة",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.error,
                                )
                            }
                            Text(
                                text = "المفتاح غير متطابق أو غير محدد (انقر لتعيين المفتاح)",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                            if (!crypto.coverText.isNullOrBlank()) {
                                Text(
                                    text = "النص الظاهر: \"${crypto.coverText}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = messageBodyText(message),
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = if (message.contentKind == ContentKind.TEXT) FontStyle.Normal else FontStyle.Italic,
                    )
                }

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
            Color(0xFF7E57C2),
            Color(0xFF1E88E5),
            Color(0xFF00897B),
            Color(0xFFE53935),
            Color(0xFFFB8C00),
            Color(0xFF00ACC1),
            Color(0xFF5E35B1),
            Color(0xFF3949AB),
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

/**
 * WimLow-style chat input bar with:
 * - Circular action/send button on the left (RTL): Send icon when text is entered, '+' quick actions when empty.
 * - Rounded capsule pill text box with Lock button replacing the sticker/emoji icon.
 * - Lock button enables/disables encryption or selects explicit/steganographic modes.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WimLowReplyBar(
    appLabel: String,
    draft: String,
    sending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    encryptionMode: EncryptionMode,
    conversationEncryptionMode: EncryptionMode?,
    onSetEncryptionMode: (EncryptionMode) -> Unit,
    onSetConversationEncryptionMode: (EncryptionMode?) -> Unit,
    hasPrivateKey: Boolean,
    isKeyConfigured: Boolean,
    customCoverText: String,
    onCustomCoverTextChange: (String) -> Unit,
    onOpenKeyDialog: () -> Unit,
    onOpenSourceApp: (() -> Unit)?,
    appIcon: Drawable?,
    onCycleEncryptionMode: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    var quickMenuOpen by remember { mutableStateOf(false) }
    var encryptionMenuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // Quick Actions Floating Menu (as shown in WimLow Screenshot 2 when '+' is tapped)
        if (quickMenuOpen) {
            Column(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .align(Alignment.Start),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (onOpenSourceApp != null) {
                    QuickActionPill(
                        label = "افتح $appLabel",
                        icon = {
                            if (appIcon != null) {
                                DrawableImage(appIcon, null, Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Outlined.Forum, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(20.dp))
                            }
                        },
                        onClick = {
                            quickMenuOpen = false
                            onOpenSourceApp()
                        },
                    )
                }
            }
        }

        // Steganography cover text row (if mode == STEGANO)
        if (encryptionMode == EncryptionMode.STEGANO) {
            Surface(
                color = colors.surfaceContainerHigh.copy(alpha = 0.9f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    TextField(
                        value = customCoverText,
                        onValueChange = onCustomCoverTextChange,
                        placeholder = { Text("جملة الغطاء (فارغة = جملة عفوية تلقائية)", style = MaterialTheme.typography.bodySmall) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { onCustomCoverTextChange(SteganographyEngine.randomCoverText()) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Filled.AutoFixHigh,
                            contentDescription = "جملة عشوائية",
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // Main Input Bar (Circular Button + Capsule Pill)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Circular action / send button
            Surface(
                shape = CircleShape,
                color = if (draft.isNotBlank()) Color(0xFF0084FF) else colors.primary,
                contentColor = Color.White,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (draft.isNotBlank()) {
                            onSend()
                        } else {
                            quickMenuOpen = !quickMenuOpen
                        }
                    }
                    .testTag(ConversationTestTags.REPLY_SEND),
                shadowElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (draft.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Filled.Add,
                        contentDescription = if (draft.isNotBlank()) stringResource(R.string.reply_send) else "إجراءات سريعة",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Rounded capsule pill input container
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = colors.surfaceContainerHigh,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    // Encryption mode button inside the capsule
                    Box {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = onCycleEncryptionMode,
                                    onLongClick = { encryptionMenuOpen = true },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = when (encryptionMode) {
                                    EncryptionMode.NONE -> CryptoIcons.RemoveModerator
                                    EncryptionMode.EXPLICIT -> CryptoIcons.ShieldLock
                                    EncryptionMode.STEGANO -> CryptoIcons.VerifiedUser
                                },
                                contentDescription = "وضع التشفير (انقر للتبديل، اضغط مطولاً للخيارات)",
                                tint = if (encryptionMode != EncryptionMode.NONE) {
                                    Color(0xFF00A2FF)
                                } else {
                                    colors.onSurfaceVariant.copy(alpha = 0.5f)
                                },
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        DropdownMenu(
                            expanded = encryptionMenuOpen,
                            onDismissRequest = { encryptionMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("بدون")
                                        if (encryptionMode == EncryptionMode.NONE) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.primary)
                                        }
                                    }
                                },
                                leadingIcon = { Icon(CryptoIcons.RemoveModerator, contentDescription = null) },
                                onClick = {
                                    encryptionMenuOpen = false
                                    onSetEncryptionMode(EncryptionMode.NONE)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("تشفير")
                                        if (encryptionMode == EncryptionMode.EXPLICIT) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.primary)
                                        }
                                    }
                                },
                                leadingIcon = { Icon(CryptoIcons.ShieldLock, contentDescription = null) },
                                onClick = {
                                    encryptionMenuOpen = false
                                    onSetEncryptionMode(EncryptionMode.EXPLICIT)
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("مموه")
                                        if (encryptionMode == EncryptionMode.STEGANO) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.primary)
                                        }
                                    }
                                },
                                leadingIcon = { Icon(CryptoIcons.VerifiedUser, contentDescription = null) },
                                onClick = {
                                    encryptionMenuOpen = false
                                    onSetEncryptionMode(EncryptionMode.STEGANO)
                                },
                            )
                        }
                    }

                    // Text field
                    TextField(
                        value = draft,
                        onValueChange = { if (it.length <= MAX_REPLY_LENGTH) onDraftChange(it) },
                        placeholder = {
                            Text(
                                text = "اكتب رسالتك",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant.copy(alpha = 0.55f),
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        enabled = !sending,
                        modifier = Modifier.weight(1f).testTag(ConversationTestTags.REPLY_FIELD),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionPill(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon()
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private const val MAX_REPLY_LENGTH = 4_000
