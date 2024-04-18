package net.sergeych.downmark

/**
 * Create a Markdown parser that will parse text and use optional
 * external links resolver.
 *
 * @param text Markdown text to parse
 * @param linkResolver callback to resolve external links.
 */
class Parser(
    val text: String,
    val tabSize: Int = 4,
    /**
     * Resolver of the links not completely defined in the document. Resolved s called
     * when parser detects a link in form `[link_text]`, where
     * `link_text` is not presented in footnote links (like `[text_link]: some_link_body`.
     * The parser then calls linkResolver if present, and if it returns not null, parser
     * creates [InlineItem.Link] with [Ref.link] and [Ref.title]  as returned by the parser,
     * and [Ref.name] of `text_link`.
     *
     * If resolver returns null, parser will insert the link as a plain text.
     *
     * Link returned by the `linkResolver` should conform to Markdown link format, e.g. either
     * `link_without_spaces` or `link_without_spaces "title"`.
     */
    @Suppress("KDocUnresolvedReference") private val linkResolver: (String) -> String? = { null },
) {

    private var lastBlock: BlockItem? = null
    private val src = CharSource(text)

    private var _errors = mutableListOf<SyntaxError>()
    private val errors: List<SyntaxError> = _errors

    private val footnotes = mutableMapOf<String, Ref>()

    private var italic = false
    private var bold = false
    private var strikeThrough = false
    private var symbol = false

    private val mItalic = Modifier("_", "*") { italic = it }
    private val mBold = Modifier("__", "**") { bold = it }
    private val mMBoldItalic = Modifier("___", "***") { bold = it; italic = it }
    private val mStrikeThrough = Modifier("~~") { strikeThrough = it }
    private val mSymbol = Modifier("`") { symbol = it }

    private val markupRanges = mutableListOf<OpenEndRange<Pos>>()

    // Keep the proper order -- it is important!
    private val allModifiers = arrayOf(mMBoldItalic, mBold, mItalic, mStrikeThrough, mSymbol)

    /**
     * Important! __it could only be used for tokens conained in a line, tokens spanned
     * across multiple lines will not work!_ (it's easy to update, but markdown does not
     * have any).
     */
    private fun addRange(m: Modifier) {
        if (m.currentTokenLength > 0) {
            val pos = src.pos()
            markupRanges += pos..<src.posAt(pos.row, pos.col + m.currentTokenLength)
        }
    }

    fun parse(): MarkdownDoc {
        scanReferences()
        val result = mutableListOf<BlockItem>()
        while (true) {
            parseBlock(src)?.let { result += it } ?: break
        }
        return MarkdownDoc(text, result, errors)
    }

    /**
     * First pass: detect all footnote references
     */
    private fun scanReferences() = src.mark {
        while (!src.end) {
            val lineStart = src.pos()
            val name = src.readBracedInLine('[', ']')
            if (name == null || src.current != ':') {
                src.skipLine(); continue
            }
            src.advance()
            src.skipWs()
            val start2 = src.pos()
            val mup1 = lineStart..<start2
            val s = src.readToEndOfLine()
            if (s.isBlank()) {
                addError("malformed footnote")
            } else {
                val (link, title) = extractLinkAndTitle(s)
                footnotes[name] =
                    Ref(name, link, title, Ref.Type.Footnote, MarkupPlacement(listOf(mup1), start2..<src.pos()))
            }
        }
        rewind()
    }

    private fun parseBlock(src: CharSource): BlockItem? = doParseBlock(src).also { lastBlock = it }

    private fun doParseBlock(src: CharSource): BlockItem? {

        val start = src.pos()

        while (src.isBlankToEndOfLine() && !src.end)
            src.skipLine()
        if (src.end) return null

        if (src.col != 0) throw RuntimeException("parseBlock must be called from line start: ${src.pos()}")

        if (src.current == '#') {
            src.expectAny(*headings)?.let {
                val bodyStart = src.pos()
                val mup = start..<bodyStart
                return BlockItem.Heading(
                    it.length - 1, parseContent(),
                    MarkupPlacement(
                        listOf(mup),
                        bodyStart..<src.pos()
                    )
                )
            }
        }

        // todo: lists

        if (src.current == '~' || src.current == '`') {
            src.expectAny("~~~", "```")?.let {
                readCodeBlock(start, it)?.let { return it }
            }
        }

        if (src.current == '-') {
            val found = src.mark {
                while (src.current == '-') src.advance()
                src.skipWs()
                if (src.eol && src.col >= 3) {
                    src.advance()
                    true
                } else {
                    rewind()
                    false
                }
            }
            if (found)
                return BlockItem.HorizontalLine(MarkupPlacement(listOf(start..<src.pos()), null))
            // todo: dashed list level 0
        }

        src.skipWs()
        val next = src.nextInLine()
        when (src.current) {
            '-', '+', '*' -> if (next == ' ' || next == '\t') {
                return extractUnorderedList()
            }

            in '0'..'9' -> {
                tryExtractOrderedList()?.let { return it }
            }

            else -> {}
        }
        // Now it should be a text block, probably idented:

        return BlockItem.Paragraph(
            currentIndentLevel(),
            parseContent(),
            MarkupPlacement(listOf(), start..<src.pos())
        )

    }

    private fun currentIndentLevel(): Int {
        var col = 0
        for (i in 0..<src.col) {
            when (src.currentLine[i]) {
                ' ' -> col++
                '\t' -> col = ((col % tabSize) + 1) * tabSize
                else -> break
            }
        }
        return col / 4
    }

    private fun extractUnorderedList(): BlockItem.ListItem {
        // it is called when current is a first char of a mark, so we save indent here:
        val level = currentIndentLevel()
        // skip mark and space
        src.advance(2)
        val start = src.pos()
        return BlockItem.ListItem(
            BlockItem.ListType.Bulleted, level, null, parseContent(), MarkupPlacement(
                listOf(), // we don't treat list mark as a special character in cyntax coloring
                src.rangeToCurrent(start)
            )
        )
    }

    private fun readPositiveInt(): Int? {
        var result: Int? = null
        while (src.current?.isDigit() == true) {
            result = (result ?: 0) * 10 + (src.current!! - '0')
            src.advance()
        }
        return result
    }

    private fun tryExtractOrderedList(): BlockItem.ListItem? =
        src.mark {
            val start = src.pos()
            val level = currentIndentLevel()
            val block = readPositiveInt()?.let { seq ->
                if (src.current == '.') {
                    val n = lastBlock?.let {
                        if (it is BlockItem.ListItem && it.type == BlockItem.ListType.Ordered)
                            it.number
                        else null
                    } ?: seq
                    src.advance()
                    BlockItem.ListItem(
                        BlockItem.ListType.Ordered, level, n, parseContent(),
                        MarkupPlacement(
                            listOf(),
                            src.rangeToCurrent(start)
                        )
                    )
                } else null
            }
            if (block == null) rewind()
            block
        }

    private fun readCodeBlock(start: Pos, ending: String): BlockItem.Code? =
        src.mark {
            val lang = src.readToEndOfLine()
            val bodyStart = src.pos()
            val mup1 = start..<bodyStart
            var ok = true
            val acc = StringBuilder()
            var mup2: OpenEndRange<Pos>? = null
            do {
                val p0 = src.pos()
                val line = src.readToEndOfLine()
                if (line.trimEnd() == ending) {
                    mup2 = p0..<src.pos()
                    break
                }
                acc.appendLine(line)
                if (src.end) {
                    addError("unterminated block $ending")
                    rewind()
                    ok = false
                    break
                }
            } while (!src.end)
            if (ok)
                BlockItem.Code(
                    acc.dropLast(1).toString(), lang, MarkupPlacement(
                        listOf(mup1, mup2!!),
                        bodyStart..<mup2.start
                    )
                )
            else null
        }


    private fun CharSource.Mark.addError(text: String) {
        _errors += createError(text)
    }


    private fun clearStyleModifiers() {
        allModifiers.forEach { it.clear() }
    }


    private fun parseContent(): List<InlineItem> {
        var start = src.pos()
        clearStyleModifiers()
        markupRanges.clear()

        val acc = StringBuilder()
        val result = mutableListOf<InlineItem>()

        fun flush() {
            if (acc.isNotEmpty()) {
                val text = acc.toString()
                val p = MarkupPlacement(markupRanges.toList(), start..<src.pos())
                result += if (symbol)
                    InlineItem.Code(text, p)
                else
                    InlineItem.Text(text, bold, italic, strikeThrough, p)

                acc.clear()
                markupRanges.clear()
            }
        }

        allModifiers.forEach {
            it.beforeChange = {
                if (it.token == null) {
                    // in the beginning: flush existing accumulator
                    flush()
                    addRange(it)
                } else {
                    // in the end: add the range and flush
                    addRange(it)
                    flush()
                }
                start = src.pos()
            }
            it.afterChange = {
                start = src.pos()
            }
        }

        loop@ while (!src.end) {

            if (src.eol) {
                src.advance()
                acc.append(' ')
                if (src.skipSpacesToEnd()) break
            }

            when (src.current) {
                '\\' -> { // escapes
                    when (val n = src.nextInLine()) {
                        '\\', '[', ']' -> {
                            acc.append(n)
                            src.advance(2)
                            continue@loop
                        }
                    }
                }

                '-' -> { // dashes
                    val ch = src.mark {
                        if (src.nextInLine() == '-') {
                            src.advance(2)
                            if (src.current == '-' && src.nextInLine()?.isWhitespace() == true) {
                                src.advance()
                                '\u2014'
                            } else if (src.nextInLine()?.isWhitespace() == true)
                                '\u2013'
                            else {
                                rewind()
                                null
                            }
                        } else null
                    }
                    if (ch != null) {
                        acc.append(ch)
                        continue@loop
                    }
                }

                '[' -> {
                    val r = extractRef()
                    if (r != null) {
                        // if the ref was actually extracted, flush the acc;
                        // it could be filled with plain text, but only if the
                        // valid ref was extracted:
                        flush()
                        result += InlineItem.Link(r)
                        start = src.pos()
                        continue@loop
                    }
                }
            }

            for (m in allModifiers)
                if (m.check(src) != null) {
                    if (m.token == null)
                        start = src.pos()
                    continue@loop
                }

            // todo: images

            acc.append(src.current)
            src.advance()
        }
        flush()
        // free closures stored in modifiers:
        clearStyleModifiers()
        return result
    }

    /**
     * Extract ref from current position
     */
    private fun extractRef(): Ref? {
        return src.mark {
            src.readBracedInLine('[', ']')?.let { name ->
                // could be a footnote, inline or even external:
                val bodyRange = startPosition + 1..<src.pos() - 1
                val markupRanges = mutableListOf(
                    startPosition..<startPosition + 1,
                    src.pos() - 1..<src.pos()
                )
                val pos2 = src.pos()
                when (src.current) {
                    '(' -> {
                        // inline
                        src.readBracedInLine('(', ')')?.let { source ->
                            extractLinkAndTitle(source).let {
                                Ref(
                                    name, it.first, it.second, Ref.Type.Inline,
                                    MarkupPlacement(
                                        markupRanges + (pos2..<src.pos()),
                                        bodyRange,
                                    )
                                )
                            }
                        }
                    }

                    '[' -> {
                        // footnote with different id
                        src.readBracedInLine('[', ']')?.let {
                            footnotes[it]?.copy(
                                name = name, placement = MarkupPlacement(
                                    markupRanges + (pos2..<src.pos()),
                                    bodyRange
                                )
                            )
                        }
                    }

                    else -> {
                        footnotes[name] ?: linkResolver.invoke(name)
                            ?.let { extractLinkAndTitle(it) }
                            ?.let {
                                Ref(
                                    name, it.first, it.second, Ref.Type.External, MarkupPlacement(
                                        markupRanges, bodyRange
                                    )
                                )
                            } ?: run {
                            rewind()
                            null
                        }
                    }
                }
            }
        }
    }

    /**
     * Extract link part of the reference, e.g., `url` or `url "title" from
     * a given string.`
     * @return link to title pair or null if not found
     */
    private fun extractLinkAndTitle(s: String): Pair<String, String?> {
        val parts = s.split(' ')
        val link: String
        val title: String?
        if (parts.size == 2 && parts[1].length >= 2 && parts[1][0] == '"' && parts[1].last() == '"') {
            link = parts[0]
            title = parts[1].let { it.slice(1..<it.length - 1) }
        } else {
            link = s
            title = null
        }
        return link to title
    }

    companion object {
        val headings = (1..7).map { "#".repeat(it) + ' ' }.toTypedArray()
    }
}


