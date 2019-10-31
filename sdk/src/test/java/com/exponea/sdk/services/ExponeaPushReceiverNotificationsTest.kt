package com.exponea.sdk.services

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.exponea.sdk.Exponea
import com.exponea.sdk.manager.FcmManager
import com.exponea.sdk.manager.FcmManagerImpl
import com.exponea.sdk.manager.NotificationTestPayloads
import com.exponea.sdk.models.ExponeaConfiguration
import com.exponea.sdk.models.NotificationAction
import com.exponea.sdk.models.NotificationData
import com.exponea.sdk.preferences.ExponeaPreferencesImpl
import com.exponea.sdk.repository.FirebaseTokenRepositoryImpl
import com.exponea.sdk.repository.PushNotificationRepositoryImpl
import com.exponea.sdk.testutil.ExponeaSDKTest
import com.google.firebase.messaging.RemoteMessage
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf


@RunWith(ParameterizedRobolectricTestRunner::class)
internal class ExponeaPushReceiverNotificationsTest(
    name: String,
    private val notificationPayload: Map<String, String>,
    private val intentGetter: (notification: Notification) -> Intent,
    private val expectedTrackingData: NotificationData?,
    private val expectedTrackingActionData: NotificationAction?
) : ExponeaSDKTest() {
    private lateinit var manager: FcmManager
    private lateinit var notificationManager: NotificationManager

    companion object {
        data class TestCase(
            val name: String,
            val notificationPayload: Map<String, String>,
            val intentGetter: (notification: Notification) -> Intent,
            val expectedTrackingData: NotificationData?,
            val expectedTrackingActionData: NotificationAction?
        )

        private val testCases = arrayListOf(
            TestCase(
                name = "basic notification",
                notificationPayload = NotificationTestPayloads.BASIC_NOTIFICATION,
                intentGetter = { shadowOf(it.contentIntent).savedIntent },
                expectedTrackingData = null,
                expectedTrackingActionData = NotificationAction("notification", null, null)
            ),

            TestCase(
                name = "notification with deeplink",
                notificationPayload = NotificationTestPayloads.DEEPLINK_NOTIFICATION,
                intentGetter = { shadowOf(it.contentIntent).savedIntent },
                expectedTrackingData = null,
                expectedTrackingActionData = NotificationAction("notification", null, "app://test")
            ),

            TestCase(
                name = "notification with web url",
                notificationPayload = NotificationTestPayloads.BROWSER_NOTIFICATION,
                intentGetter = { shadowOf(it.contentIntent).savedIntent },
                expectedTrackingData = null,
                expectedTrackingActionData = NotificationAction("notification", null, "http://google.com")
            ),

            TestCase(
                name = "notification with actions",
                notificationPayload = NotificationTestPayloads.ACTIONS_NOTIFICATION,
                intentGetter = { shadowOf(it.contentIntent).savedIntent },
                expectedTrackingData = null,
                expectedTrackingActionData = NotificationAction("notification", null, null)
            ),

            TestCase(
                name = "notification with actions using first action",
                notificationPayload = NotificationTestPayloads.ACTIONS_NOTIFICATION,
                intentGetter = { shadowOf(it.actions[0].actionIntent).savedIntent },
                expectedTrackingData = null,
                expectedTrackingActionData = NotificationAction("button", "Action 1 title", null)
            ),

            TestCase(
                name = "notification with actions using second action",
                notificationPayload = NotificationTestPayloads.ACTIONS_NOTIFICATION,
                intentGetter = { shadowOf(it.actions[1].actionIntent).savedIntent },
                expectedTrackingData = null,
                expectedTrackingActionData = NotificationAction("button", "Action 2 title", "app://deeplink")
            ),

            TestCase(
                name = "notification with actions using third action",
                notificationPayload = NotificationTestPayloads.ACTIONS_NOTIFICATION,
                intentGetter = { shadowOf(it.actions[2].actionIntent).savedIntent },
                expectedTrackingData = null,
                expectedTrackingActionData = NotificationAction("button", "Action 3 title", "http://google.com")
            ),

            TestCase(
                "notification with attributes",
                NotificationTestPayloads.ATTRIBUTES_NOTIFICATION,
                intentGetter = { shadowOf(it.contentIntent).savedIntent },
                expectedTrackingData = NotificationData("5db1582b1b2be24d0bee4de9", "push with buttons", 2),
                expectedTrackingActionData = NotificationAction("notification", null, null)
            )
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Opening {0}")
        fun data(): List<Array<out Any?>> {
            return testCases.map {
                arrayOf(
                    it.name,
                    it.notificationPayload,
                    it.intentGetter,
                    it.expectedTrackingData,
                    it.expectedTrackingActionData
                )
            }
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        manager = FcmManagerImpl(
            context,
            ExponeaConfiguration(),
            FirebaseTokenRepositoryImpl(ExponeaPreferencesImpl(context)),
            PushNotificationRepositoryImpl(ExponeaPreferencesImpl(context))
        )
        notificationManager = spyk(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        mockkObject(Exponea)
        every { Exponea.trackDeliveredPush(any(), any()) } just Runs
        every { Exponea.trackClickedPush(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun getRemoteMessage(notification: Map<String, String>): RemoteMessage {
        val messageBundle = Bundle()
        notification.entries.forEach { messageBundle.putString(it.key, it.value) }
        return RemoteMessage(messageBundle)
    }

    @Test
    fun onReceiveTest() {
        val notificationSlot = slot<Notification>()
        every { notificationManager.notify(any(), capture(notificationSlot)) } just Runs
        manager.handleRemoteMessage(getRemoteMessage(notificationPayload), notificationManager, true)

        ExponeaPushReceiver().onReceive(
            ApplicationProvider.getApplicationContext(),
            intentGetter(notificationSlot.captured)
        )
        verify(exactly = 1) {
            Exponea.trackClickedPush(expectedTrackingData, expectedTrackingActionData, any())
        }
    }
}