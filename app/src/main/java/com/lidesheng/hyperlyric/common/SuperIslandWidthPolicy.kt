package com.lidesheng.hyperlyric.common

object SuperIslandWidthPolicy {
    const val SIDE_COMPONENT_WIDTH_DP = 22
    const val MIN_ISLAND_WIDTH_DP = 22
    const val MAX_SIDE_WIDTH_DP = 120

    fun minIslandWidth(showAlbum: Boolean, showRhythm: Boolean): Int =
        (componentWidth(showAlbum) - componentWidth(showRhythm))
            .coerceAtLeast(MIN_ISLAND_WIDTH_DP)

    fun maxIslandWidth(showRhythm: Boolean): Int =
        MAX_SIDE_WIDTH_DP - componentWidth(showRhythm)

    fun normalizeIslandWidth(
        islandWidth: Int,
        showAlbum: Boolean,
        showRhythm: Boolean
    ): Int = islandWidth.coerceIn(
        minIslandWidth(showAlbum, showRhythm),
        maxIslandWidth(showRhythm)
    )

    fun leftContentWidth(
        islandWidth: Int,
        showAlbum: Boolean,
        showRhythm: Boolean
    ): Int = (islandWidth + leftContentWidthOffsetDp(showAlbum, showRhythm)).coerceAtLeast(0)

    fun leftContentWidthOffsetDp(showAlbum: Boolean, showRhythm: Boolean): Int =
        componentWidth(showRhythm) - componentWidth(showAlbum)

    fun baseWidthFromLeftContentWidth(
        leftContentWidthDp: Float,
        showAlbum: Boolean,
        showRhythm: Boolean
    ): Float = leftContentWidthDp - leftContentWidthOffsetDp(showAlbum, showRhythm)

    private fun componentWidth(visible: Boolean): Int =
        if (visible) SIDE_COMPONENT_WIDTH_DP else 0
}
