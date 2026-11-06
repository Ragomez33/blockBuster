package com.blockbuster.core.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrictionInputTest {

    @Test
    fun rejectsInsertionOfMoreThanThreeCharacters() {
        assertTrue(FrictionInput.isPaste("abc", "abcdefg"))
        assertEquals(4, FrictionInput.insertedCharacterCount("abc", "abcdefg"))
    }

    @Test
    fun allowsInsertionOfExactlyThreeCharacters() {
        assertFalse(FrictionInput.isPaste("abc", "abcdef"))
        assertEquals(3, FrictionInput.insertedCharacterCount("abc", "abcdef"))
    }

    @Test
    fun detectsPasteInTheMiddle() {
        assertTrue(FrictionInput.isPaste("abcdef", "abcXXXXdef"))
        assertEquals(4, FrictionInput.insertedCharacterCount("abcdef", "abcXXXXdef"))
    }

    @Test
    fun detectsSelectionReplacementAsInsertion() {
        assertTrue(FrictionInput.isPaste("abcdef", "abcXYZW"))
        assertEquals(4, FrictionInput.insertedCharacterCount("abcdef", "abcXYZW"))
    }

    @Test
    fun deletionIsNotPaste() {
        assertFalse(FrictionInput.isPaste("abcdef", "abc"))
        assertEquals(0, FrictionInput.insertedCharacterCount("abcdef", "abc"))
    }

    @Test
    fun typingOneCharacterIsNotPaste() {
        assertFalse(FrictionInput.isPaste("", "a"))
        assertEquals(1, FrictionInput.insertedCharacterCount("", "a"))
    }
}
