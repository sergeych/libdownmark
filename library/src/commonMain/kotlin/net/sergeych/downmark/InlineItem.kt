package net.sergeych.downmark

sealed class InlineItem: MarkupItem {

    data class Text(
        val text: String,
        val bold: Boolean = false,
        val italics: Boolean = false,
        val strikeThrough: Boolean = false,
        override val placement: MarkupPlacement
    ): InlineItem() {

        override fun toString(): String {
            val fmt = StringBuilder()
            if(bold) fmt.append('B')
            if(italics) fmt.append('I')
            if( fmt.isNotEmpty() ) fmt.append(':')
            return "Text($fmt$text)"
        }
    }

    data class Code(val text: String, override val placement: MarkupPlacement): InlineItem()

    data class Link(val ref: Ref, override val placement: MarkupPlacement): InlineItem()

    data class Image(val ref: Ref, override val placement: MarkupPlacement): InlineItem()
}