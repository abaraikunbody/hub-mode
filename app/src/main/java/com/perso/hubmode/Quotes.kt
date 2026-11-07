package com.perso.hubmode

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale

data class Quote(
    val symbol: String,
    val name: String,
    val price: Double,
    val previousClose: Double,
    val currency: String,
    val timeMillis: Long,
    val points: List<Double>
) {
    val changePct: Double
        get() = if (previousClose > 0) (price - previousClose) / previousClose * 100.0 else 0.0
}

/** Récupère les cours via l'API publique (non officielle) de Yahoo Finance. */
object Quotes {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    fun fetch(symbol: String): Quote {
        var last: Exception? = null
        for (host in listOf("query1", "query2")) {
            try {
                return fetchFrom(host, symbol)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Erreur inconnue")
    }

    private fun fetchFrom(host: String, symbol: String): Quote {
        val enc = URLEncoder.encode(symbol, "UTF-8")
        val url = URL("https://$host.finance.yahoo.com/v8/finance/chart/$enc?range=1d&interval=5m")
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json")
        try {
            val code = c.responseCode
            if (code != 200) throw IOException("HTTP $code")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val result = JSONObject(body)
                .getJSONObject("chart")
                .getJSONArray("result")
                .getJSONObject(0)
            val meta = result.getJSONObject("meta")
            val price = meta.getDouble("regularMarketPrice")
            val prev = when {
                meta.has("chartPreviousClose") -> meta.getDouble("chartPreviousClose")
                meta.has("previousClose") -> meta.getDouble("previousClose")
                else -> price
            }
            val name = meta.optString("shortName").ifBlank { meta.optString("longName") }.ifBlank { symbol }
            val points = ArrayList<Double>()
            val closes = result.optJSONObject("indicators")
                ?.optJSONArray("quote")
                ?.optJSONObject(0)
                ?.optJSONArray("close")
            if (closes != null) {
                for (i in 0 until closes.length()) {
                    if (!closes.isNull(i)) points.add(closes.getDouble(i))
                }
            }
            return Quote(
                symbol = symbol,
                name = name,
                price = price,
                previousClose = prev,
                currency = meta.optString("currency", "EUR"),
                timeMillis = meta.optLong("regularMarketTime", 0L) * 1000L,
                points = points
            )
        } finally {
            c.disconnect()
        }
    }

    fun formatPrice(v: Double, currency: String): String {
        val nf = NumberFormat.getNumberInstance(Locale.FRANCE)
        val d = if (v < 20) 3 else 2
        nf.minimumFractionDigits = d
        nf.maximumFractionDigits = d
        val sym = when (currency) {
            "EUR" -> "€"
            "USD" -> "$"
            "GBP", "GBp" -> "£"
            else -> currency
        }
        return "${nf.format(v)} $sym"
    }

    fun formatPct(p: Double): String {
        val arrow = if (p >= 0) "▲" else "▼"
        return arrow + " " + String.format(Locale.FRANCE, "%+.2f %%", p)
    }
}
