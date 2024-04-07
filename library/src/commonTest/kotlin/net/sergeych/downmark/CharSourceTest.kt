package net.sergeych.downmark

import kotlin.test.*

class CharSourceTest {
    @Test
    fun testEatings() {
        val s1 = CharSource("**oops end**")
        assertFalse { s1.getStart("_", "*") }
        assertEquals('*', s1.current)
        assertTrue { s1.getStart("__", "**") }
        assertEquals('o', s1.current)
        assertEquals("oops end**", s1.readToEndOfLine())
    }

}