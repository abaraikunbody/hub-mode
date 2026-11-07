package com.perso.hubmode

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.max

/**
 * Détermine l'orientation physique du téléphone grâce à l'accéléromètre,
 * indépendamment de la rotation automatique de l'écran.
 *
 * Valeurs envoyées (en quarts de tour, comme Surface.ROTATION_*) :
 *  0 = portrait, 1 = paysage (haut du téléphone à gauche),
 *  2 = portrait à l'envers, 3 = paysage (haut à droite),
 *  null = posé à plat / inconnu.
 */
class OrientationWatcher(
    ctx: Context,
    private val onChange: (Int?) -> Unit
) : SensorEventListener {

    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var gx = 0f
    private var gy = 0f
    private var initialized = false

    var current: Int? = null
        private set
    private var candidate: Int? = null
    private var candidateSince = 0L
    private var published = false

    fun start() {
        if (sensor == null) {
            onChange(null)
            return
        }
        sm?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sm?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        if (!initialized) {
            gx = x; gy = y; initialized = true
        } else {
            gx += 0.25f * (x - gx)
            gy += 0.25f * (y - gy)
        }

        val q = classify()
        val now = SystemClock.elapsedRealtime()
        if (!published) {
            // Première mesure : on publie tout de suite.
            published = true
            current = q
            candidate = q
            candidateSince = now
            onChange(q)
            return
        }
        if (q == current) {
            candidate = q
            return
        }
        if (q != candidate) {
            candidate = q
            candidateSince = now
        } else if (now - candidateSince > 700) {
            current = q
            onChange(q)
        }
    }

    private fun classify(): Int? {
        val ax = abs(gx)
        val ay = abs(gy)
        if (max(ax, ay) < 3.5f) return null // à plat
        return when {
            ax > ay * 1.3f -> if (gx > 0) 1 else 3
            ay > ax * 1.3f -> if (gy > 0) 0 else 2
            else -> current // entre deux : on garde l'état actuel
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
