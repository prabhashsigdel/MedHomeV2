package com.medhome.nepal.data

import com.medhome.nepal.fakes.InMemorySavePromptHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordSaveOffersTest {

    private val history = InMemorySavePromptHistory()
    private val offers = PasswordSaveOffers(history)

    @Test
    fun `saved and declined both stop future offers for that account`() {
        offers.record("a@b.co", SaveOutcome.SAVED)
        offers.record("c@d.co", SaveOutcome.DECLINED)
        offers.offer("a@b.co", "password123")
        assertNull(offers.pending.value)
        offers.offer("c@d.co", "password123")
        assertNull(offers.pending.value)
    }

    @Test
    fun `an unavailable password manager does not count as asked`() {
        offers.record("a@b.co", SaveOutcome.UNAVAILABLE)
        offers.offer("a@b.co", "password123")
        assertNotNull(offers.pending.value)
    }

    @Test
    fun `an offer can be taken only once and discarded`() {
        offers.offer("a@b.co", "password123")
        assertNotNull(offers.take())
        assertNull(offers.take())

        offers.offer("a@b.co", "password123")
        offers.discard()
        assertNull(offers.pending.value)
    }

    @Test
    fun `pending offers never print the password or email`() {
        val text = PendingPasswordSave("a@b.co", "password123").toString()
        assertFalse(text.contains("password123"))
        assertFalse(text.contains("a@b.co"))
        assertFalse(SavedCredential.Password("a@b.co", "password123").toString().contains("password123"))
    }

    @Test
    fun `stored key is a case-insensitive hash, not the email`() {
        val hash = hashOf("Asha@Example.com")
        assertEquals(hashOf(" asha@example.com "), hash)
        assertEquals(64, hash.length)
        assertTrue(hash.none { it == '@' })
    }
}
