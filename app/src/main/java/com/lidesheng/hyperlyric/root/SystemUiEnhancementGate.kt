package com.lidesheng.hyperlyric.root

import com.lidesheng.hyperlyric.common.RootConstants

internal object SystemUiEnhancementGate {
    fun isEnabled(): Boolean {
        val entry = HookEntry.instance ?: return false
        return runCatching {
            entry.prefs.getBoolean(
                RootConstants.KEY_HOOK_ENABLE,
                RootConstants.DEFAULT_HOOK_ENABLE
            )
        }.getOrDefault(false)
    }
}
