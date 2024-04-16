package net.sergeych.downmark

class MarkdownDoc internal constructor(
    val text: String,
    val body: List<BlockItem>,
    val errors: List<SyntaxError>) {

    @Suppress("unused")
    val markupItems: List<MarkupItem> by lazy {
        body + body.map { it.content }.flatten()
    }

    /**
     * Get a block of expected type. Throws exception if the block type is different,
     * for testing mainly.
     * We recommend access [body] isntead.
     */
    inline fun <reified T: BlockItem>blockAt(index: Int): T = body[index] as T

    companion object {

        /**
         * Create a Markdown document parsing the text. See [Parser] for details
         */
        operator fun invoke(
            text: String,
            linkResolver: (String)->String? = {null}
        ): MarkdownDoc = Parser(text, linkResolver=linkResolver).parse()
    }
}