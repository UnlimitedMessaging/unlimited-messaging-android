package app.unlimitedmessaging.android.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class TelemetryRulesTest {

    @Test
    fun `no previous checklist means a baseline, not events`() {
        assertTrue(diffSetupSteps(null, setOf(SetupStep.SMS, SetupStep.BATTERY)).isEmpty)
    }

    @Test
    fun `reports steps granted and revoked since last time, in checklist order`() {
        val diff = diffSetupSteps(
            previous = setOf(SetupStep.SMS, SetupStep.NOTIFICATIONS),
            current = setOf(SetupStep.PHONE_NUMBERS, SetupStep.SMS, SetupStep.BATTERY),
        )
        assertEquals(listOf(SetupStep.BATTERY, SetupStep.PHONE_NUMBERS), diff.completed)
        assertEquals(listOf(SetupStep.NOTIFICATIONS), diff.revoked)
        assertTrue(diffSetupSteps(setOf(SetupStep.SMS), setOf(SetupStep.SMS)).isEmpty)
    }

    @Test
    fun `an outage is one episode, whatever the number of retries`() {
        val tracker = OfflineEpisodeTracker()
        assertNull(tracker.onContact(0))
        tracker.onFailure(UnknownHostException("api.example.com"), now = 1_000)
        tracker.onFailure(IOException("timeout"), now = 31_000)
        tracker.onFailure(IOException("timeout"), now = 91_000)
        val episode = tracker.onContact(now = 121_000)
        assertEquals(OfflineEpisodeTracker.Episode(durationSeconds = 120, errorClass = "UnknownHostException", attempts = 3), episode)
        assertNull("closed episodes are reported once", tracker.onContact(130_000))
    }

    @Test
    fun `crash summary keeps the root class and app frames, never the message`() {
        val root = IllegalStateException("secret +33612345678").apply {
            stackTrace = arrayOf(
                StackTraceElement("android.os.Handler", "dispatchMessage", "Handler.java", 1),
                StackTraceElement("app.unlimitedmessaging.android.sms.SmsSender", "send", "SmsSender.kt", 10),
                StackTraceElement("app.unlimitedmessaging.android.service.GatewayService\$runLoop\$1", "invokeSuspend", "GatewayService.kt", 20),
            )
        }
        val wrapper = RuntimeException("Unable to start service", root).apply {
            stackTrace = arrayOf(StackTraceElement("app.unlimitedmessaging.android.GatewayApp", "onCreate", "GatewayApp.kt", 5))
        }
        assertEquals("IllegalStateException", CrashSummary.exceptionClass(wrapper))
        assertEquals(
            listOf("SmsSender#send", "GatewayService\$runLoop\$1#invokeSuspend", "GatewayApp#onCreate"),
            CrashSummary.appFrames(wrapper),
        )
    }

    @Test
    fun `crash summary keeps at most five frames`() {
        val error = Error().apply {
            stackTrace = Array(12) { StackTraceElement("app.unlimitedmessaging.android.A$it", "m", null, -1) }
        }
        assertEquals(CrashSummary.MAX_FRAMES, CrashSummary.appFrames(error).size)
    }
}
