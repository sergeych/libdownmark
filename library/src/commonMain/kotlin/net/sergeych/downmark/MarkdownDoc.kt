package net.sergeych.downmark

class MarkdownDoc internal constructor(
    val body: List<BlockItem>,
    val errors: List<SyntaxError>) {

    inline fun <reified T: BlockItem>blockAt(index: Int): T = body[index] as T

    companion object {

        /**
         * Create a Markdown document parsing the text. See [Parser] for details
         */
        operator fun invoke(
            text: String,
            linkResolver: (String)->String? = {null}
        ): MarkdownDoc = Parser(text, linkResolver).parse()
    }
}