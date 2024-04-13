package net.sergeych.downmark

data class MarkupPlacement(
    val markups: List<OpenEndRange<Pos>>,
    val body: OpenEndRange<Pos>?
)

