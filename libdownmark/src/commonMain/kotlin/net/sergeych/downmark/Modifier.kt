package net.sergeych.downmark

/**
 * User by parser to uniformly detect and parse style modifiers like `*italic*`, etc.
 * It checks that the
 */
internal class Modifier(
    /** list of tokens this modifier accepts.
     */
    private vararg val tokens: String,
    /**
     * The handler called every time [token] value is changed. There is  handler [beforeChange]
     * that, when set, will be called immediately before changing [token] value
     */
    val onChange: Modifier.(Boolean) -> Unit,
) {
    /**
     * Current active, detected token or null. It is alwaus null or one of [tokens]
     */
    var token: String? = null
        private set

    var currentTokenLength: Int = 0
        private set


    /**
     * Dynamically updatable handler that will invoke _before_ [token] will be changed. See
     * its current value: if it is null, then it will be set to some non-null value, otherwise
     * cleared to null.
     * Note tha [onChange] will be changed _after_ it when [token] is set to new value.
     */
    var beforeChange: (() -> Unit)? = null
    var afterChange: (() -> Unit)? = null

    fun clear() {
        token = null
        onChange(false)
        beforeChange = null
    }

    private fun update(src: CharSource, newToken: String?) {
        require(newToken != token)
        currentTokenLength = (newToken ?: token)?.length ?: 0
        src.mark {
            src.advance(-currentTokenLength)
            beforeChange?.invoke()
            rewind()
        }
        token = newToken
        onChange(token != null)
        afterChange?.invoke()
    }

    /**
     * Check that current position is open/close token for the
     * modifier.
     * The current position is checked using Markdown rules to contain proper
     * sequence before or after the token.
     *
     * If the token state change is detected, first [beforeChange] is called, then
     * [token] is set or cleared to proper value and [onChange] is called with a new state.
     *
     * Important to know is that when [beforeChange] is called the state is not yet changed.
     *
     * @param src source to detect and extract tokens from/
     * @return null if state is not changed, otherwise true on start, when [token] is set from null to
     *      corresponding value, and false on end, when [token] is set to null.
     */
    fun check(src: CharSource): Boolean? {
        // Check that current pos matches any token
        return src.mark {
            val prevChar = src.prevInLine()
            val t = src.expectAny(*tokens) ?: return@mark null
            if (token == null) {
                if (src.findInBlock(t) != null && prevChar?.isLetterOrDigit() != true) {
                    update(src, t)
                    true
                } else {
                    rewind()
                    null
                }
            } else {
                if (t == token && src.current?.isLetterOrDigit() == false) {
                    update(src, null)
                    false
                } else {
                    rewind()
                    null
                }
            }
        }
    }
}