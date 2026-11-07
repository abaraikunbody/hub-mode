package com.perso.hubmode

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/**
 * L'écran Hub affiché par-dessus l'écran de verrouillage.
 * ✕ = retour à l'écran de verrouillage · balayer vers le haut = déverrouiller.
 */
class HubActivity : Activity() {

    companion object {
        @Volatile var current: HubActivity? = null
    }

    private var hub: HubController? = null
    private lateinit var gestures: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.hub)
        hideBars()

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dy = (e1?.y ?: e2.y) - e2.y
                if (vy < -1200 && dy > 120) {
                    unlock()
                    return true
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        current = this
        applyNightBrightness()
        hub = HubController(this, findViewById(R.id.rotator)) {
            Diag.log("Hub fermé (✕)")
            HubState.suppressed = true
            finish()
        }.also { it.start() }
    }

    override fun onPause() {
        hub?.stop()
        hub = null
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // Écran éteint ou autre appli au premier plan : on ferme, le service relancera au besoin.
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    private fun unlock() {
        HubState.suppressed = true
        val km = getSystemService(KeyguardManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = finish()
            })
        } else {
            finish()
        }
    }

    private fun applyNightBrightness() {
        val lp = window.attributes
        lp.screenBrightness = if (HubController.isNight(this)) 0.02f
        else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }
}
