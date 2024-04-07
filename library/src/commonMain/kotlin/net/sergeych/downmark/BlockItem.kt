package net.sergeych.downmark

typealias Row = List<Content>

sealed class BlockItem() {

    data class Paragraph(val indent: Int,val content: Content): BlockItem()
    class Heading(val level: Int,val content: Content): BlockItem()

    enum class ListType { Dashed, Bulleted, Numbered }

    class ListItem(
        val type: ListType,
        val level: Int,
        val content: Content): BlockItem()

    data class Quote(val content: Content): BlockItem()

    data class Code(val text: String,val land: String?=null): BlockItem()

    object HorizontalLine: BlockItem()

    class Table(
        val header: Row,
        val alignemnt: List<Align>,
        val body: List<Row>
    ): BlockItem() {
        val cols = header.size
        val bodyRows = body.size

        enum class Align {
            Start, End, Center
        }
    }

    class Footnote(ref: Ref): BlockItem()

}