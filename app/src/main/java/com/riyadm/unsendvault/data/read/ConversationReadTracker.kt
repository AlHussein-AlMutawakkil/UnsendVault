package com.riyadm.unsendvault.data.read

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Tracks unread message counts for conversations.
 *
 * Starts fresh ("من جديد") so that existing conversations are marked read (unreadCount = 0).
 * As new messages arrive via notifications, [onNewMessages] increments the unread count.
 * When a conversation is opened, [markAsRead] resets the counter to 0, hiding the badge.
 */
class ConversationReadTracker(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("conversation_read_state", Context.MODE_PRIVATE)

    @Volatile
    private var visibleConversationId: Long? = null

    private val _unreadCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<Long, Int>> = _unreadCounts.asStateFlow()

    init {
        val isInitialized = prefs.getBoolean(KEY_INITIALIZED, false)
        if (!isInitialized) {
            // First launch with unread tracking: mark all existing conversations as read
            prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
            _unreadCounts.value = emptyMap()
        } else {
            val loaded = mutableMapOf<Long, Int>()
            for ((key, value) in prefs.all) {
                if (key.startsWith(PREFIX_UNREAD) && value is Int && value > 0) {
                    val id = key.removePrefix(PREFIX_UNREAD).toLongOrNull()
                    if (id != null) {
                        loaded[id] = value
                    }
                }
            }
            _unreadCounts.value = loaded
        }
    }

    fun setVisibleConversation(conversationId: Long?) {
        visibleConversationId = conversationId
        if (conversationId != null) {
            markAsRead(conversationId)
        }
    }

    fun markAsRead(conversationId: Long) {
        prefs.edit().putInt(PREFIX_UNREAD + conversationId, 0).apply()
        _unreadCounts.update { current ->
            if (current.containsKey(conversationId)) current - conversationId else current
        }
    }

    fun onNewMessages(conversationId: Long, count: Int) {
        if (count <= 0) return
        if (conversationId == visibleConversationId) {
            // The user is currently reading this chat
            return
        }
        val currentCount = _unreadCounts.value[conversationId] ?: 0
        val newCount = currentCount + count
        prefs.edit().putInt(PREFIX_UNREAD + conversationId, newCount).apply()
        _unreadCounts.update { current ->
            current + (conversationId to newCount)
        }
    }

    fun unreadCount(conversationId: Long): Int {
        return _unreadCounts.value[conversationId] ?: 0
    }

    companion object {
        private const val KEY_INITIALIZED = "initialized_fresh"
        private const val PREFIX_UNREAD = "unread_"
    }
}
