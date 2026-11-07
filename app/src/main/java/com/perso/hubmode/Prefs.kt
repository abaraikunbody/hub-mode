package com.perso.hubmode

import android.content.Context
import android.content.SharedPreferences

data class Ticker(val symbol: String, val label: String?)

object Prefs {
    const val DEFAULT_TICKERS = "DCAM.PA|PEA Monde"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("hub", Context.MODE_PRIVATE)

    fun rawTickers(ctx: Context): String =
        sp(ctx).getString("tickers", DEFAULT_TICKERS) ?: DEFAULT_TICKERS

    fun setRawTickers(ctx: Context, value: String) =
        sp(ctx).edit().putString("tickers", value.trim()).apply()

    fun tickers(ctx: Context): List<Ticker> =
        rawTickers(ctx).lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(3)
            .map { line ->
                val parts = line.split("|", limit = 2)
                Ticker(
                    symbol = parts[0].trim().uppercase(),
                    label = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
                )
            }

    fun landscapeOnly(ctx: Context): Boolean = sp(ctx).getBoolean("landscape_only", true)
    fun setLandscapeOnly(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean("landscape_only", v).apply()

    fun nightDim(ctx: Context): Boolean = sp(ctx).getBoolean("night_dim", true)
    fun setNightDim(ctx: Context, v: Boolean) =
        sp(ctx).edit().putBoolean("night_dim", v).apply()
}

fun Context.dp(v: Float): Float = v * resources.displayMetrics.density
fun Context.dpi(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
