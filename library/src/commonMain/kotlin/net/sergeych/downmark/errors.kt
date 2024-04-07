package net.sergeych.downmark

data class SyntaxError(val pos: Pos,val text: String)

class MarkdownException(text: String): Exception(text)