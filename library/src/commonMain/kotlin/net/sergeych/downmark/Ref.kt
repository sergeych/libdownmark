package net.sergeych.downmark

data class Ref(
    val name: String,
    val link: String,
    val title: String? = null,
    val isFootnote: Boolean = false
) {
}