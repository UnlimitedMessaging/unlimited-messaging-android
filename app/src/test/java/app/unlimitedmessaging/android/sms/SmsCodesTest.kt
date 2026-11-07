package app.unlimitedmessaging.android.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsCodesTest {

    @Test
    fun `3gpp TP-Status ranges`() {
        assertEquals(DeliveryOutcome.DELIVERED, classifyDeliveryStatus(0x00, "3gpp"))
        assertEquals(DeliveryOutcome.DELIVERED, classifyDeliveryStatus(0x02, "3gpp"))
        assertEquals(DeliveryOutcome.PENDING, classifyDeliveryStatus(0x20, "3gpp"))
        assertEquals(DeliveryOutcome.FAILED, classifyDeliveryStatus(0x40, "3gpp"))
        assertEquals(DeliveryOutcome.FAILED, classifyDeliveryStatus(0x65, null))
    }

    @Test
    fun `3gpp2 error classes`() {
        assertEquals(DeliveryOutcome.DELIVERED, classifyDeliveryStatus(0, "3gpp2"))
        assertEquals(DeliveryOutcome.PENDING, classifyDeliveryStatus(2 shl 24, "3gpp2"))
        assertEquals(DeliveryOutcome.FAILED, classifyDeliveryStatus(3 shl 24, "3gpp2"))
    }

    @Test
    fun `radio errors are named`() {
        assertEquals("NO_SERVICE", radioError(4))
        assertEquals("RADIO_ERROR_99", radioError(99))
    }

    @Test
    fun `dedup key is stable and content-sensitive`() {
        val a = inboundDedupKey(1, "+33600000000", 1_700_000_000_000, "hello")
        assertEquals(a, inboundDedupKey(1, "+33600000000", 1_700_000_000_000, "hello"))
        assertEquals(64, a.length)
        assert(a != inboundDedupKey(2, "+33600000000", 1_700_000_000_000, "hello"))
        assert(a != inboundDedupKey(1, "+33600000000", 1_700_000_000_001, "hello"))
    }
}
