package com.lidesheng.hyperlyric.root.source

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.lidesheng.hyperlyric.common.lyric.LyricInfoParser
import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.lyric.source.LyricSink
import com.lidesheng.hyperlyric.lyric.source.LyricSource
import com.lidesheng.hyperlyric.root.utils.HookLogger

class LyricInfoSource(private val context: Context) : LyricSource {
    override val id = "lyricinfo"
    override val displayName = "LyricInfo"
    override fun isAvailable() = true

    private val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val handler = Handler(Looper.getMainLooper())
    private data class Session(
        val controller: MediaController,
        val callback: MediaController.Callback,
        var payload: String? = null,
        var mediaId: String? = null,
        var album: String? = null
    )
    private class Run(val owner: LyricSessionArbiter) {
        val sessions = LinkedHashMap<MediaSession.Token, Session>()
        var listener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    }
    @Volatile private var activeRun: Run? = null

    override fun start(sink: LyricSink) {
        val owner = LyricSessionArbiter(context, sink, 33L)
        val run = Run(owner)
        activeRun = run
        owner.start()
        owner.execute {
            val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                if (activeRun === run) sync(run, controllers.orEmpty())
            }
            run.listener = sessionListener
            runCatching {
                manager.addOnActiveSessionsChangedListener(sessionListener, null, handler)
                sync(run, manager.getActiveSessions(null))
            }.onFailure { HookLogger.w("LyricInfoSource", "媒体会话监听失败", it) }
        }
    }

    override fun stop() {
        val run = activeRun ?: return
        activeRun = null
        val cleanup = {
            run.listener?.let { runCatching { manager.removeOnActiveSessionsChangedListener(it) } }
            run.sessions.values.forEach { runCatching { it.controller.unregisterCallback(it.callback) } }
            run.sessions.clear()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) cleanup() else handler.post { cleanup() }
        run.owner.close()
    }

    private fun sync(run: Run, controllers: List<MediaController>) {
        val owner = run.owner
        val sessions = run.sessions
        val tokens = controllers.map { it.sessionToken }.toSet()
        sessions.keys.filter { it !in tokens }.forEach { token ->
            sessions.remove(token)?.let { runCatching { it.controller.unregisterCallback(it.callback) } }
            owner.remove(token)
        }
        controllers.forEach { controller ->
            val token = controller.sessionToken
            if (token !in sessions) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        if (activeRun === run && sessions[token]?.controller === controller) update(run, controller, metadata)
                    }
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        if (activeRun === run && sessions[token]?.controller === controller) update(run, controller, controller.metadata)
                    }
                    override fun onSessionDestroyed() {
                        if (activeRun === run) runCatching { sync(run, manager.getActiveSessions(null)) }
                    }
                }
                runCatching {
                    controller.registerCallback(callback, handler)
                    sessions[token] = Session(controller, callback)
                    update(run, controller, controller.metadata)
                }.onFailure { HookLogger.w("LyricInfoSource", "媒体会话回调注册失败", it) }
            }
        }
    }

    private fun update(run: Run, controller: MediaController, metadata: MediaMetadata?) {
        val owner = run.owner
        val session = run.sessions[controller.sessionToken] ?: return
        if (metadata == null) return
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.takeIf { it.isNotBlank() }
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
        val trackChanged = (mediaId != null && session.mediaId != null && mediaId != session.mediaId) ||
            (mediaId == null && session.mediaId == null && album != null && session.album != null && album != session.album)
        val raw = runCatching { metadata.getString("lyricInfo") }.getOrNull()?.takeIf { it.isNotBlank() }
        val changed = trackChanged || raw != session.payload
        val payload = if (changed && raw != null) LyricInfoParser.parsePayload(raw) else null
        session.mediaId = mediaId ?: session.mediaId
        session.album = album ?: session.album
        // Metadata can temporarily omit lyricInfo. Keep the confirmed timeline for this track,
        // but never carry it across a definite media-item change.
        if (payload != null) session.payload = raw
        else if (trackChanged) session.payload = null
        val state = controller.playbackState
        owner.update(controller.sessionToken, controller.packageName, controller.sessionToken) {
            if (payload != null || trackChanged) {
                onSongChanged(payload?.song)
                onMetadata(payload?.let {
                    LyricMediaMetadata(
                        sourceId = id, packageName = controller.packageName, songId = it.songId,
                        title = it.title, artist = it.artist, album = it.album,
                        duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { duration -> duration > 0L },
                        sessionToken = controller.sessionToken, mediaId = mediaId
                    )
                })
            }
            onPlaybackStateChanged(state?.state == PlaybackState.STATE_PLAYING, state?.playbackSpeed ?: Float.NaN)
        }
    }
}
