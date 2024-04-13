package net.sergeych.downmark

/**
 * Position in a source text, row, col and offset. This simplifies syntax coloring and
 * source navigation.
 */
data class Pos(val row: Int, val col: Int,val offset: Int) : Comparable<Pos> {
    override fun compareTo(other: Pos): Int = when {
        row < other.row -> -1
        row > other.row -> +1
        col < other.col -> -1
        col > other.col -> +1
        else -> 0
    }

}