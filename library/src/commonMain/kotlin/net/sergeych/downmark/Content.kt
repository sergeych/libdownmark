package net.sergeych.downmark

typealias Content = List<InlineItem>

inline fun <reified T: InlineItem>Content.itemAt(index: Int) = this[index] as T