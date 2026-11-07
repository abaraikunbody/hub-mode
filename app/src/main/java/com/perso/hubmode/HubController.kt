package com.perso.hubmode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Toute la logique de l'écran Hub : batterie, Spotify, ETF, anti-marquage OLED.
 * Utilisé à la fois par l'écran de veille et par l'aperçu.
 */
class HubController(
    private val ctx: Context,
    root: View,
    private val onClose: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private var running = false

    private val content: View = root.findViewById(R.id.content)
    private val bg: View = root.findViewById(R.id.bg)
    private var bgColor = Color.BLACK
    private var lastArt: Bitmap? = null
    private var bgAnim: ValueAnimator? = null
    private val battery: TextView = root.findViewById(R.id.battery)
    private val etfRow: LinearLayout = root.findViewById(R.id.etfs)

    private val art: ImageView = root.findViewById(R.id.art)
    private val appLabel: TextView = root.findViewById(R.id.media_app)
    private val title: TextView = root.findViewById(R.id.title)
    private val artist: TextView = root.findViewById(R.id.artist)
    private val progress: ProgressBar = root.findViewById(R.id.progress)
    private val pos: TextView = root.findViewById(R.id.pos)
    private val dur: TextView = root.findViewById(R.id.dur)
    private val hint: TextView = root.findViewById(R.id.media_hint)
    private val play: ImageButton = root.findViewById(R.id.play)
    private val prev: ImageButton = root.findViewById(R.id.prev)
    private val next: ImageButton = root.findViewById(R.id.next)

    private val media = MediaWatcher(ctx) { render(it) }
    private var mediaState: MediaWatcher.State? = null
    private val cards = ArrayList<EtfCard>()
    private var batteryRegistered = false

    init {
        art.clipToOutline = true
        title.isSelected = true
        root.findViewById<View>(R.id.close).setOnClickListener { onClose() }
        play.setOnClickListener { media.playPause() }
        prev.setOnClickListener { media.previous() }
        next.setOnClickListener { media.next() }
    }

    fun start() {
        running = true
        buildEtfCards()
        handler.post(quoteLoop)
        media.start()
        ctx.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryRegistered = true
        handler.post(tick)
        handler.post(minuteLoop)
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        media.stop()
        if (batteryRegistered) {
            try {
                ctx.unregisterReceiver(batteryReceiver)
            } catch (_: Exception) {
            }
            batteryRegistered = false
        }
        io.shutdownNow()
    }

    // ---------------------------------------------------------------- Boucles

    /** Toutes les secondes : barre de progression de la musique. */
    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            if (running) handler.postDelayed(this, 1000)
        }
    }

    /** Toutes les minutes : léger décalage anti-marquage + mode nuit. */
    private val minuteLoop = object : Runnable {
        override fun run() {
            val r = ctx.dp(6f)
            content.animate()
                .translationX(Random.nextFloat() * 2 * r - r)
                .translationY(Random.nextFloat() * 2 * r - r)
                .setDuration(1500)
                .start()
            content.alpha = if (isNight(ctx)) 0.45f else 1f
            if (running) handler.postDelayed(this, 60_000)
        }
    }

    /** Toutes les 5 minutes (30 min hors bourse) : cours des ETF. */
    private val quoteLoop = object : Runnable {
        override fun run() {
            refreshQuotes()
            if (running) handler.postDelayed(this, if (marketOpen()) 5 * 60_000L else 30 * 60_000L)
        }
    }

    // ---------------------------------------------------------------- Batterie

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (level < 0) return
            val pct = level * 100 / scale.coerceAtLeast(1)
            battery.text = if (plugged) "·  ⚡ $pct %" else "·  $pct %"
        }
    }

    // ---------------------------------------------------------------- Spotify

    private fun render(s: MediaWatcher.State) {
        mediaState = s
        val controlsAlpha: Float
        when {
            !s.access -> {
                appLabel.text = "SPOTIFY"
                title.text = "Accès non autorisé"
                artist.text = ""
                hint.text = "Ouvre l'appli Hub Mode › « Autoriser l'accès aux notifications »"
                hint.visibility = View.VISIBLE
                art.setImageResource(R.drawable.ic_music)
                art.scaleType = ImageView.ScaleType.CENTER
                controlsAlpha = 0.3f
            }
            !s.hasSession -> {
                appLabel.text = "SPOTIFY"
                title.text = "Rien en lecture"
                artist.text = "Appuie sur ▶ pour reprendre"
                hint.visibility = View.GONE
                art.setImageResource(R.drawable.ic_music)
                art.scaleType = ImageView.ScaleType.CENTER
                controlsAlpha = 1f
            }
            else -> {
                appLabel.text = "EN LECTURE SUR " + s.appName.uppercase(Locale.FRANCE)
                title.text = s.title.ifBlank { "Titre inconnu" }
                artist.text = s.artist
                hint.visibility = View.GONE
                if (s.art != null) {
                    art.setImageBitmap(s.art)
                    art.scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    art.setImageResource(R.drawable.ic_music)
                    art.scaleType = ImageView.ScaleType.CENTER
                }
                controlsAlpha = 1f
            }
        }
        updateTint(if (s.access && s.hasSession) s.art else null)
        play.setImageResource(if (s.playing) R.drawable.ic_pause else R.drawable.ic_play)
        play.alpha = controlsAlpha
        prev.alpha = if (s.hasSession) 1f else 0.3f
        next.alpha = if (s.hasSession) 1f else 0.3f
        updateProgress()
    }

    private fun updateProgress() {
        val s = mediaState
        if (s == null || !s.hasSession || s.duration <= 0) {
            progress.progress = 0
            pos.text = ""
            dur.text = ""
            return
        }
        val p = s.currentPosition()
        progress.progress = (p * 1000 / s.duration).toInt()
        pos.text = fmtTime(p)
        dur.text = fmtTime(s.duration)
    }

    /** Fond teinté avec la couleur dominante de la pochette (assombrie pour l'OLED). */
    private fun updateTint(art: Bitmap?) {
        if (art === lastArt) return
        lastArt = art
        val target = if (art == null) Color.BLACK else dominantDark(art)
        val accent = if (art == null) Color.rgb(124, 240, 168) else accentOf(target)
        appLabel.setTextColor(accent)
        bgAnim?.cancel()
        bgAnim = ValueAnimator.ofArgb(bgColor, target).apply {
            duration = 900
            addUpdateListener {
                bgColor = it.animatedValue as Int
                bg.setBackgroundColor(bgColor)
            }
            start()
        }
    }

    private fun dominantDark(src: Bitmap): Int {
        val small = try {
            Bitmap.createScaledBitmap(src.copy(Bitmap.Config.ARGB_8888, false), 24, 24, true)
        } catch (_: Exception) {
            return Color.BLACK
        }
        var r = 0L; var g = 0L; var b = 0L; var w = 0f
        val hsv = FloatArray(3)
        for (x in 0 until small.width) for (y in 0 until small.height) {
            val c = small.getPixel(x, y)
            Color.colorToHSV(c, hsv)
            // On privilégie les pixels colorés, ni trop sombres ni trop clairs.
            val weight = 0.15f + hsv[1] * (if (hsv[2] in 0.15f..0.95f) 1f else 0.2f)
            r += (Color.red(c) * weight).toLong()
            g += (Color.green(c) * weight).toLong()
            b += (Color.blue(c) * weight).toLong()
            w += weight
        }
        if (w <= 0f) return Color.BLACK
        val avg = Color.rgb((r / w).toInt().coerceIn(0, 255), (g / w).toInt().coerceIn(0, 255), (b / w).toInt().coerceIn(0, 255))
        Color.colorToHSV(avg, hsv)
        hsv[1] = (hsv[1] * 1.2f).coerceAtMost(0.55f)
        hsv[2] = 0.16f
        return Color.HSVToColor(hsv)
    }

    private fun accentOf(dark: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(dark, hsv)
        hsv[1] = (hsv[1] * 0.9f).coerceIn(0.25f, 0.5f)
        hsv[2] = 0.95f
        return Color.HSVToColor(hsv)
    }

    private fun fmtTime(ms: Long): String {
        val t = ms / 1000
        return String.format(Locale.FRANCE, "%d:%02d", t / 60, t % 60)
    }

    // ---------------------------------------------------------------- ETF

    private fun buildEtfCards() {
        etfRow.removeAllViews()
        cards.clear()
        Prefs.tickers(ctx).forEachIndexed { index, t ->
            val card = EtfCard(ctx, t)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) lp.topMargin = ctx.dpi(6f)
            etfRow.addView(card.view, lp)
            cards.add(card)
        }
    }

    private fun refreshQuotes() {
        for (card in cards.toList()) {
            try {
                io.execute {
                    val res = runCatching { Quotes.fetch(card.ticker.symbol) }
                    handler.post {
                        if (!running) return@post
                        res.onSuccess { card.bind(it) }
                            .onFailure { card.error() }
                    }
                }
            } catch (_: Exception) {
                // exécuteur arrêté
            }
        }
    }

    private fun marketOpen(): Boolean {
        val c = Calendar.getInstance()
        val dow = c.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return false
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return m in (8 * 60 + 50)..(17 * 60 + 45)
    }

    companion object {
        fun isNight(ctx: Context): Boolean {
            if (!Prefs.nightDim(ctx)) return false
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return h >= 22 || h < 7
        }
    }
}

/** Ligne compacte d'un ETF (en haut à droite) : nom, cours et variation du jour. */
private class EtfCard(private val ctx: Context, val ticker: Ticker) {

    private val green = Color.rgb(124, 240, 168)
    private val red = Color.rgb(255, 120, 120)

    val view = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.END
    }

    private val name = TextView(ctx).apply {
        textSize = 12f
        setTextColor(Color.argb(190, 255, 255, 255))
        text = ticker.label ?: ticker.symbol
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.END
    }
    private val line = TextView(ctx).apply {
        textSize = 17f
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        text = "…"
        gravity = Gravity.END
    }
    private var hasValue = false

    init {
        view.addView(name)
        view.addView(line)
    }

    fun bind(q: Quote) {
        if (ticker.label == null) name.text = q.name
        val price = Quotes.formatPrice(q.price, q.currency)
        val pct = String.format(Locale.FRANCE, "%+.2f %%", q.changePct)
        val sb = SpannableString("$price  $pct")
        val col = if (q.changePct >= 0) green else red
        sb.setSpan(ForegroundColorSpan(col), price.length + 2, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        line.text = sb
        hasValue = true
    }

    fun error() {
        if (!hasValue) line.text = "indisponible"
    }
}
