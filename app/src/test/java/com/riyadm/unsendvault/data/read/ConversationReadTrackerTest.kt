package com.riyadm.unsendvault.data.read

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ConversationReadTrackerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.getSharedPreferences("conversation_read_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun freshLaunch_startsEmpty() {
        val tracker = ConversationReadTracker(context)
        assertEquals(0, tracker.unreadCount(100L))
        assertEquals(emptyMap<Long, Int>(), tracker.unreadCounts.value)
    }

    @Test
    fun newMessages_incrementsCount() {
        val tracker = ConversationReadTracker(context)
        tracker.onNewMessages(1L, 2)
        assertEquals(2, tracker.unreadCount(1L))
        assertEquals(mapOf(1L to 2), tracker.unreadCounts.value)

        tracker.onNewMessages(1L, 1)
        assertEquals(3, tracker.unreadCount(1L))
    }

    @Test
    fun markAsRead_resetsCount() {
        val tracker = ConversationReadTracker(context)
        tracker.onNewMessages(1L, 5)
        tracker.onNewMessages(2L, 1)

        tracker.markAsRead(1L)
        assertEquals(0, tracker.unreadCount(1L))
        assertEquals(1, tracker.unreadCount(2L))
        assertEquals(mapOf(2L to 1), tracker.unreadCounts.value)
    }

    @Test
    fun visibleConversation_doesNotIncrement() {
        val tracker = ConversationReadTracker(context)
        tracker.setVisibleConversation(1L)

        tracker.onNewMessages(1L, 3)
        assertEquals(0, tracker.unreadCount(1L))

        tracker.setVisibleConversation(null)
        tracker.onNewMessages(1L, 2)
        assertEquals(2, tracker.unreadCount(1L))
    }

    @Test
    fun persistsAcrossInstances() {
        val tracker1 = ConversationReadTracker(context)
        tracker1.onNewMessages(42L, 7)

        val tracker2 = ConversationReadTracker(context)
        assertEquals(7, tracker2.unreadCount(42L))
        assertEquals(mapOf(42L to 7), tracker2.unreadCounts.value)
    }
}
