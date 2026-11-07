package com.perso.hubmode

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings

/** État partagé entre le service et l'écran Hub. */
object HubState {
    /** Vrai après un ✕ ou un déverrouillage : on ne relance pas tant que l'écran ne s'est pas éteint. */
    @Volatile var suppressed = false
}

/**
 * Service de fond (avec une notification discrète) qui surveille :
 * la charge, l'écran, le verrouillage et l'orientation du téléphone.
 * En charge + verrouillé + à l'horizontale → il affiche l'écran Hub.
 */
class HubService : Service() {

    companion object {
        private const val CHANNEL = "hub"

        fun start(ctx: Context) {
            val i = Intent(ctx, HubService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, HubService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var orientation: OrientationWatcher? = null
    private var quarter: Int? = null
    private var charging = false
    private var wakeLock: PowerManager.WakeLock? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_POWER_CONNECTED -> setCharging(true)
                Intent.ACTION_POWER_DISCONNECTED -> {
                    setCharging(false)
                    HubActivity.current?.finish()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    HubState.suppressed = false
                }
                Intent.ACTION_SCREEN_ON -> handler.postDelayed({ maybeLaunch("écran allumé") }, 300)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Diag.init(this)
        startInForeground()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(receiver, f)

        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        setCharging((battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0)
        Diag.log("Service démarré (en charge : ${if (charging) "oui" else "non"})")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        setCharging(false)
        Diag.log("Service arrêté")
        super.onDestroy()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Hub Mode actif", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_music)
            .setContentTitle("Hub Mode actif")
            .setContentText("S'affiche quand le téléphone est en charge et couché")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    /** On n'écoute l'accéléromètre que pendant la charge (aucun impact batterie sinon). */
    private fun setCharging(c: Boolean) {
        if (c == charging && (orientation != null) == c) return
        charging = c
        if (c) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hubmode:charge").apply {
                setReferenceCounted(false)
                acquire()
            }
            orientation = OrientationWatcher(this) { q ->
                val was = quarter
                quarter = q
                if (q == 0 || q == 2) HubState.suppressed = false
                if (q != was) maybeLaunch("téléphone tourné")
            }.also { it.start() }
        } else {
            orientation?.stop()
            orientation = null
            quarter = null
            try {
                wakeLock?.release()
            } catch (_: Exception) {
            }
            wakeLock = null
        }
    }

    private fun maybeLaunch(reason: String) {
        val q = quarter
        val landscape = q == 1 || q == 3
        if (!charging || !landscape) return
        if (HubActivity.current != null || HubState.suppressed) return
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) return
        if (!Settings.canDrawOverlays(this)) {
            Diag.log("Impossible d'afficher : autorisation « par-dessus les autres applis » manquante")
            return
        }
        Diag.log("Affichage du Hub ($reason)")
        try {
            startActivity(
                Intent(this, HubActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            )
        } catch (e: Exception) {
            Diag.log("Échec de l'affichage : $e")
        }
    }
}

/** Relance le service au démarrage du téléphone et après une mise à jour de l'appli. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Prefs.serviceEnabled(c)) {
            try {
                HubService.start(c)
            } catch (_: Exception) {
            }
        }
    }
}
