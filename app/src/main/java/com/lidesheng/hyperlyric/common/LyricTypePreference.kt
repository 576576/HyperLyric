package com.lidesheng.hyperlyric.common

import android.content.SharedPreferences

object LyricTypePreference {
    fun read(prefs: SharedPreferences): Int = prefs.getInt(
        RootConstants.KEY_HOOK_LYRIC_TYPE,
        RootConstants.DEFAULT_HOOK_LYRIC_TYPE,
    ).coerceIn(
        RootConstants.LYRIC_TYPE_SUPER_ISLAND,
        RootConstants.LYRIC_TYPE_STATUS_BAR,
    )

    fun isEnabled(prefs: SharedPreferences, type: Int): Boolean =
        prefs.getBoolean(RootConstants.KEY_HOOK_ENABLE, RootConstants.DEFAULT_HOOK_ENABLE) &&
                read(prefs) == type
}
