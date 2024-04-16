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

    @Suppress("unused")
    fun move(src: CharSource, steps: Int): Pos = src.posAt(this, steps)

    operator fun plus(steps: Int) = Pos(row,col+steps,offset+steps)

    operator fun minus(steps: Int): Pos {
        require(col - steps >= 0)
        return Pos(row,col-steps,offset-steps)
    }
}