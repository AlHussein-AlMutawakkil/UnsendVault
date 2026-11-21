package com.riyadm.unsendvault.reply

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.riyadm.unsendvault.capture.NotificationSnapshot
import com.riyadm.unsendvault.capture.SnapshotFactory
import com.riyadm.unsendvault.capture.adapter.AdapterRegistry
import com.riyadm.unsendvault.capture.parser.ParseOutcome

/**
 * In-memory LRU cache of the most recent free-text reply actions ([Notification.Action] with free-form [android.app.RemoteInput])
 * captured from messaging notifications.
 *
 * This allows users to reply to chats (e.g. WhatsApp, Telegram, Signal, Messenger) even when the notification has been
 * dismissed or cleared from the status bar. The cached action remains valid until superseded by a newer notification
 * for the same conversation, evicted by capacity, or invalidated when the source app cancels its [android.app.PendingIntent]
 * (raising [android.app.PendingIntent.CanceledException]).
 */
class ReplyActionCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    data class Entry(
        val action: Notification.Action,
        val postTime: Long,
        val cachedAt: Long = System.currentTimeMillis(),
    )

    private val lock = Any()

    @Suppress("SerialVersionUIDInSerializableClass")
    private val map = object : LinkedHashMap<Pair<String, String>, Entry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String>, Entry>?): Boolean {
            return size > maxEntries
        }
    }

    /**
     * Records [action] for the conversation identified by ([packageName], [conversationKey]).
     * If an entry already exists, it is updated only if [postTime] is greater than or equal to the existing entry's time.
     */
    fun record(packageName: String, conversationKey: String, action: Notification.Action, postTime: Long) {
        synchronized(lock) {
            val existing = map[packageName to conversationKey]
            if (existing == null || postTime >= existing.postTime) {
                map[packageName to conversationKey] = Entry(action, postTime)
            }
        }
    }

    /**
     * Inspects [sbn] and [snapshot], and if it contains a free-text reply action and matches a single conversation
     * in [registry], records it into this cache.
     */
    fun recordFromNotification(
        sbn: StatusBarNotification,
        snapshot: NotificationSnapshot,
        registry: AdapterRegistry,
    ) {
        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val action = ReplyActions.replyAction(notification) ?: return
        val parsed = registry.parse(snapshot).outcome as? ParseOutcome.Parsed ?: return
        val keys = parsed.messages.mapTo(HashSet()) { it.conversationKey } + parsed.deletionMarkers.map { it.conversationKey }
        val convKey = keys.singleOrNull() ?: return
        val pkg = sbn.packageName ?: return
        record(pkg, convKey, action, sbn.postTime)
    }

    /**
     * Inspects [sbn], builds a snapshot using [ownPackage], and records the reply action if valid.
     */
    fun recordFromNotification(
        sbn: StatusBarNotification,
        registry: AdapterRegistry,
        ownPackage: String,
    ) {
        val snapshot = SnapshotFactory.fromStatusBarNotification(sbn, ownPackage) ?: return
        recordFromNotification(sbn, snapshot, registry)
    }

    /** Retrieves the cached action for this conversation, or null if none is cached. */
    fun get(packageName: String, conversationKey: String): Entry? {
        synchronized(lock) {
            return map[packageName to conversationKey]
        }
    }

    /** Removes the cached action for this conversation (e.g. after a [android.app.PendingIntent.CanceledException]). */
    fun remove(packageName: String, conversationKey: String): Entry? {
        synchronized(lock) {
            return map.remove(packageName to conversationKey)
        }
    }

    /** Clears all cached actions (e.g. on cache reset). */
    fun clear() {
        synchronized(lock) {
            map.clear()
        }
    }

    /** Current number of cached conversations. */
    val size: Int
        get() = synchronized(lock) { map.size }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 100
    }
}
