package net.sergeych.downmark

inline fun <reified T : InlineItem> List<InlineItem>.itemAt(index: Int) =
    this[index] as T