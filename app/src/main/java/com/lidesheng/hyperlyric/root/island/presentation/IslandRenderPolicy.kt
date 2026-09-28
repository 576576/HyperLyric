package com.lidesheng.hyperlyric.root.island.presentation

/**
 * Pure presentation policy for Super Island lyric targets.
 *
 * Hookers provide already extracted facts. This class deliberately does not
 * access views, preferences, reflection, or the SystemUI lifecycle.
 */
internal object IslandRenderPolicy {

    sealed interface OwnerEvidence {
        data class Media(val packageName: String) : OwnerEvidence
        data object Pending : OwnerEvidence
        data object NotMedia : OwnerEvidence
    }

    data class Input(
        val owner: OwnerEvidence,
        val lyricPackageName: String?,
        val hasLyricsForPresentation: Boolean,
        val hasMusicInfoForPresentation: Boolean,
        val showMusicInfoWhenNoLyrics: Boolean,
        val enabled: Boolean,
        val playbackActive: Boolean,
        val pauseBehavior: Int
    )

    data class Evaluation(
        val input: Input,
        val decision: Decision,
        val reason: String
    )

    enum class Decision {
        TARGET,
        PENDING,
        OTHER_PACKAGE,
        SUPPRESSED,
        NOT_MEDIA
    }

    fun evaluate(input: Input): Decision {
        return evaluateDetailed(input).decision
    }

    fun evaluateDetailed(input: Input): Evaluation {
        if (input.owner == OwnerEvidence.NotMedia) {
            return Evaluation(input, Decision.NOT_MEDIA, "not_media")
        }
        if (!input.enabled) {
            return Evaluation(input, Decision.SUPPRESSED, "super_island_disabled")
        }
        if (!input.hasLyricsForPresentation &&
            (!input.showMusicInfoWhenNoLyrics || !input.hasMusicInfoForPresentation)
        ) {
            val reason = if (!input.showMusicInfoWhenNoLyrics) {
                "has_lyric_false_music_info_fallback_disabled"
            } else {
                "has_lyric_false_music_info_unavailable"
            }
            return Evaluation(input, Decision.SUPPRESSED, reason)
        }

        val mediaOwner = input.owner as? OwnerEvidence.Media
            ?: return Evaluation(input, Decision.PENDING, "media_owner_pending")
        val lyricPackageName = input.lyricPackageName
            ?.takeIf(String::isNotEmpty)
            ?: return Evaluation(input, Decision.PENDING, "lyric_package_pending")

        if (mediaOwner.packageName != lyricPackageName) {
            return Evaluation(input, Decision.OTHER_PACKAGE, "package_mismatch")
        }
        if (!input.playbackActive && input.pauseBehavior == 0) {
            return Evaluation(input, Decision.SUPPRESSED, "paused_by_policy")
        }
        return Evaluation(input, Decision.TARGET, "eligible")
    }

    fun isPresentationAllowed(
        enabled: Boolean,
        playbackActive: Boolean,
        pauseBehavior: Int
    ): Boolean {
        return enabled && (playbackActive || pauseBehavior != 0)
    }
}
