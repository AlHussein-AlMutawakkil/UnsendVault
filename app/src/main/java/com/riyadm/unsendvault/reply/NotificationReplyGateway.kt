package com.riyadm.unsendvault.reply

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.riyadm.unsendvault.capture.CaptureLog
import com.riyadm.unsendvault.capture.SnapshotFactory
import com.riyadm.unsendvault.capture.VaultNotificationListener
import com.riyadm.unsendvault.capture.adapter.AdapterRegistry
import com.riyadm.unsendvault.capture.parser.ParseOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [ReplyGateway] on the bound [VaultNotificationListener] with fallback to [ReplyActionCache].
 *
 * When the user opens a chat (or presses Send), this first checks active notifications in the status bar for a free-text
 * reply action matching this chat. If the notification was dismissed or cleared, it falls back to the most recent cached
 * reply action recorded for this conversation, allowing replies without needing the notification to stay in the status bar.
 * A notification that mixes several chats (e.g. a bundled summary) is never used, so a reply can't go to the wrong person.
 */
class NotificationReplyGateway(
    context: Context,
    private val registry: () -> AdapterRegistry,
    private val activeNotifications: (String) -> List<StatusBarNotification>? = VaultNotificationListener::activeNotificationsOf,
    private val cache: ReplyActionCache = ReplyActionCache(),
) : ReplyGateway {
    private val context = context.applicationContext

    override suspend fun availability(packageName: String, conversationKey: String): ReplyAvailability = withContext(Dispatchers.Default) {
        when (val found = find(packageName, conversationKey)) {
            Lookup.ListenerOff -> ReplyAvailability.LISTENER_OFF
            Lookup.None -> ReplyAvailability.NO_NOTIFICATION
            is Lookup.Found -> ReplyAvailability.AVAILABLE
        }
    }

    override suspend fun send(packageName: String, conversationKey: String, text: String): ReplyResult = withContext(Dispatchers.Default) {
        val body = text.trim()
        if (body.isEmpty()) return@withContext ReplyResult.EMPTY
        val found = find(packageName, conversationKey) as? Lookup.Found ?: return@withContext ReplyResult.UNAVAILABLE
        try {
            ReplyActions.send(context, found.action, body)
            ReplyResult.SENT
        } catch (e: PendingIntent.CanceledException) {
            cache.remove(packageName, conversationKey)
            ReplyResult.UNAVAILABLE
        } catch (e: RuntimeException) {
            CaptureLog.d("reply failed: ${e.javaClass.simpleName}")
            ReplyResult.FAILED
        }
    }

    private sealed interface Lookup {
        data object ListenerOff : Lookup
        data object None : Lookup
        data class Found(val action: Notification.Action) : Lookup
    }

    private fun find(packageName: String, conversationKey: String): Lookup {
        val active = activeNotifications(packageName)
        val adapters = registry()
        val match = active
            ?.asSequence()
            ?.filter { it.notification?.flags?.and(Notification.FLAG_GROUP_SUMMARY) == 0 }
            ?.mapNotNull { sbn ->
                val action = ReplyActions.replyAction(sbn.notification) ?: return@mapNotNull null
                val snapshot = SnapshotFactory.fromStatusBarNotification(sbn, context.packageName) ?: return@mapNotNull null
                val parsed = adapters.parse(snapshot).outcome as? ParseOutcome.Parsed ?: return@mapNotNull null
                val keys = parsed.messages.mapTo(HashSet()) { it.conversationKey } + parsed.deletionMarkers.map { it.conversationKey }
                if (keys.singleOrNull() == conversationKey) sbn.postTime to action else null
            }
            ?.maxByOrNull { it.first }

        if (match != null) {
            cache.record(packageName, conversationKey, match.second, match.first)
            return Lookup.Found(match.second)
        }

        val cached = cache.get(packageName, conversationKey)
        if (cached != null) {
            return Lookup.Found(cached.action)
        }

        return if (active == null) Lookup.ListenerOff else Lookup.None
    }
}

/** Finding and firing a notification's free-text reply action. */
object ReplyActions {
    /**
     * The action a user would type a reply into: prefer the one marked [Notification.Action.SEMANTIC_ACTION_REPLY], else
     * the first with a free-form [RemoteInput]. Wear OS actions are checked too (some apps only put the reply there).
     */
    fun replyAction(notification: Notification?): Notification.Action? {
        if (notification == null) return null
        val candidates = buildList {
            notification.actions?.let { addAll(it.filterNotNull()) }
            try {
                @Suppress("DEPRECATION")
                addAll(Notification.WearableExtender(notification).actions)
            } catch (e: RuntimeException) {
                // Malformed extras: ignore Wear actions.
            }
        }.filter { a -> a.actionIntent != null && a.remoteInputs?.any { it.allowFreeFormInput } == true }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            candidates.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY }?.let { return it }
        }
        return candidates.firstOrNull()
    }

    fun send(context: Context, action: Notification.Action, text: String) {
        val inputs = action.remoteInputs?.filter { it.allowFreeFormInput }.orEmpty()
        require(inputs.isNotEmpty())
        val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
        RemoteInput.addResultsToIntent(inputs.toTypedArray(), intent, results)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Some apps open their chat after a reply; the user is looking at UnsendVault, so that start is user-initiated.
            ActivityOptions.makeBasic().apply {
                @Suppress("DEPRECATION")
                setPendingIntentBackgroundActivityStartMode(
                    if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                    else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            }.toBundle()
        } else {
            null
        }
        action.actionIntent.send(context, 0, intent, null, null, null, options)
    }
}
