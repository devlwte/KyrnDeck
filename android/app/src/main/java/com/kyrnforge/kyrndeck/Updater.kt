package com.kyrnforge.kyrndeck

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Al abrir la app mira GitHub Releases, como mucho una vez cada 4 horas.
// Desde la barra de arriba (el número de versión) se puede buscar a mano.
private const val EVERY_MS = 4 * 60 * 60 * 1000L

data class Release(val version: String, val apkUrl: String, val size: Long, val notes: String)

private sealed interface Check {
    data class Found(val r: Release) : Check
    data object UpToDate : Check
    data object Offline : Check
}

fun isNewer(remote: String, local: String): Boolean {
    fun parts(v: String) = v.trimStart('v', 'V').substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val a = parts(remote)
    val b = parts(local)
    for (i in 0 until maxOf(a.size, b.size)) {
        val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (d != 0) return d > 0
    }
    return false
}

private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
    connectTimeout = 10_000
    readTimeout = 20_000
    setRequestProperty("Accept", "application/vnd.github+json")
    setRequestProperty("User-Agent", "KyrnDeck-Android")
}

private fun check(current: String): Check = runCatching {
    val c = open(BuildConfig.UPDATE_URL)
    try {
        when (c.responseCode) {
            200 -> {}
            404 -> return Check.UpToDate
            else -> return Check.Offline
        }
        val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val tag = json.optString("tag_name")
        if (!isNewer(tag, current)) return Check.UpToDate
        val assets = json.optJSONArray("assets") ?: return Check.UpToDate
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) } ?: return Check.UpToDate
        Check.Found(Release(tag.trimStart('v', 'V'), apk.getString("browser_download_url"), apk.optLong("size"), json.optString("body")))
    } finally {
        c.disconnect()
    }
}.getOrDefault(Check.Offline)

private fun download(ctx: Context, r: Release, onProgress: (Float) -> Unit): File? = runCatching {
    val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "KyrnDeck-${r.version}.apk")
    val c = open(r.apkUrl)
    try {
        if (c.responseCode != 200) return null
        val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
        c.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        file
    } finally {
        c.disconnect()
    }
}.getOrNull()

private fun install(ctx: Context, file: File) {
    val installer = ctx.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        .apply { setAppPackageName(ctx.packageName) }
    val id = installer.createSession(params)
    installer.openSession(id).use { session ->
        file.inputStream().use { input ->
            session.openWrite("kyrndeck.apk", 0, file.length()).use { out ->
                input.copyTo(out)
                session.fsync(out)
            }
        }
        val done = PendingIntent.getBroadcast(
            ctx, id, Intent(ctx, InstallResult::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        session.commit(done.intentSender)
    }
}

// Si Android pide que el usuario confirme la instalación, abrimos su pantalla.
class InstallResult : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) != PackageInstaller.STATUS_PENDING_USER_ACTION) return
        val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
        confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

private fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

private fun askInstallPermission(ctx: Context) = runCatching {
    ctx.startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}.isSuccess

private sealed interface UpdateState {
    data class Found(val r: Release) : UpdateState
    data class Downloading(val r: Release, val progress: Float) : UpdateState
    data class NeedsPermission(val r: Release, val file: File) : UpdateState
    data class Failed(val r: Release) : UpdateState
}

fun appVersion(ctx: Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

@Composable
fun UpdatePrompt(manualChecks: Int, onInfo: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateState?>(null) }

    LaunchedEffect(manualChecks) {
        val manual = manualChecks > 0
        val prefs = ctx.getSharedPreferences("kyrndeck_update", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!manual && now - prefs.getLong("last_check", 0) < EVERY_MS) return@LaunchedEffect
        prefs.edit().putLong("last_check", now).apply()
        when (val result = withContext(Dispatchers.IO) { check(appVersion(ctx)) }) {
            is Check.Found -> state = UpdateState.Found(result.r)
            Check.UpToDate -> if (manual) onInfo("Tienes la última versión")
            Check.Offline -> if (manual) onInfo("No se pudo conectar con GitHub")
        }
    }

    fun start(r: Release) {
        state = UpdateState.Downloading(r, 0f)
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                download(ctx, r) { p -> scope.launch { state = UpdateState.Downloading(r, p) } }
            }
            state = when {
                file == null -> UpdateState.Failed(r)
                !canInstall(ctx) -> UpdateState.NeedsPermission(r, file)
                else -> { install(ctx, file); null }
            }
        }
    }

    when (val s = state) {
        null -> {}
        is UpdateState.Found -> UpdateDialog(
            title = "KyrnDeck ${s.r.version}",
            text = "Tienes la ${appVersion(ctx)}. La nueva ocupa " + (if (s.r.size > 0) "${bytes(s.r.size)}." else "poco."),
            notes = s.r.notes,
            ok = "Actualizar", onOk = { start(s.r) }, cancel = "Más tarde", onCancel = { state = null }
        )
        is UpdateState.Downloading -> UpdateDialog(
            title = "Descargando ${s.r.version}", text = "Tarda unos segundos.", progress = s.progress
        )
        is UpdateState.NeedsPermission -> UpdateDialog(
            title = "Falta un permiso",
            text = "Para instalar, permite que KyrnDeck instale apps y vuelve aquí. " +
                "Si el visor no muestra esa opción, desde el PC: adb shell appops set com.kyrnforge.kyrndeck REQUEST_INSTALL_PACKAGES allow",
            ok = "Continuar", onOk = {
                if (canInstall(ctx)) { install(ctx, s.file); state = null }
                else if (!askInstallPermission(ctx)) onInfo("El visor no tiene esa pantalla, usa el comando desde el PC")
            },
            cancel = "Más tarde", onCancel = { state = null }
        )
        is UpdateState.Failed -> UpdateDialog(
            title = "No se pudo descargar",
            text = "Revisa la conexión e inténtalo otra vez.",
            ok = "Reintentar", onOk = { start(s.r) }, cancel = "Cerrar", onCancel = { state = null }
        )
    }
}

@Composable
private fun UpdateDialog(
    title: String,
    text: String,
    notes: String = "",
    ok: String? = null,
    onOk: () -> Unit = {},
    cancel: String? = null,
    onCancel: () -> Unit = {},
    progress: Float? = null,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.fillMaxSize().background(p.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(16.dp).widthIn(max = 460.dp).fillMaxWidth().clip(shape)
                .background(p.surface).border(1.dp, p.lineStrong, shape)
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(p.accent))
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(title, style = cond(26.sp, p.ink))
                BasicText(text, style = body(14.sp, p.muted, FontWeight.Medium))
                val shortNotes = notes.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(6)
                if (shortNotes.isNotEmpty()) Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(p.surface2).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    shortNotes.forEach { BasicText(it.trimStart('-', '*', ' '), style = body(13.sp, p.ink, FontWeight.Medium)) }
                }
                if (progress != null) {
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).background(p.line)) {
                        Box(Modifier.fillMaxWidth(progress).height(4.dp).background(p.accent))
                    }
                    BasicText("${(progress * 100).toInt()}%", style = cond(15.sp, p.ink))
                }
                if (ok != null || cancel != null) Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    if (cancel != null) Btn(cancel, BtnKind.GHOST, onClick = onCancel)
                    if (ok != null) Btn(ok, BtnKind.PRIMARY, onClick = onOk)
                }
            }
        }
    }
}
