package net.sergeych.downmark

import kotlin.test.*

class ParserTest {

    @Test
    fun testParseSimple1() {
        val r = MarkdownDoc("hello, world!")
        println(r.body)
        assertTrue {  r.errors.isEmpty() }
        assertEquals(1, r.body.size )
        assertEquals("hello, world!",
            ((r.body.first() as BlockItem.Paragraph).content.first() as InlineItem.Text).text
        )
    }

    @Test
    fun testParseSimple2() {
        val r = MarkdownDoc("hello, _world_!")
        println(r.body)
        println(r.errors)
        assertTrue {  r.errors.isEmpty() }
        assertEquals("hello, ",
            ((r.body.first() as BlockItem.Paragraph).content.first() as InlineItem.Text).text
        )
        assertEquals("Text(I:world)",
            ((r.body.first() as BlockItem.Paragraph).content[1] as InlineItem.Text).toString()
        )
        assertEquals("!",
            ((r.body.first() as BlockItem.Paragraph).content[2] as InlineItem.Text).text
        )
    }

    @Test
    fun codeBlockTest() {
        val d = MarkdownDoc("""
            ~~~kt
                // line 1
                // line 2
            ~~~
        """.trimIndent())
        println(d.body)
        val t = (d.body[0] as BlockItem.Code).text
        println(t)
        assertEquals("""    // line 1
            |    // line 2
            |""".trimMargin(),t)
    }

    @Test
    fun headingsTest() {
        val d = MarkdownDoc("""
            # This is h1
            
            ## this is _a h2_
        """.trimIndent())
        assertEquals("This is h1",
            d.blockAt<BlockItem.Heading>(0).content.itemAt<InlineItem.Text>(0).text)
        println("d2: ${d.body[1]}")
        val cc = d.blockAt<BlockItem.Heading>(1).content
        assertEquals("this is ", cc.itemAt<InlineItem.Text>(0).text)
        val c2 = cc.itemAt<InlineItem.Text>(1)
        assertEquals("a h2", c2.text)
        assertTrue { c2.italics }
    }

    @Test
    fun horLineTest() {
        val d = MarkdownDoc("""
            # This is h1

            ---------------------------

            ## this is _a h2_
        """.trimIndent())
        assertEquals(BlockItem.HorizontalLine, d.body[1])
    }

    @Test
    fun horLineTest2() {
        val d = MarkdownDoc("""
            # This is h1
            ---------------------------

            ## this is _a h2_
        """.trimIndent())
        println(d.body)
        assertIs<BlockItem.Heading>(d.body[0])
        assertIs<BlockItem.Heading>(d.body[1])
    }

    @Test
    fun refTest() {
        val d = MarkdownDoc("""
            [welcome](toUs) and with title: [bienvenue](a_nous "welcome!")
            and with [named footnote][f1] or a [simple footnote].
            
            There could also be a [plain text] and an [external reference].
            
            ---------------------------

            ## this is _a h2_
            
            [f1]: fnlink1
            [simple footnote]: fnlink2
                        
        """.trimIndent()) {
            if( it == "external reference" ) "http://external.link"
            else null
        }
        println(d.body[0])
        val c1 = d.blockAt<BlockItem.Paragraph>(0).content
        val l1 = c1.itemAt<InlineItem.Link>(0)
        assertEquals( "welcome", l1.ref.name)
        val t = c1.itemAt<InlineItem.Text>(1)
        assertEquals(" and with title: ", t.text)
        val l2 = c1.itemAt<InlineItem.Link>(2)
        assertEquals("bienvenue", l2.ref.name)
        assertEquals("a_nous", l2.ref.link)
        assertEquals("welcome!", l2.ref.title)

        val l3 = c1.itemAt<InlineItem.Link>(4)
        assertEquals("named footnote", l3.ref.name)
        assertEquals("fnlink1", l3.ref.link)
        assertEquals(null, l3.ref.title)

        val l4 = c1.itemAt<InlineItem.Link>(6)
        assertEquals("simple footnote", l4.ref.name)
        assertEquals("fnlink2", l4.ref.link)
        assertEquals(null, l4.ref.title)

        val c2 = d.blockAt<BlockItem.Paragraph>(1).content
        val l11 = c2.itemAt<InlineItem.Text>(0)
        assertEquals( "There could also be a [plain text] and an ", l11.text)
        val l12 = c2.itemAt<InlineItem.Link>(1)
        assertEquals("external reference", l12.ref.name)
        assertEquals("http://external.link", l12.ref.link)
        assertEquals(Ref.Type.External, l12.ref.type)
    }

}