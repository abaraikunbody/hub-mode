package com.perso.hubmode

import android.app.Application
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Petit journal de diagnostic (visible dans l'appli). */
object Diag {
    private var file: File? = null

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "journal.txt")
    }

    @Synchronized
    fun log(msg: String) {
        val f = file ?: return
        try {
            val t = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())
            val lines = (if (f.exists()) f.readLines() else emptyList()).takeLast(60) + "$t  $msg"
            f.writeText(lines.joinToString("\n"))
        } catch (_: Exception) {
        }
    }

    fun read(): String = try {
        file?.takeIf { it.exists() }?.readLines()?.reversed()?.joinToString("\n") ?: ""
    } catch (_: Exception) {
        ""
    }

    fun clear() {
        file?.delete()
    }
}

class HubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diag.init(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Diag.log("PLANTAGE : " + e.toString() + " @ " + (e.stackTrace.firstOrNull()?.toString() ?: ""))
            previous?.uncaughtException(t, e)
        }
    }
}
