package com.riyadm.unsendvault.reply

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput as CompatRemoteInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riyadm.unsendvault.capture.SnapshotFactory
import com.riyadm.unsendvault.capture.adapter.AdapterRegistry
import com.riyadm.unsendvault.capture.parser.CapturePlaceholders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ReplyActionCacheTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = AdapterRegistry.createDefault(CapturePlaceholders())

    @Suppress("DEPRECATION")
    private fun dummyAction(code: Int): Notification.Action {
        val intent = PendingIntent.getBroadcast(
            context, code, Intent("ACTION_TEST_$code"), PendingIntent.FLAG_MUTABLE,
        )
        return NotificationCompat.Action.Builder(0, "Reply", intent)
            .addRemoteInput(CompatRemoteInput.Builder("key").setLabel("Reply").build())
            .build()
            .let { NotificationCompat.Action.Builder(it).build() }
            .run {
                Notification.Action.Builder(0, "Reply", intent)
                    .addRemoteInput(android.app.RemoteInput.Builder("key").setLabel("Reply").build())
                    .build()
            }
    }

    @Test
    fun record_and_get() {
        val cache = ReplyActionCache(maxEntries = 5)
        val action = dummyAction(1)
        cache.record("com.whatsapp", "user1", action, postTime = 1000L)

        val entry = cache.get("com.whatsapp", "user1")
        assertNotNull(entry)
        assertEquals(action, entry!!.action)
        assertEquals(1000L, entry.postTime)
        assertEquals(1, cache.size)
    }

    @Test
    fun record_updatesOnlyWithNewerOrEqualPostTime() {
        val cache = ReplyActionCache()
        val oldAction = dummyAction(1)
        val newAction = dummyAction(2)

        cache.record("com.whatsapp", "user1", oldAction, postTime = 2000L)
        // Attempt to record with older postTime: should not overwrite
        cache.record("com.whatsapp", "user1", newAction, postTime = 1500L)
        assertEquals(oldAction, cache.get("com.whatsapp", "user1")!!.action)

        // Attempt to record with newer postTime: should overwrite
        cache.record("com.whatsapp", "user1", newAction, postTime = 2500L)
        assertEquals(newAction, cache.get("com.whatsapp", "user1")!!.action)
    }

    @Test
    fun lru_evictsOldestWhenMaxEntriesExceeded() {
        val cache = ReplyActionCache(maxEntries = 2)
        val a1 = dummyAction(1)
        val a2 = dummyAction(2)
        val a3 = dummyAction(3)

        cache.record("com.whatsapp", "u1", a1, 100L)
        cache.record("com.whatsapp", "u2", a2, 200L)
        assertEquals(2, cache.size)

        // Access u1 so u2 becomes the least recently used
        assertNotNull(cache.get("com.whatsapp", "u1"))

        // Record u3: should evict u2
        cache.record("com.whatsapp", "u3", a3, 300L)
        assertEquals(2, cache.size)
        assertNotNull(cache.get("com.whatsapp", "u1"))
        assertNull(cache.get("com.whatsapp", "u2"))
        assertNotNull(cache.get("com.whatsapp", "u3"))
    }

    @Test
    fun remove_and_clear() {
        val cache = ReplyActionCache()
        val action = dummyAction(1)
        cache.record("com.whatsapp", "u1", action, 100L)
        cache.record("com.telegram", "u2", action, 200L)
        assertEquals(2, cache.size)

        cache.remove("com.whatsapp", "u1")
        assertNull(cache.get("com.whatsapp", "u1"))
        assertEquals(1, cache.size)

        cache.clear()
        assertEquals(0, cache.size)
    }

    @Test
    fun recordFromNotification_withSnapshot() {
        val cache = ReplyActionCache()
        val action = dummyAction(1)
        val intent = PendingIntent.getBroadcast(context, 1, Intent("TEST"), PendingIntent.FLAG_MUTABLE)
        val compatAction = NotificationCompat.Action.Builder(0, "Reply", intent)
            .addRemoteInput(CompatRemoteInput.Builder("key").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()

        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
            .addMessage("hello", 1000L, Person.Builder().setName("Alex").build())
        val notification = NotificationCompat.Builder(context, "chat")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("Alex")
            .setContentText("hello")
            .setStyle(style)
            .addAction(compatAction)
            .build()

        @Suppress("DEPRECATION")
        val sbn = StatusBarNotification(
            "com.whatsapp", "com.whatsapp", 1, null, 10001, 0, 0,
            notification, Process.myUserHandle(), 1000L,
        )

        val snap = SnapshotFactory.fromStatusBarNotification(sbn, context.packageName)!!
        val parsed = registry.parse(snap).outcome as com.riyadm.unsendvault.capture.parser.ParseOutcome.Parsed
        val expectedKey = parsed.messages.map { it.conversationKey }.distinct().single()

        cache.recordFromNotification(sbn, snap, registry)

        assertEquals(1, cache.size)
        val entry = cache.get("com.whatsapp", expectedKey)
        assertNotNull(entry)
        assertEquals(1000L, entry!!.postTime)
    }
}
