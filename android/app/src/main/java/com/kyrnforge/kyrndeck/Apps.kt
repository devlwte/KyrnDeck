package com.kyrnforge.kyrndeck

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.File

enum class Source { STORE, SIDELOAD, SYSTEM }

data class AppEntry(
    val pkg: String,
    val label: String,
    val icon: ImageBitmap,
    val backdrop: ImageBitmap,
    val tint: Color,
    val source: Source,
    val installer: String,
    val vr: Boolean,
    val version: String,
    val installed: Long,
    val updated: Long,
    val apkBytes: Long,
)

const val OCMS = "com.oculus.ocms"
const val VR_CATEGORY = "com.oculus.intent.category.VR"

object Apps {

    // Muchos juegos de la tienda solo declaran la categoría VR, sin LAUNCHER.
    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val vrPkgs = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(VR_CATEGORY), 0)
            .map { it.activityInfo.packageName }.toSet()
        val pkgs = linkedSetOf<String>()
        listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_INFO).forEach { cat ->
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
                .forEach { pkgs += it.activityInfo.packageName }
        }
        pkgs += vrPkgs
        pkgs -= ctx.packageName

        return pkgs.mapNotNull { pkg -> runCatching { entry(pm, pkg, vrPkgs) }.getOrNull() }
    }

    private fun entry(pm: PackageManager, pkg: String, vrPkgs: Set<String>): AppEntry? {
        val info = pm.getPackageInfo(pkg, PackageManager.GET_CONFIGURATIONS)
        val appInfo = info.applicationInfo ?: return null
        val installer = runCatching { pm.getInstallSourceInfo(pkg).installingPackageName }.getOrNull()
        val system = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val source = when {
            installer == OCMS -> Source.STORE
            system -> Source.SYSTEM
            else -> Source.SIDELOAD
        }
        val headTracking = info.reqFeatures?.any { it.name == "android.hardware.vr.headtracking" } == true
        val bmp = pm.getApplicationIcon(appInfo).toSafeBitmap()
        val tint = dominant(bmp)
        return AppEntry(
            pkg = pkg,
            label = pm.getApplicationLabel(appInfo).toString().trim().ifEmpty { pkg },
            icon = bmp.asImageBitmap(),
            backdrop = backdropOf(bmp, tint).asImageBitmap(),
            tint = tint,
            source = source,
            installer = installerName(pm, installer, system),
            vr = pkg in vrPkgs || headTracking,
            version = info.versionName ?: "-",
            installed = info.firstInstallTime,
            updated = info.lastUpdateTime,
            apkBytes = runCatching { File(appInfo.sourceDir).length() }.getOrDefault(0L),
        )
    }

    fun launchIntent(ctx: Context, pkg: String): Intent? {
        val pm = ctx.packageManager
        pm.getLaunchIntentForPackage(pkg)?.let { return it }
        val vr = Intent(Intent.ACTION_MAIN).addCategory(VR_CATEGORY).setPackage(pkg)
        val ri = pm.queryIntentActivities(vr, 0).firstOrNull() ?: return null
        return vr.setClassName(pkg, ri.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun installerName(pm: PackageManager, installer: String?, system: Boolean): String = when (installer) {
        null -> if (system) "Preinstalada en el sistema" else "ADB / instalación directa"
        OCMS -> "Meta Horizon Store"
        "com.android.packageinstaller", "com.google.android.packageinstaller" -> "Instalador de APK"
        else -> runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(installer, 0)).toString()
        }.getOrDefault(installer)
    }

    private fun Drawable.toSafeBitmap(): Bitmap {
        val w = intrinsicWidth.takeIf { it in 1..512 } ?: 192
        val h = intrinsicHeight.takeIf { it in 1..512 } ?: 192
        return toBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

    // Fondo de la tarjeta: el icono reducido a 5x5 y vuelto a ampliar queda como un desenfoque suave.
    private fun backdropOf(bmp: Bitmap, tint: Color): Bitmap {
        val base = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        Canvas(base).apply {
            drawColor(tint.toArgb())
            drawBitmap(Bitmap.createScaledBitmap(bmp, 40, 40, true), -4f, -4f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val tiny = Bitmap.createScaledBitmap(base, 5, 5, true)
        return Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(tiny, 20, 20, true), 80, 80, true)
    }

    // Color medio del icono, dando más peso a los píxeles saturados.
    private fun dominant(bmp: Bitmap): Color {
        val s = Bitmap.createScaledBitmap(bmp, 24, 24, true)
        var r = 0f; var g = 0f; var b = 0f; var wsum = 0f
        val hsv = FloatArray(3)
        for (y in 0 until 24) for (x in 0 until 24) {
            val p = s.getPixel(x, y)
            if ((p ushr 24) and 0xFF < 128) continue
            android.graphics.Color.colorToHSV(p, hsv)
            val w = 0.12f + hsv[1] * hsv[2] * 2f
            r += ((p shr 16) and 0xFF) * w; g += ((p shr 8) and 0xFF) * w; b += (p and 0xFF) * w; wsum += w
        }
        if (wsum == 0f) return Color(0xFF6B7682)
        return Color(r / wsum / 255f, g / wsum / 255f, b / wsum / 255f)
    }
}
