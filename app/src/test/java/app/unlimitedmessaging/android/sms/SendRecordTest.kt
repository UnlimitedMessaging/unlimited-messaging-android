package app.unlimitedmessaging.android.sms

import app.unlimitedmessaging.android.api.ReportedStatus.DELIVERED
import app.unlimitedmessaging.android.api.ReportedStatus.FAILED
import app.unlimitedmessaging.android.api.ReportedStatus.SENT
import app.unlimitedmessaging.android.api.ReportedStatus.UNDELIVERABLE
import org.junit.Assert.assertEquals
import org.junit.Test

class SendRecordTest {
    private fun record(parts: Int) = SendRecord("m1", parts, createdAt = 0)

    @Test
    fun `single part goes SENT then DELIVERED`() {
        val sent = record(1).onPartSent(0)
        assertEquals(listOf(SENT), sent.emitted)
        val delivered = sent.record.onPartDelivered(0)
        assertEquals(listOf(DELIVERED), delivered.emitted)
    }

    @Test
    fun `multipart is SENT only once every part is sent`() {
        val afterFirst = record(3).onPartSent(0)
        assertEquals(emptyList<String>(), afterFirst.emitted)
        val afterSecond = afterFirst.record.onPartSent(2)
        assertEquals(emptyList<String>(), afterSecond.emitted)
        assertEquals(listOf(SENT), afterSecond.record.onPartSent(1).emitted)
    }

    @Test
    fun `the same part reported twice does not count twice`() {
        val twice = record(2).onPartSent(0).record.onPartSent(0)
        assertEquals(emptyList<String>(), twice.emitted)
    }

    @Test
    fun `one failed part fails the message once, and later parts change nothing`() {
        val failed = record(2).onPartSent(0).record.onPartFailed(1, "NO_SERVICE")
        assertEquals(listOf(FAILED), failed.emitted)
        assertEquals("NO_SERVICE", failed.record.errorCode)
        assertEquals(emptyList<String>(), failed.record.onPartSent(1).emitted)
        assertEquals(emptyList<String>(), failed.record.onPartDelivered(0).emitted)
    }

    @Test
    fun `a delivery report before the sent callback emits SENT and DELIVERED in order`() {
        val delivered = record(1).onPartDelivered(0)
        assertEquals(listOf(SENT, DELIVERED), delivered.emitted)
    }

    @Test
    fun `multipart is DELIVERED only once every part is delivered`() {
        var r = record(2).onPartSent(0).record.onPartSent(1).record
        val first = r.onPartDelivered(0)
        assertEquals(emptyList<String>(), first.emitted)
        r = first.record
        assertEquals(listOf(DELIVERED), r.onPartDelivered(1).emitted)
    }

    @Test
    fun `a permanent delivery failure makes the message UNDELIVERABLE`() {
        val r = record(1).onPartSent(0).record.onPartUndeliverable(0, AppErrorCodes.DELIVERY_FAILED)
        assertEquals(listOf(UNDELIVERABLE), r.emitted)
        assertEquals(emptyList<String>(), r.record.onPartDelivered(0).emitted)
    }
}
