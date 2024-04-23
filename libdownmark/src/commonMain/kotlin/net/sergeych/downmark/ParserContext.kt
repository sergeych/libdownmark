package net.sergeych.downmark

class ParserContext(
    val src: CharSource,
    /**
     * Column of the _beginning_ of the current list mark. Note that if there
     * os no active list, indirection should be 0
     */
    val ibase: Int = 0,
    /**
     * Base of the text block, e.g. column next after '1. ', '* ' or whatever list type
     * is in u  se.
     */
    val bbase: Int = 0,
    /**
     * Current list indirection level. 0 means no list, 1 - first-level list, etc.
     */
    val level: Int = 0,

    /**
     * IF the list is being parsed, list type
     */
    val listType: BlockItem.ListType? = null,

    /**
     * If the numbered list is being parsed, current number at this level
     * of indirection
     */
    val currentNumber: Int? = null,

    /**
     * Unless it is a root level state, previous list parser context. Null means
     * there is no list
     */
    val prevContext: ParserContext? = null,
) {

    constructor(text: String, ibase: Int = 0, bbase: Int = 0) : this(CharSource(text), ibase, bbase)

    init {
        // Sanity check
        if (listType != null || currentNumber != null || prevContext != null) {
            require(listType != null)
            require(prevContext != null)
        }
    }

    val isInList: Boolean = listType != null

    /**
     * Parse the block at the current indirection level,
     * return null and leave src at the start of line if there
     * is no block on the current indirection level
     */
    fun parseBlock(): Sequence<BlockItem> {
        require(src.col == 0) { "parseBlock should be called from col 0" }
        return sequence {
            while (!src.end) {
                val m = src.createMark()
                src.skipWs()
                if (src.col >= bbase + 4) {
                    // top priority: non-fenced code block
                    m.rewind()
                    yield(readNonFencedCodeBlock())
                    continue
                }

                if (src.col < ibase) break

                if (src.col < bbase) {
                    // should be a line item same level
                }

                // start at bbase:
                // todo: fenced block
                // todo: list
                // todo: quite
                // todo: heading
                // todo: line

                // otherwise, it's a paragraph
                yieldAll(readOther())
            }
        }
    }

    /**
     * Read content from current pos which should be the first pos of the content_
     * until the end of the block (somehow) and parse it.
     */
    private fun readContent(): List<InlineItem> {
        val result = mutableListOf<BlockItem>()
        var start = src.pos()
        val acc = StringBuilder(src.readToEndOfLine())

        /*
         * The logic is:
         *
         * 1. addCurrent line to style processing
         * 2. get next line.
         * 3. if it is the end of some type, return
         *    collected styles
         *
         *    otherwise, add the line to style processing.
         */

        fun flush() {
            if (acc.isNotEmpty()) {
                result += BlockItem.Paragraph(
                    level, parseInlines(
                        acc.toString()), MarkupPlacement(
                            listOf(), start..<src.pos()
                        )
                    )
                )
                acc.toString()
                start = src.pos()
            }
        }
        do {

            // paragraph ends:
            if (src.end) break
            if (src.isBlankToEndOfLine()) break
            // outdent text: end
            val indent = src.currentLineIndent()
            if (indent < bbase) break

            /* now it can be
                - Start of the list item, this level or +1, outtend already caused
                exit from the function (above).

                - All other is a continuation.
             */
            detectListStart()?.let { (i, t) ->
            }

        } while (true)
        return result
    }


    fun parseList(): Sequence<BlockItem.ListItem>? {
        return null
    }

    private fun detectListStart(): Pair<Int, BlockItem.ListType>? {
        TODO()
    }

    fun parseInlines(str: String): List<InlineItem> {
        TODO()
    }


    private fun readNonFencedCodeBlock(): BlockItem.Code {
        val start = src.pos()
        val minIndent = bbase + 4
        val acc = StringBuilder()
        do {
            val stop = src.mark {
                if (src.end) true
                else {
                    val line = src.readToEndOfLine().expandTabs(2)
                    if (line.isBlank()) {
                        acc.appendLine()
                        false
                    } else {
                        if (line.indentSpaces() >= minIndent) {
                            acc.appendLine(line.drop(minIndent))
                            false
                        } else {
                            rewind()
                            true
                        }
                    }
                }
            }
        } while (!stop)

        return BlockItem.Code(
            acc.toString(), null,
            level,
            MarkupPlacement(listOf(), start..<src.pos())
        )
    }
}



