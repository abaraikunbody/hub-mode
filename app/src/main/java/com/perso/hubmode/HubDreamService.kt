package com.perso.hubmode

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Display

/**
 * L'écran de veille « Hub Mode ».
 * Android le lance tout seul quand le téléphone est verrouillé et en charge
 * (réglage « Écran de veille » › « Quand démarrer »).
 */
class HubDreamService : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private var hub: HubController? = null
    private var orientation: OrientationWatcher? = null
    private var rotator: RotatingLayout? = null
    private var quarter: Int? = null
    private var displayManager: DisplayManager? = null

    private val finishIfPortrait = Runnable {
        Diag.log("Arrêt : téléphone détecté en portrait")
        finish()
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) applyRotation()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Diag.log("Écran de veille : démarrage (rotation écran=${displayRotation()})")
        isInteractive = true          // pour pouvoir toucher les boutons Spotify
        isFullscreen = true
        isScreenBright = !HubController.isNight(this)
        setContentView(R.layout.hub)

        rotator = findViewById(R.id.rotator)
        hub = HubController(this, rotator!!) {
            Diag.log("Arrêt : bouton fermer")
            finish()
        }.also { it.start() }

        displayManager = getSystemService(DisplayManager::class.java)
        displayManager?.registerDisplayListener(displayListener, handler)

        orientation = OrientationWatcher(this) { q ->
            Diag.log("Orientation détectée : " + when (q) { 0 -> "portrait"; 2 -> "portrait inversé"; 1, 3 -> "paysage"; else -> "à plat" })
            quarter = q
            applyRotation()
            checkLandscape()
        }.also { it.start() }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        Diag.log("Écran de veille : affiché")
    }

    override fun onDreamingStopped() {
        Diag.log("Écran de veille : arrêté par Android")
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        Diag.log("Écran de veille : fermé")
        handler.removeCallbacks(finishIfPortrait)
        orientation?.stop()
        displayManager?.unregisterDisplayListener(displayListener)
        hub?.stop()
        hub = null
        super.onDetachedFromWindow()
    }

    private fun displayRotation(): Int =
        displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0

    /** Remet l'affichage à l'endroit selon la position réelle du téléphone. */
    private fun applyRotation() {
        val dr = displayRotation()
        // Téléphone à plat : on suppose qu'il est en paysage.
        val q = quarter ?: if (dr == 1 || dr == 3) dr else 1
        rotator?.angle = ((q - dr + 4) % 4) * 90
    }

    /** Option « seulement à l'horizontale » : en portrait, on laisse l'écran s'éteindre. */
    private fun checkLandscape() {
        val q = quarter
        val portrait = q == 0 || q == 2
        if (Prefs.landscapeOnly(this) && portrait) {
            handler.removeCallbacks(finishIfPortrait)
            handler.postDelayed(finishIfPortrait, 4000)
        } else {
            handler.removeCallbacks(finishIfPortrait)
        }
    }
}
