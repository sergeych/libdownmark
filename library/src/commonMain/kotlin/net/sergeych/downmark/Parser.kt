package net.sergeych.downmark

/**
 * Create a Markdown parser that will parse text and use optional
 * external links resolver.
 *
 * @param text Markdown text to parse
 * @param linkResolver callback to resolve external links.
 */
class Parser(
    text: String,
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

    private val src = CharSource(text)

    private var _errors = mutableListOf<SyntaxError>()
    private val errors: List<SyntaxError> = _errors

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

    private val allModifiers = arrayOf(mItalic, mBold, mMBoldItalic, mStrikeThrough, mSymbol)

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
    private fun scanReferences() {
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
                    footnotes[name] = Ref(name, link, title, Ref.Type.Footnote)
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

    private fun readCodeBlock(ending: String): BlockItem.Code =
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


    private fun CharSource.Mark.addError(text: String) {
        _errors += createError(text)
    }


    private fun clearStyleModifiers() {
        allModifiers.forEach { it.clear() }
    }

    private fun parseContent(): Content {
        clearStyleModifiers()

        val acc = StringBuilder()
        val result = mutableListOf<InlineItem>()

        fun flush(trimLastSpace: Boolean=false) {
            if (acc.isNotEmpty()) {
                val text = acc.toString().let {
                    if( trimLastSpace ) it.trimEnd()
                    else it
                }
                result += if (symbol)
                    InlineItem.Code(text)
                else
                    InlineItem.Text(text, bold, italic, strikeThrough)
                acc.clear()
            }
        }

        allModifiers.forEach { it.beforeChange = { flush() } }

        loop@ while (!src.end) {

            if (src.eol) {
                src.advance()
                acc.append(' ')
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
                    val ch = src.mark {
                        if (src.nextInLine() == '-') {
                            src.advance(2)
                            if (src.current == '-' && src.nextInLine()?.isWhitespace() == true) {
                                src.advance()
                                '\u2014'
                            } else if( src.nextInLine()?.isWhitespace() == true)
                                '\u2013'
                            else {
                                rewind()
                                null
                            }
                        }
                        else null
                    }
                    if( ch != null ) {
                        acc.append(ch)
                        continue@loop
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

            // todo: images

            acc.append(src.current)
            src.advance()
        }
        flush(trimLastSpace = true)
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
                when (src.current) {
                    '(' -> {
                        // inline
                        src.readBracedInLine('(', ')')?.let { source ->
                            extractLinkAndTitle(source).let {
                                Ref(name, it.first, it.second, Ref.Type.Inline)
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
                        footnotes[name] ?: linkResolver.invoke(name)
                            ?.let { extractLinkAndTitle(it) }
                            ?.let {
                                Ref(name, it.first, it.second, Ref.Type.External)
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


