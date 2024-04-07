package net.sergeych.downmark

data class Pos(val row: Int, val col: Int) : Comparable<Pos> {
    override fun compareTo(other: Pos): Int = when {
        row < other.row -> -1
        row > other.row -> +1
        col < other.col -> -1
        col > other.col -> +1
        else -> 0
    }

}