package net.sergeych.downmark

class StyleProcessor(val src: CharSource, val end: Pos) {

    data class Style(
        val isItalic: Boolean = false,
        val isBold: Boolean = false,
        val isStrikeThrough: Boolean = false,
    )

    inner class MarkupDetector(vararg val symbols: String, val styler: (Style) -> Style) {
        var current: String? = null
            private set

        val markupPlacements = mutableListOf<OpenEndRange<Pos>>()

        fun detectChange(): Boolean {
            return if (current != null) {
                // detect end
                TODO()
            } else {
                // detect start
                src.isStartOf(*symbols)?.let { p ->
                    if (src.findEnd(p, end) != null) {
                        current = p
                        true
                    } else false
                } ?: false
            }
        }

    }

    private val content = mutableListOf<InlineItem>()

    private val markups = listOf<MarkupDetector>(
        MarkupDetector("***", "___") { it.copy(isItalic = true, isBold = true) },
        MarkupDetector("**", "__") { it.copy(isItalic = true, isBold = true) },
        MarkupDetector("*", "_") { it.copy(isItalic = true) },
    )

    private val acc = StringBuilder()
    private var startBody = src.pos()
    private var markpuPlacement = mutableListOf<OpenEndRange<Pos>>()

    private fun flush() {
//        if( acc.isNotEmpty() ) {
//            val mups = startMarkup?.let { it ..< src}
//        }
        TODO()
    }

    fun scanTo(end: Pos) {
        while (!src.eol && !src.end && src.pos() < end) {

            // Escaped character}
            if (src.current == '\\') {
                src.advance()
                src.pop()?.let { acc.append(it) }
                    ?: break
                continue
            }

            // style mark
            for (m in markups) {
                if (m.detectChange()) {
                    // start or stop?
                    // start: flush existing style & add markup & reset start
                    // stop: flush existing style & use markup to form a block & reset start
                }

            }
        }

    }


}