package com.perso.hubmode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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
            battery.text = if (plugged) "⚡ $pct %  ·  en charge" else "$pct %"
        }
    }

    // ---------------------------------------------------------------- Spotify

    private fun render(s: MediaWatcher.State) {
        mediaState = s
        val controlsAlpha: Float
        when {
            !s.access -> {
                appLabel.text = "Spotify"
                title.text = "Accès non autorisé"
                artist.text = ""
                hint.text = "Ouvre l'appli Hub Mode › « Autoriser l'accès aux notifications »"
                hint.visibility = View.VISIBLE
                art.setImageResource(R.drawable.ic_music)
                art.scaleType = ImageView.ScaleType.CENTER
                controlsAlpha = 0.3f
            }
            !s.hasSession -> {
                appLabel.text = "Spotify"
                title.text = "Rien en lecture"
                artist.text = "Appuie sur ▶ pour reprendre"
                hint.visibility = View.GONE
                art.setImageResource(R.drawable.ic_music)
                art.scaleType = ImageView.ScaleType.CENTER
                controlsAlpha = 1f
            }
            else -> {
                appLabel.text = s.appName
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

    private fun fmtTime(ms: Long): String {
        val t = ms / 1000
        return String.format(Locale.FRANCE, "%d:%02d", t / 60, t % 60)
    }

    // ---------------------------------------------------------------- ETF

    private fun buildEtfCards() {
        etfRow.removeAllViews()
        cards.clear()
        val list = Prefs.tickers(ctx)
        list.forEachIndexed { index, t ->
            val card = EtfCard(ctx, t)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (index > 0) lp.marginStart = ctx.dpi(12f)
            etfRow.addView(card.view, lp)
            cards.add(card)
        }
        // Une seule carte : on évite qu'elle soit trop large.
        if (list.size == 1) {
            etfRow.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 0.6f))
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

/** Carte d'un ETF : nom, cours, variation du jour, mini-courbe. */
private class EtfCard(private val ctx: Context, val ticker: Ticker) {

    private val green = Color.rgb(30, 215, 96)
    private val red = Color.rgb(255, 92, 92)

    val view = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = ctx.dpi(14f)
        setPadding(p + ctx.dpi(2f), p, p + ctx.dpi(2f), p)
        background = GradientDrawable().apply {
            cornerRadius = ctx.dp(22f)
            setColor(Color.argb(23, 255, 255, 255))
        }
    }

    private val name = text(13f, Color.argb(180, 255, 255, 255)).apply {
        text = ticker.label ?: ticker.symbol
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val price = text(26f, Color.WHITE).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        text = "—"
    }
    private val change = text(14f, Color.argb(180, 255, 255, 255))
    private val spark = SparklineView(ctx)
    private val status = text(11f, Color.argb(120, 255, 255, 255)).apply { text = "Chargement…" }

    init {
        view.addView(name)
        view.addView(price)
        view.addView(change)
        view.addView(
            spark,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ctx.dpi(38f)).apply {
                topMargin = ctx.dpi(6f)
            }
        )
        view.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ctx.dpi(4f) })
    }

    private fun text(sp: Float, color: Int) = TextView(ctx).apply {
        textSize = sp
        setTextColor(color)
        includeFontPadding = false
    }

    fun bind(q: Quote) {
        if (ticker.label == null) name.text = q.name
        price.text = Quotes.formatPrice(q.price, q.currency)
        val col = if (q.changePct >= 0) green else red
        change.text = Quotes.formatPct(q.changePct)
        change.setTextColor(col)
        spark.set(q.points, q.previousClose, col)
        status.text = statusText(q.timeMillis)
    }

    fun error() {
        status.text = if (price.text == "—") "Cours indisponible" else "Hors ligne · dernier cours affiché"
    }

    private fun statusText(t: Long): String {
        if (t <= 0) return ""
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = t }
        val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        val fresh = System.currentTimeMillis() - t < 30 * 60_000L
        return when {
            fresh -> "En direct · " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(t))
            sameDay -> "Clôture · " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(t))
            else -> "Clôture · " + SimpleDateFormat("EEE d MMM", Locale.FRANCE).format(Date(t))
        }
    }
}
