package net.sergeych.downmark

typealias SourceRange = OpenEndRange<Pos>

data class MarkupPlacement(
    val markups: List<SourceRange>,
    val body: SourceRange?
)

interface MarkupItem {
    val placement: MarkupPlacement
}