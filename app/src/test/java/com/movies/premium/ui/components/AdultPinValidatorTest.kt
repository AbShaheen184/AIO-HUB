package com.movies.premium.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The PIN validation of the settings screen.
 *
 * The rules are the original's (`com.app.mlounge.ui.components.x`): the PIN has
 * to be exactly four characters long, and in the create variant the two fields
 * have to be equal. The original counts the characters — it does not check that
 * they are digits.
 */
class AdultPinValidatorTest {

    @Test
    fun `a four character pin is accepted`() {
        assertNull(AdultPinValidator.validate("1234"))
    }

    @Test
    fun `a too short pin is rejected`() {
        assertEquals(
            AdultPinValidator.ERROR_LENGTH,
            AdultPinValidator.validate("123"),
        )
    }

    @Test
    fun `an empty pin is rejected`() {
        assertEquals(
            AdultPinValidator.ERROR_LENGTH,
            AdultPinValidator.validate(""),
        )
    }

    @Test
    fun `a too long pin is rejected`() {
        assertEquals(
            AdultPinValidator.ERROR_LENGTH,
            AdultPinValidator.validate("12345"),
        )
    }

    @Test
    fun `the length is checked before the match`() {
        assertEquals(
            AdultPinValidator.ERROR_LENGTH,
            AdultPinValidator.validate("123", "123"),
        )
    }

    @Test
    fun `two matching fields are accepted`() {
        assertNull(AdultPinValidator.validate("1234", "1234"))
    }

    @Test
    fun `two differing fields are rejected`() {
        assertEquals(
            AdultPinValidator.ERROR_MISMATCH,
            AdultPinValidator.validate("1234", "4321"),
        )
    }

    @Test
    fun `the original counts characters rather than digits`() {
        // The original does not check the character set, so a pin of letters
        // passes the length check.
        assertNull(AdultPinValidator.validate("abcd"))
    }
}
