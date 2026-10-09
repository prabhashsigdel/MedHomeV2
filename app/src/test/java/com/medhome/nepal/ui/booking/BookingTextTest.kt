package com.medhome.nepal.ui.booking

import com.medhome.nepal.R
import com.medhome.nepal.domain.CancelledBy
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingTextTest {

    @Test
    fun `the card says who cancelled, or just Cancelled when unknown`() {
        assertEquals(R.string.booking_status_cancelled_by_you, cancelledStatusText(CancelledBy.PATIENT))
        assertEquals(R.string.booking_status_cancelled_by_clinic, cancelledStatusText(CancelledBy.CLINIC))
        assertEquals(R.string.booking_status_cancelled, cancelledStatusText(null))
    }

    @Test
    fun `the detail notice says who cancelled, or just that it was when unknown`() {
        assertEquals(R.string.booking_cancelled_by_you_notice, cancelledNoticeText(CancelledBy.PATIENT))
        assertEquals(R.string.booking_cancelled_by_clinic_notice, cancelledNoticeText(CancelledBy.CLINIC))
        assertEquals(R.string.booking_cancelled_notice, cancelledNoticeText(null))
    }
}
