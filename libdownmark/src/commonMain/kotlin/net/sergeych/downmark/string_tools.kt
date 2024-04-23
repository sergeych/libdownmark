package net.sergeych.downmark

fun String.indentExpandingTabs(tabSize: Int): Int {
    var col = 0
    for (i in 0..length) {
        when (this[i]) {
            ' ' -> col++
            '\t' -> col = ((col % tabSize) + 1) * tabSize
            else -> break
        }
    }
    return col
}

fun String.indentSpaces(): Int {
    var i = 0
    for( c in this) if( c == ' ' ) i++
    return i
}


fun String.expandTabs(tabSize: Int): String {
    val acc = StringBuilder()
    var i = 0
    for (c in this) {
        when (c) {
            '\t' -> {
                val n = ((i + tabSize) / tabSize) * tabSize
                while (i < n) {
                    acc.append(' '); i++
                }
            }

            else -> {
                acc.append(c); i++
            }
        }
    }
    return acc.toString()
}

fun String.skipIndent(): Pair<String,Int> {
    val i = indentSpaces()
    return substring(i) to i
}

fun List<InlineItem>.text(): String =
    joinToString {
        when(it) {
            is InlineItem.Code -> it.text
            is InlineItem.Image -> TODO()
            is InlineItem.Link -> it.ref.name
            is InlineItem.Text -> it.text
        }
    }
