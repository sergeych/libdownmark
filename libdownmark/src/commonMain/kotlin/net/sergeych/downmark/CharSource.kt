package net.sergeych.downmark

class CharSource(private val text: String) {

    inner class Mark() {
        val startPosition = pos()

        fun rewind() {
            resetTo(startPosition)
        }

        fun createError(text: String): SyntaxError =
            SyntaxError(startPosition, text)

    }

    fun createMark(): Mark = Mark()

    val lines = text.lines().let {
        if (it.isEmpty()) listOf("") else it
    }

    internal var currentLine = lines[0]

    private var row = 0

    var col = 0
        private set

    private var _currentPos: Pos? = null

    fun pos(): Pos {
        _currentPos?.let { return it }
        return posAt(row, col).also { _currentPos = it }
    }

//    fun back(steps: Int = 1) {
//        for (i in 0..steps) {
//            if (col == 0) {
//                if (row == 0) throw IndexOutOfBoundsException("back below first character")
//                row--
//                col = lines[row].length
//            }
//        }
//        sync()
//    }


    fun resetTo(p: Pos) {
        val cp = pos()
        if (cp != p) {
            row = p.row
            col = p.col
            sync()
        }
    }

    var current: Char? = null
        private set

    val eol: Boolean get() = current == '\n'

    var end = true
        private set

    fun skipLine() {
        if (!end) {
            row++
            col = 0
            sync()
        }
    }

    /**
     * return the current char and advance
     */
    fun pop(): Char? = current?.also { advance() }


    fun advance(steps: Int = 1) {
        val dir: Int
        val limit: Int
        if (steps > 0) {
            dir = 1
            limit = steps
        } else {
            dir = -1
            limit = -steps
        }


        for (i in 0..<limit) {
            if (dir > 0) {
                if (end) throw IndexOutOfBoundsException("advance past the end")
                if (current == '\n') {
                    row++
                    col = 0
                    if (row >= lines.size) end = true
                } else col++
            } else {
                if (col == 0) {
                    if (row == 0) throw IndexOutOfBoundsException("advance back before the start")
                    col = lines[--row].length
                } else col--
            }
        }
        sync()
    }

    private fun sync() {
        _currentPos = null
        end = row >= lines.size
        if (end)
            current = null
        else {
            currentLine = lines[row]
            current = if (col >= currentLine.length) '\n' else currentLine[col]
        }
    }

    /**
     * Skip spaces in current line only
     */
    fun skipWs(): CharSource {
        while (current?.let { it in " \t" } == true) advance()
        return this
    }

    inline fun <reified T> mark(f: Mark.() -> T): T = with(createMark()) { f() }

    /**
     * Check that source matches any <prefix><non-space>, in which case
     * the source is advanced to the position right after the prefix
     * @return true if prefix-non-space match was found
     */
    fun getStart(vararg prefixes: String): Boolean = mark {
        if (expectAny(*prefixes) == null)
            false
        else {
            // now the next char should exist (it does) and be non-space:
            val ok = current?.let { c ->
                c !in spaces && prefixes.all { c !in it }
            } ?: false
            if (ok)
                true
            else {
                rewind()
                false
            }
        }
    }

    fun isAtStart() = pos().offset == 0


    /**
     * Style span start prefix detection. Prev char should not exist or be a space, next char
     * should not be a space. The current position is not changed.
     * @return found prefix or null
     */
    fun isStartOf(vararg prefixes: String): String? {
        // 1. we at start or after a space:
        if( col == 0 || currentLine[col-1].isWhitespace()) {
            // 2. there is a prefix
            for( x in prefixes ) {
                if(currentLine.startsWith(x, col)) {
                    // found it
                    return x
                }
            }
        }
        return null
    }

    fun findEnd(sample: String, limit: Pos): Pos? = mark {
        var found: Pos? = null
        while (pos() < limit) {
            if (currentLine.startsWith(sample, col)) {
                if( currentLine.length > col+sample.length) {
                    val next = currentLine[col+sample.length]
                    if( next.isWhitespace() || next == '.') {
                        found = pos()
                        break
                    }
                }
            }
            advance()
        }
        found.also {
            rewind()
        }
    }


    /**
     * If the expected value is at the current position, advance past it.
     * @return true if the expected value was read, false otherwise
     */
    fun expect(pattern: String): Boolean {
        for (i in 0..<pattern.length) {
            val k = col + i
            if (k >= currentLine.length || currentLine[k] != pattern[i])
                return false
        }
        col += pattern.length
        sync()
        return true
    }

    fun expectAny(vararg patterns: String): String? {
        for (p in patterns) {
            if (expect(p)) return p
        }
        return null
    }


    /**
     * Retrieve text from current position to the end of line. Line ending
     * will not be added to the returned string.
     * After the call current position is on the first char of the next line
     * or [end]
     * @return the retrieved text which could be empty.
     */
    fun readToEndOfLine(): String {
        return if (eol) {
            advance()
            ""
        } else {
            currentLine.substring(col).also {
                row++
                col = 0
                sync()
            }
        }
    }

    /**
     * Check that this is [eol] or characters until [eol] are all spaces consuming it and line
     * ending.
     *
     * After this call, the [current] is either null or points to the first char if the first non-empty line
     *
     * @return true if there was a consumed blank line ahead
     */
    fun skipSpacesToEnd(): Boolean {
        while (!eol && !end) {
            if (current?.isWhitespace() != true) return false
            advance()
        }
        return true
    }

    /**
     * Check that from the current position to endo of line or end of data
     * there are only spaces of there are no characters.
     * __Does not advance current position__.
     */
    fun isBlankToEndOfLine(): Boolean {
        if (end || eol) return true
        var c = col
        while (c < currentLine.length)
            if (currentLine[c++].isWhitespace() != true) return false
        return true
    }

    fun currentLineIndent(tabSize: Int = 2): Int = currentLine.indentExpandingTabs(tabSize)


    /**
     * Get previous char in the current line or null if there is no such char (start of line)
     */
    fun prevInLine(): Char? {
        if (end || col == 0) return null
        return currentLine[col - 1]
    }

    /**
     * return next char in the same line or null if it is line ending.
     */
    fun nextInLine(): Char? = if (col + 1 >= currentLine.length) null else currentLine[col + 1]

    private val lineSize: List<Int> by lazy { lines.map { it.length + 1 } }

    private val offsetOfLine: List<Int> by lazy {
        var offset = 0
        val result = ArrayList<Int>(lineSize.size)

        for (s in lineSize) {
            result.add(offset)
            offset += s
        }
        result.add(offset)
        result
    }

    fun posAt(r: Int, c: Int): Pos {
        require(r >= 0 && r <= lines.size, { "row not in document bounds" })
        if (r == lines.size)
            require(c == 0, { "after the document end" })
        else {
            require(
                c >= 0 && c <= lines[r].length,
                { "ourside line $r boundaries ($c should be in ${0..lines[r].length}" })
        }
        return Pos(r, c, offsetOfLine[r] + c)
    }

    fun posAt(from: Pos, offset: Int): Pos {
        // Actually, it could be faster to navigate from pos, bit not always
        return posAt(from.offset + offset)
    }

    fun posAt(offset: Int): Pos {
        var r = 0
        while (r < lines.size - 1 && offsetOfLine[r + 1] < offset) r++
        val c = offset - offsetOfLine[r]
        return Pos(r, c, offset)
    }

    @Suppress("unused")
    fun charAt(pos: Pos): Char = text[pos.offset]

    @Suppress("unused")
    fun slice(range: OpenEndRange<Pos>): String = text.slice(range.start.offset..<range.endExclusive.offset)

    @Suppress("unused")
    fun slice(range: ClosedRange<Pos>): String = text.slice(range.start.offset..<range.endInclusive.offset)


    /**
     * Find the first occurrence ot the pattern inside a block (in markdown sense). Does not
     * change current position.
     * Note that pattern could not be spanned to several lines, it should fit entirely in a line
     */
    fun findInBlock(pattern: String): Pos? {
        var offset = col
        var r = row
        do {
            val i = lines[r].indexOf(pattern, offset)
            if (i >= 0) return posAt(r, i)
            r++
            offset = 0
            if (r < lines.size) {
                if (lines[r].isEmpty() || lines[r][0].isWhitespace())
                    break
            } else break
        } while (true)
        return null
    }

    /**
     * Read text from open to closed char (e.g. braces), optionally favoring escape character
     * _only con `close` char!_ and _only in the current line_.
     * E.g. [current] should `== open`.
     * The current position is advanced to the character next after `end` if found, or left intact.
     * @return found string or null
     */
    fun readBracedInLine(open: Char, close: Char, favorEscapes: Char? = '\\'): String? {
        if (current != open) return null
        return mark {
            val c0 = col + 1
            while (true) {
                advance()
                if (eol) break
                if (favorEscapes == current && nextInLine() == close) {
                    advance()
                    continue
                }
                if (current == close) {
                    return@mark currentLine.substring(c0, col).also { advance() }
                }
            }
            rewind()
            null
        }
    }

    fun rangeToCurrent(start: Pos): OpenEndRange<Pos> = start..<pos()

    init {
        if (text.isEmpty())
            throw IllegalArgumentException("can't create CharSource on empty line")
        sync()
    }

    companion object {
        val spaces = " \t".toSet()
    }

}