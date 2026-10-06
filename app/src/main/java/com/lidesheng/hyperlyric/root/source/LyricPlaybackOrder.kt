package com.lidesheng.hyperlyric.root.source

/** Playback transitions, rather than content/position updates, establish activity order. */
internal class LyricPlaybackOrder<K> {
    private data class State(var playing: Boolean, var sequence: Long)
    private val states = LinkedHashMap<K, State>()
    private var sequence = 0L

    fun seed(keys: List<Pair<K, Boolean>>) {
        keys.asReversed().forEach { (key, playing) ->
            if (key !in states) states[key] = State(playing, ++sequence)
        }
    }

    fun update(key: K, playing: Boolean) {
        val previous = states[key]
        if (previous == null) states[key] = State(playing, ++sequence)
        else {
            if (playing && !previous.playing) previous.sequence = ++sequence
            previous.playing = playing
        }
    }

    fun remove(key: K) { states.remove(key) }
    fun clear() { states.clear(); sequence = 0L }
    fun isPlaying(key: K): Boolean = states[key]?.playing == true
    fun rank(key: K): Long = states[key]?.sequence ?: Long.MIN_VALUE
}
