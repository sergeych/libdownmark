package net.sergeych.downmark


class Parser(text: String) {

    private val src = CharSource(text)

    private var _errors = mutableListOf<SyntaxError>()
    val errors: List<SyntaxError> = _errors

    private val footnotes = mutableMapOf<String, Ref>()
    private var listLevel = 0

    private var italic = false
    private var bold = false
    private var strikeThrough = false
    private var symbol = false

    private val mItalic = Modifier("_", "*") { italic = it }
    private val mBold = Modifier("__", "**") { bold = it }
    private val mMBoldItalic = Modifier("___", "***") { bold = it; italic = it }
    private val mStrikeThrough = Modifier("~~") { strikeThrough = it }
    private val mSymbol = Modifier("`") { symbol = it }

    private val allModifiers = arrayOf(mItalic, mBold, mBold, mSymbol, mSymbol)


    val lines: List<String> = src.lines

    fun parse(): MarkdownDoc {
        scanReferences()
        val result = mutableListOf<BlockItem>()
        while (true) {
            parseBlock(src)?.let { result += it } ?: break
        }
        return MarkdownDoc(result, errors)
    }

    /**
     * First pass: detect all footnote references
     */
    fun scanReferences() {
        src.mark {
            while (!src.end) {
                val name = src.readBracedInLine('[', ']')
                if (name == null || src.current != ':') {
                    src.skipLine(); continue
                }
                src.advance()
                src.skipWs()
                val s = src.readToEndOfLine()
                if (s.isBlank()) {
                    addError("malformed footnote")
                } else {
                    val (link, title) = extractLinkAndTitle(s)
                    footnotes[name] = Ref(name, link, title, true)
                }
            }
            rewind()
        }
    }

    private fun parseBlock(src: CharSource): BlockItem? {

        while (src.isBlankLine() && !src.end) src.skipLine()
        if (src.end) return null

        if (src.col != 0) throw RuntimeException("parseBlock must be called from line start: ${src.pos()}")

        if (src.current == '#') {
            src.expectAny(*headings)?.let {
                return BlockItem.Heading(it.length - 1, parseContent())
            }
        }

        // todo: lists

        if (src.current == '~' || src.current == '`') {
            src.expectAny("~~~", "```")?.let {
                return readCodeBlock(it)
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
            if (found) return BlockItem.HorizontalLine

            // todo: dashed list level 0
        }

        return when (src.current) {
            ' ', '\t' -> {
                src.skipWs()
                BlockItem.Paragraph(
                    if (listLevel > 0) listLevel else 1,
                    parseContent()
                )
            }

            else -> BlockItem.Paragraph(0, parseContent())
        }
    }

    fun readCodeBlock(ending: String): BlockItem.Code =
        src.mark {
            val lang = src.readToEndOfLine()
            val acc = StringBuilder()
            do {
                val line = src.readToEndOfLine()
                if (line == ending) break
                acc.appendLine(line)
                if (src.end) addError("unterminated block $ending")
            } while (!src.end)
            BlockItem.Code(acc.toString(), lang)
        }


    fun CharSource.Mark.addError(text: String) {
        _errors += createError(text)
    }


    private fun clearStyleModifiers() {
        allModifiers.forEach { it.clear() }
    }

    private fun parseContent(): Content {
        clearStyleModifiers()

        val acc = StringBuilder()
        val result = mutableListOf<InlineItem>()

        fun flush() {
            if (acc.isNotEmpty()) {
                result += if (symbol)
                    InlineItem.Code(acc.toString())
                else
                    InlineItem.Text(acc.toString(), bold, italic, strikeThrough)
                acc.clear()
            }
        }

        allModifiers.forEach { it.beforeChange = { flush() } }

        loop@ while (!src.end) {

            if (src.eol) {
                src.advance()
                if (src.isBlankLine()) break
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
                    if (src.nextInLine() == '-') {
                        src.advance(2)
                        if (src.current == '-') {
                            acc.append('\u2014')
                            src.advance()
                        } else {
                            acc.append('\u2013')
                            continue@loop
                        }
                    }
                }

                '[' -> {
                    val r = extractRef()
                    if (r != null) {
                        flush()
                        result += InlineItem.Link(r)
                        continue@loop
                    }
                }
            }

            for (m in allModifiers)
                if (m.check(src) != null) continue@loop

            // todo: links
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
     * Exctract ref from current position
     */
    private fun extractRef(): Ref? {
        return src.mark {
            src.readBracedInLine('[', ']')?.let { name ->
                // could be a footnote or inline:
                when (src.current) {
                    '(' -> {
                        // inline
                        src.readBracedInLine('(', ')')?.let {
                            extractLinkAndTitle(it).let {
                                Ref(name, it.first, it.second, false)
                            }
                        }
                    }

                    '[' -> {
                        // footnote with different id
                        src.readBracedInLine('[', ']')?.let {
                            footnotes[it]?.copy(name = name)
                        }
                    }

                    else -> {
                        footnotes[name] ?: run {
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
        var link: String
        var title: String?
        if (parts.size == 2 && parts[1][0] == '"' && parts[1].last() == '"') {
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


