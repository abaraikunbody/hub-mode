package com.perso.hubmode

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Écran de réglages (aussi accessible depuis Paramètres › Écran de veille › ⚙️). */
class MainActivity : Activity() {

    private lateinit var tickers: EditText
    private lateinit var serviceOn: Switch
    private lateinit var overlayStatus: TextView
    private lateinit var nightDim: Switch
    private lateinit var mediaStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tickers = findViewById(R.id.tickers)
        serviceOn = findViewById(R.id.service_on)
        overlayStatus = findViewById(R.id.overlay_status)
        nightDim = findViewById(R.id.night_dim)
        mediaStatus = findViewById(R.id.media_status)

        tickers.setText(Prefs.rawTickers(this))
        serviceOn.isChecked = Prefs.serviceEnabled(this)
        serviceOn.setOnCheckedChangeListener { _, on ->
            Prefs.setServiceEnabled(this, on)
            if (on) HubService.start(this) else HubService.stop(this)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        nightDim.isChecked = Prefs.nightDim(this)

        findViewById<Button>(R.id.btn_overlay).setOnClickListener {
            open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btn_access).setOnClickListener { openListenerSettings() }
        findViewById<Button>(R.id.btn_appinfo).setOnClickListener {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btn_save).setOnClickListener {
            save()
            Toast.makeText(this, "Enregistré ✔", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btn_preview).setOnClickListener {
            save()
            startActivity(Intent(this, PreviewActivity::class.java))
        }
        findViewById<Button>(R.id.btn_clear).setOnClickListener {
            Diag.clear()
            showJournal()
        }
    }

    override fun onResume() {
        super.onResume()
        val ok = MediaWatcher.hasAccess(this)
        mediaStatus.text = if (ok) "✅ Accès autorisé : la musique en cours s'affichera."
        else "❌ Accès non autorisé : Spotify ne s'affichera pas."
        val overlay = Settings.canDrawOverlays(this)
        overlayStatus.text = if (overlay) "✅ Affichage par-dessus les autres applis autorisé."
        else "❌ Autorise « Afficher par-dessus les autres applis » (obligatoire)."
        if (Prefs.serviceEnabled(this)) HubService.start(this)
        showJournal()
    }

    override fun onPause() {
        save()
        super.onPause()
    }

    private fun showJournal() {
        findViewById<TextView>(R.id.journal).text = Diag.read().ifBlank { "(vide)" }
    }

    private fun save() {
        Prefs.setRawTickers(this, tickers.text.toString())
        Prefs.setNightDim(this, nightDim.isChecked)
    }

    private fun openListenerSettings() {
        val comp = ComponentName(this, MediaListener::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, comp.flattenToString())
            if (open(detail, toastOnFail = false)) return
        }
        open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun open(i: Intent, toastOnFail: Boolean = true): Boolean = try {
        startActivity(i)
        true
    } catch (e: ActivityNotFoundException) {
        if (toastOnFail) Toast.makeText(this, "Réglage introuvable sur ce téléphone", Toast.LENGTH_LONG).show()
        false
    }
}
