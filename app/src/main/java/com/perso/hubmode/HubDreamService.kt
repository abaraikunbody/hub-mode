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

    private val finishIfPortrait = Runnable { finish() }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) applyRotation()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true          // pour pouvoir toucher les boutons Spotify
        isFullscreen = true
        isScreenBright = !HubController.isNight(this)
        setContentView(R.layout.hub)

        rotator = findViewById(R.id.rotator)
        hub = HubController(this, rotator!!) { finish() }.also { it.start() }

        displayManager = getSystemService(DisplayManager::class.java)
        displayManager?.registerDisplayListener(displayListener, handler)

        orientation = OrientationWatcher(this) { q ->
            quarter = q
            applyRotation()
            checkLandscape()
        }.also { it.start() }
    }

    override fun onDetachedFromWindow() {
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
