package net.sergeych.downmark

/**
 * Reference is a way a Markdown document specifies other objects.
 * References are different from links which are visible items [InlineItem.Link],
 * and are used by them and also by [InlineItem.Image].
 *
 * Reference carries markdown-specific information define some other object
 * external to the document.
 *
 * References could be if different types, see [Ref.Type].
 */
data class Ref(
    val name: String,
    val link: String,
    val title: String? = null,
    val type: Type,
    val placement: MarkupPlacement
) {
    /**
     * Link types, to properly convert back to Markdown or other formats, see
     * [Type.Inline], [Type.Footnote] and [Type.External].
     */
    enum class Type {
        /**
         * Inline link, the link follows the link name `[name](link)`
         */
        Inline,

        /**
         * Footnote, the link is placed at the end of the document:
         * ~~~
         * Some [link_namee] here.
         *
         * [link_name]: https://8-rays.dev
         */
        Footnote,

        /**
         * External link, the link itself provided by [Parser.linkResolver] and
         * should not be defined in the document, e.g., `[link_name]` not defined
         * in the document.
         */
        @Suppress("KDocUnresolvedReference")
        External
    }
}