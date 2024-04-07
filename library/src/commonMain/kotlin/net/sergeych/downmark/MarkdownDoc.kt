package net.sergeych.downmark

class MarkdownDoc(
    val body: List<BlockItem>,
    val errors: List<SyntaxError>) {

    inline fun <reified T: BlockItem>blockAt(index: Int): T = body[index] as T

    companion object {
        operator fun invoke(text: String): MarkdownDoc = Parser(text).parse()
    }
}