package net.sergeych.downmark

sealed class InlineItem {

    data class Text(
        val text: String,
        val bold: Boolean = false,
        val italics: Boolean = false,
        val strikeThrough: Boolean = false
    ): InlineItem() {

        override fun toString(): String {
            val fmt = StringBuilder()
            if(bold) fmt.append('B')
            if(italics) fmt.append('I')
            if( fmt.isNotEmpty() ) fmt.append(':')
            return "Text($fmt$text)"
        }
    }

    data class Code(
        val text: String
    ): InlineItem()

    data class Link(val ref: Ref): InlineItem()

    data class Image(val ref: Ref): InlineItem()
}