package net.sergeych.downmark

class CharSource(text: String) {

    inner class Mark() {
        val resetPos = pos()

        fun rewind() {
            resetTo(resetPos)
        }

        fun createError(text: String): SyntaxError =
            SyntaxError(resetPos, text)

    }

    fun createMark(): Mark = Mark()

    val lines = text.lines().let {
        if (it.isEmpty()) listOf("") else it
    }

    private var currentLine = lines[0]

    private var row = 0

    var col = 0
        private set

    fun pos(): Pos = makePos(row, col)

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

    fun advance(steps: Int = 1) {
        for (i in 0..<steps) {
            if (end) throw IndexOutOfBoundsException("advance past the end")
            if (current == '\n') {
                row++
                col = 0
                if (row >= lines.size) end = true
            } else col++
        }
        sync()
    }

    private fun sync() {
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

    fun <T> mark(f: Mark.() -> T): T = with(createMark()) { f() }

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
        return if (eol) "" else {
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
    fun isBlankLine(): Boolean {
        while (!eol && !end) {
            if (current?.isWhitespace() != true) return false
            advance()
        }
        return true
    }

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

    private val lineSize: List<Int> by lazy { lines.map { it.length} }

    private val offsetOfLine: List<Int> by lazy {
        var offset = 0
        val result = ArrayList<Int>(lineSize.size)

        for( s in lineSize ) {
            result.add(offset)
            offset += s
        }
        result.add(offset)
        result
    }

    fun makePos(r: Int,c: Int): Pos =
        Pos(r, c, offsetOfLine[r] + c )


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
            if (i >= 0) return makePos(r, i)
            r++
            offset = 0
            if (r < lines.size) {
                if (lines[r][0].isWhitespace())
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
        if( current != open ) return null
        return mark {
            val c0 = col+1
            while(true) {
                advance()
                if( eol ) break
                if( favorEscapes == current && nextInLine() == close ) {
                    advance()
                    continue
                }
                if( current == close ) {
                    return@mark currentLine.substring(c0, col).also { advance() }
                }
            }
            rewind()
            null
        }
    }

    init {
        if (text.isEmpty())
            throw IllegalArgumentException("can't create CharSource on empty line")
        sync()
    }

    companion object {
        val spaces = " \t".toSet()
    }

}