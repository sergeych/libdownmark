package net.sergeych.downmark

typealias Row = List<List<InlineItem>>

@Suppress("unused")
sealed class BlockItem : MarkupItem {

    open val content: List<InlineItem> = listOf()
    open val indentLevel: Int? = null

    data class Paragraph(
        val indent: Int,
        override val content: List<InlineItem>,
        override val placement: MarkupPlacement
    ) :
        BlockItem()

    class Heading(
        val level: Int,
        override val content: List<InlineItem>,
        override val placement: MarkupPlacement
    ) : BlockItem()

    enum class ListType { Dashed, Bulleted, Ordered }

    class ListItem(
        val type: ListType,
        val level: Int,
        val number: Int?=null,
        override val content: List<InlineItem>,
        override val placement: MarkupPlacement,
    ) : BlockItem()

    data class Quote(
        override val content: List<InlineItem>,
        override val placement: MarkupPlacement
    ) : BlockItem()

    data class Code(
        val text: String,
        val land: String? = null,
        override val indentLevel: Int? = null,
        override val placement: MarkupPlacement,
    ) : BlockItem() {
        override val content = listOf(InlineItem.Code(text, placement))
    }

    data class HorizontalLine(override val placement: MarkupPlacement) : BlockItem()

    class Table(
        val header: Row,
        val alignment: List<Align>,
        val body: List<Row>,
        override val placement: MarkupPlacement,
    ) : BlockItem() {
        val cols = header.size
        val bodyRows = body.size

        enum class Align {
            Start, End, Center
        }

        override val content by lazy {
            header.flatten() + body.flatten().flatten()
        }
    }

    data class Footnote(val ref: Ref, override val placement: MarkupPlacement) : BlockItem()

}