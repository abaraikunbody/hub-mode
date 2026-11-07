package com.perso.hubmode

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.view.KeyEvent

/** Service vide : il sert juste à obtenir l'autorisation de lire la musique en cours. */
class MediaListener : NotificationListenerService()

/** Suit la lecture en cours (Spotify en priorité) et permet de la contrôler. */
class MediaWatcher(
    private val ctx: Context,
    private val onChange: (State) -> Unit
) {
    data class State(
        val access: Boolean,
        val hasSession: Boolean,
        val appName: String = "Spotify",
        val title: String = "",
        val artist: String = "",
        val art: Bitmap? = null,
        val playing: Boolean = false,
        val position: Long = 0,
        val updateTime: Long = 0,
        val speed: Float = 1f,
        val duration: Long = 0
    ) {
        fun currentPosition(): Long {
            if (!playing || updateTime <= 0) return position
            val p = position + ((SystemClock.elapsedRealtime() - updateTime) * speed).toLong()
            return if (duration > 0) p.coerceIn(0, duration) else p
        }
    }

    companion object {
        const val SPOTIFY = "com.spotify.music"

        fun hasAccess(ctx: Context): Boolean {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
            return nm.isNotificationListenerAccessGranted(ComponentName(ctx, MediaListener::class.java))
        }
    }

    private val msm = ctx.getSystemService(MediaSessionManager::class.java)
    private val comp = ComponentName(ctx, MediaListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var listening = false

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            attach(null)
            refresh()
        }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list ?: emptyList()) }

    fun start() {
        if (!hasAccess(ctx) || msm == null) {
            onChange(State(access = false, hasSession = false))
            return
        }
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, comp, handler)
            listening = true
            pick(msm.getActiveSessions(comp))
        } catch (e: SecurityException) {
            onChange(State(access = false, hasSession = false))
        }
    }

    fun stop() {
        if (listening) {
            try {
                msm?.removeOnActiveSessionsChangedListener(sessionsListener)
            } catch (_: Exception) {
            }
            listening = false
        }
        attach(null)
    }

    private fun refresh() {
        try {
            pick(msm?.getActiveSessions(comp) ?: emptyList())
        } catch (_: SecurityException) {
            publish()
        }
    }

    private fun isPlaying(c: MediaController) =
        c.playbackState?.state == PlaybackState.STATE_PLAYING

    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.packageName == SPOTIFY && isPlaying(it) }
            ?: list.firstOrNull { isPlaying(it) }
            ?: list.firstOrNull { it.packageName == SPOTIFY }
            ?: list.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) attach(best)
        publish()
    }

    private fun attach(c: MediaController?) {
        controller?.unregisterCallback(callback)
        controller = c
        c?.registerCallback(callback, handler)
    }

    private fun appName(pkg: String): String {
        if (pkg == SPOTIFY) return "Spotify"
        return try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            "Lecture en cours"
        }
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            onChange(State(access = true, hasSession = false))
            return
        }
        val md = c.metadata
        val ps = c.playbackState
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        onChange(
            State(
                access = true,
                hasSession = true,
                appName = appName(c.packageName),
                title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
                artist = artist,
                art = art,
                playing = ps?.state == PlaybackState.STATE_PLAYING,
                position = ps?.position ?: 0,
                updateTime = ps?.lastPositionUpdateTime ?: 0,
                speed = ps?.playbackSpeed ?: 1f,
                duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
            )
        )
    }

    fun playPause() {
        val c = controller
        if (c == null) {
            startSpotify()
            return
        }
        if (isPlaying(c)) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    /** Aucune session active : on demande à Spotify de reprendre la lecture. */
    private fun startSpotify() {
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val i = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setPackage(SPOTIFY)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY))
            try {
                ctx.sendBroadcast(i)
            } catch (_: Exception) {
            }
        }
        handler.postDelayed({ refresh() }, 1500)
    }
}
