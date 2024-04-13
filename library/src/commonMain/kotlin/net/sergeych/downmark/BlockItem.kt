package net.sergeych.downmark

typealias Row = List<Content>

@Suppress("unused")
sealed class BlockItem : MarkupItem {

    data class Paragraph(val indent: Int, val content: Content, override val placement: MarkupPlacement) : BlockItem()
    class Heading(val level: Int, val content: Content, override val placement: MarkupPlacement) : BlockItem()

    enum class ListType { Dashed, Bulleted, Numbered }

    class ListItem(
        val type: ListType,
        val level: Int,
        val content: Content, override val placement: MarkupPlacement,
    ) : BlockItem()

    data class Quote(val content: Content, override val placement: MarkupPlacement) : BlockItem()

    data class Code(
        val text: String,
        val land: String? = null,
        override val placement: MarkupPlacement,
    ) : BlockItem()

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
    }

    class Footnote(ref: Ref, override val placement: MarkupPlacement) : BlockItem()

}