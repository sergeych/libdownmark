package net.sergeych.downmark

import kotlin.test.*

class ParserContextText {

    @Test
    fun testExpandTabs() {

        assertEquals("123", "123".expandTabs(5))
        assertEquals("  123", "  123".expandTabs(5))
        assertEquals( "     123", "\t123".expandTabs(5))
        assertEquals( "     123", " \t123".expandTabs(5))
        assertEquals( "     123", "    \t123".expandTabs(5))
        assertEquals( "          123", "     \t123".expandTabs(5))

        assertEquals( "     123  1", "    \t123\t1".expandTabs(5))
    }

    @Test
    fun testNonFencedCodeBlock() {
        val b = ParserContext("""
            |    hello
            |    world
            |    
            |    !
        """.trimMargin()).parseBlock().first()
        assertIs<BlockItem.Code>(b)
        assertEquals("""
            hello
            world
            
            !
            
        """.trimIndent(),
            b.text
        )
    }

    @Test
    fun testNonFencedCodeBlock2() {
        val b = ParserContext("""
            |           hello
            |           world
            |    
            |           !
        """.trimMargin(), 5, 7).parseBlock().first()
        assertIs<BlockItem.Code>(b)
        assertEquals("""
            hello
            world
            
            !
            
        """.trimIndent(),
            b.text
        )
    }


    @Test
    fun testParseSimpleText() {
        val b = ParserContext("""
            |    hello
            |    world
            |    
            |    !
        """.trimMargin(),1,3).parseBlock().first()
        assertIs<BlockItem.Paragraph>(b)
        assertEquals("""
            hello world
        """.trimIndent(),
            b.content.text()
        )

    }

}