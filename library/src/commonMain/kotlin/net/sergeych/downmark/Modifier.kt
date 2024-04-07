package net.sergeych.downmark

internal class Modifier(vararg val tokens: String, val onChange: (Boolean) -> Unit) {

    val active: Boolean get() = token != null

    /**
     * Currently active token or null. Note that if [active] is true, [token] is always
     * not null.
     */
    var token: String? = null
        private set

    var beforeChange: (() -> Unit)? = null

    fun clear() {
        token = null
        onChange(false)
        beforeChange = null
    }

    private fun update(newToken: String?) {
        require(newToken != token)
        beforeChange?.invoke()
        token = newToken
        onChange(active)
    }

    /**
     * Check that current position is open/close token for the
     * modifier, remove it if present and changes [active] and [token] accordingly
     * and calls [beforeChange] then [onChange].
     * @return null if state is not changed, otherwise true on start and false on end
     */
    fun check(src: CharSource): Boolean? {
        // Check that current pos matches any token
        return src.mark {
            val prevChar = src.prevInLine()
            val t = src.expectAny(*tokens) ?: return@mark null
            if (token == null) {
                if (src.findInBlock(t) != null && prevChar?.isLetterOrDigit() != true) {
                    update(t)
                    true
                } else {
                    rewind()
                    null
                }
            } else {
                if( t == token && src.current?.isLetterOrDigit() == false ) {
                    update(null)
                    false
                }
                else {
                    rewind()
                    null
                }
            }
        }
    }

}