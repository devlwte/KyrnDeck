package com.kyrnforge.kyrndeck

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ES = Locale("es", "ES")
private val R4 = RoundedCornerShape(4.dp)
private val R6 = RoundedCornerShape(6.dp)
private val R8 = RoundedCornerShape(8.dp)

enum class Layout { WIDE, MEDIUM, NARROW }

@Composable
fun KyrnDeckApp(store: Store, reloadKey: Int) {
    val sysDark = isSystemInDarkTheme()
    val dark = when (store.theme) { ThemeMode.AUTO -> sysDark; ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    CompositionLocalProvider(LocalPalette provides if (dark) DarkPalette else LightPalette) {
        Deck(store, reloadKey)
    }
}

@Composable
private fun Deck(store: Store, reloadKey: Int) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<AppEntry?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var sys by remember { mutableStateOf<Map<String, Pair<Long, Long>>>(emptyMap()) }
    var sysOk by remember { mutableStateOf(true) }
    var manualChecks by remember { mutableStateOf(0) }
    var reopenUpdate by remember { mutableStateOf(0) }
    var available by remember { mutableStateOf<String?>(null) }
    val usage = remember(sys, store.usage.toMap()) { SystemUsage.merge(store.usage.toMap(), sys) }

    LaunchedEffect(reloadKey) {
        sysOk = SystemUsage.granted(ctx)
        sys = if (sysOk) withContext(Dispatchers.IO) { SystemUsage.load(ctx) } else emptyMap()
        val fresh = withContext(Dispatchers.IO) { Apps.load(ctx) }
        apps = fresh
        detail = detail?.let { d -> fresh.firstOrNull { it.pkg == d.pkg } }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(3500); toast = null } }

    val launch: (AppEntry) -> Unit = { a ->
        val i = Apps.launchIntent(ctx, a.pkg)
        if (i == null) toast = "${a.label} no tiene una pantalla para abrir"
        else runCatching {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            store.opened(a.pkg)
        }.onFailure { toast = "No se pudo abrir ${a.label}" }
    }
    val requestUsage = {
        if (!SystemUsage.requestAccess(ctx))
            toast = "Actívalo desde el PC: adb shell appops set com.kyrnforge.kyrndeck GET_USAGE_STATS allow"
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(p.bg)) {
        val layout = when {
            maxWidth >= 1040.dp -> Layout.WIDE
            maxWidth >= 720.dp -> Layout.MEDIUM
            else -> Layout.NARROW
        }
        val all = apps.orEmpty()
        Column(Modifier.fillMaxSize()) {
            StatusStrip(store, all, layout, available, onUpdate = { reopenUpdate++ }) {
                manualChecks++
                toast = "Buscando actualizaciones"
            }
            if (layout == Layout.NARROW) FilterTabs(store, all)
            Row(Modifier.fillMaxWidth().weight(1f)) {
                when (layout) {
                    Layout.WIDE -> Rail(store, all)
                    Layout.MEDIUM -> RailCompact(store, all)
                    Layout.NARROW -> Unit
                }
                Library(store, apps, usage, sysOk, query, { query = it }, launch, requestUsage) { detail = it }
            }
        }

        AnimatedVisibility(visible = detail != null, enter = fadeIn(tween(140)), exit = fadeOut(tween(120))) {
            Box(
                Modifier.fillMaxSize().background(p.scrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { detail = null },
                contentAlignment = Alignment.Center
            ) {
                detail?.let { d ->
                    DetailSheet(d, store, usage[d.pkg], sysOk, onOpen = { launch(d); detail = null }, onClose = { detail = null })
                }
            }
        }

        UpdatePrompt(reloadKey, manualChecks, reopenUpdate, onAvailable = { available = it }) { toast = it }

        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) {
            Row(
                Modifier.clip(R6).background(p.raised).border(1.dp, p.lineStrong, R6),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(3.dp).height(44.dp).background(p.accent))
                BasicText(toast.orEmpty(), style = body(14.sp, p.ink), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}

// Barra superior

@Composable
private fun StatusStrip(
    store: Store, apps: List<AppEntry>, layout: Layout,
    available: String?, onUpdate: () -> Unit, onVersion: () -> Unit,
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(10_000) } }
    val (battery, charging) = remember(now) { batteryOf(ctx) }
    val free = remember(now) { runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(0L) }

    Row(
        Modifier.fillMaxWidth().height(50.dp).background(p.surface).bottomRule(p.line).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Logo(20.dp)
        Spacer(Modifier.width(10.dp))
        BasicText("KYRNDECK", style = cond(19.sp, p.ink, spacing = 0.05.em))
        VersionTag(onVersion)
        if (layout == Layout.WIDE) {
            Spacer(Modifier.width(18.dp))
            Box(Modifier.width(1.dp).height(22.dp).background(p.line))
            Spacer(Modifier.width(18.dp))
            Readout("Apps", apps.count { it.source != Source.SYSTEM }.toString())
            Readout("Tienda", apps.count { it.source == Source.STORE }.toString(), p.steel)
            Readout("Desconocidas", apps.count { it.source == Source.SIDELOAD }.toString(), p.accent)
            Readout("VR", apps.count { it.vr && it.source != Source.SYSTEM }.toString(), p.green)
        }
        Spacer(Modifier.weight(1f))
        if (layout != Layout.NARROW) Readout("Libre", bytes(free))
        Readout(if (charging) "Cargando" else "Batería", "$battery%", if (battery <= 20 && !charging) p.red else p.ink)
        BasicText(SimpleDateFormat("HH:mm", ES).format(Date(now)), style = cond(20.sp, p.ink), modifier = Modifier.padding(end = 12.dp))
        if (available != null) UpdateButton(available, layout != Layout.NARROW, onUpdate)
        ThemeButton(store)
    }
}

// Número de versión; al tocarlo busca actualizaciones.
@Composable
private fun VersionTag(onClick: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val version = remember { appVersion(ctx) }
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    BasicText(
        "v$version", style = cond(12.sp, if (hover) p.ink else p.muted, FontWeight.Bold),
        modifier = Modifier.padding(start = 8.dp).clip(R4)
            .border(1.dp, if (hover) p.lineStrong else p.line, R4)
            .hoverable(src).clickable(src, null, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

// Aparece cuando hay una versión nueva y se queda hasta que se instala.
@Composable
private fun UpdateButton(version: String, wide: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    Row(
        Modifier.padding(end = 10.dp).height(32.dp).clip(R4)
            .background(if (hover) p.accent.copy(alpha = 0.85f) else p.accent)
            .hoverable(src).clickable(src, null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(12.dp)) {
            val w = 1.8.dp.toPx()
            drawLine(p.onAccent, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height * 0.7f), w)
            drawLine(p.onAccent, Offset(size.width * 0.2f, size.height * 0.42f), Offset(size.width / 2, size.height * 0.72f), w)
            drawLine(p.onAccent, Offset(size.width * 0.8f, size.height * 0.42f), Offset(size.width / 2, size.height * 0.72f), w)
            drawLine(p.onAccent, Offset(0f, size.height), Offset(size.width, size.height), w)
        }
        Spacer(Modifier.width(8.dp))
        BasicText(
            if (wide) "Actualizar a $version" else version,
            style = cond(14.sp, p.onAccent, FontWeight.Bold, 0.04.em)
        )
    }
}

@Composable
private fun Readout(label: String, value: String, color: Color = LocalPalette.current.ink) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
        BasicText(label.uppercase(ES), style = cond(11.sp, p.faint, FontWeight.Bold, 0.1.em))
        Spacer(Modifier.width(6.dp))
        BasicText(value, style = cond(16.sp, color))
    }
}

@Composable
private fun ThemeButton(store: Store) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    val next = ThemeMode.entries[(store.theme.ordinal + 1) % ThemeMode.entries.size]
    Row(
        Modifier.height(32.dp).clip(R4).border(1.dp, if (hover) p.lineStrong else p.line, R4)
            .background(if (hover) p.surface2 else Color.Transparent)
            .hoverable(src).clickable(src, null) { store.pickTheme(next) }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(14.dp)) {
            val r = size.minDimension / 2
            drawCircle(p.ink, r, style = Stroke(1.5.dp.toPx()))
            when (store.theme) {
                ThemeMode.DARK -> drawCircle(p.ink, r)
                ThemeMode.AUTO -> drawArc(p.ink, 90f, 180f, true)
                ThemeMode.LIGHT -> Unit
            }
        }
        Spacer(Modifier.width(8.dp))
        BasicText(store.theme.label.uppercase(ES), style = cond(13.sp, p.ink, FontWeight.Bold, 0.08.em))
    }
}

private fun batteryOf(ctx: Context): Pair<Int, Boolean> {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0 to false
    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
    return (level * 100 / scale) to (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
}

// Filtros

fun matches(a: AppEntry, f: Filter, favs: Set<String>): Boolean = when (f) {
    Filter.ALL -> a.source != Source.SYSTEM
    Filter.FAVS -> a.pkg in favs
    Filter.STORE -> a.source == Source.STORE
    Filter.SIDELOAD -> a.source == Source.SIDELOAD
    Filter.VR -> a.vr && a.source != Source.SYSTEM
    Filter.FLAT -> !a.vr && a.source != Source.SYSTEM
    Filter.SYSTEM -> a.source == Source.SYSTEM
}

@Composable
private fun Rail(store: Store, apps: List<AppEntry>) {
    val p = LocalPalette.current
    Column(
        Modifier.width(224.dp).fillMaxHeight().background(p.surface).rightRule(p.line)
            .verticalScroll(rememberScrollState()).padding(vertical = 14.dp)
    ) {
        Caption("Biblioteca", Modifier.padding(start = 18.dp, bottom = 6.dp))
        Filter.entries.forEachIndexed { i, f ->
            if (f == Filter.SYSTEM) Caption("Otros", Modifier.padding(start = 18.dp, top = 16.dp, bottom = 6.dp))
            RailItem(i + 1, f, apps.count { matches(it, f, store.favorites) }, store.filter == f) { store.pickFilter(f) }
        }
    }
}

@Composable
private fun RailItem(index: Int, f: Filter, count: Int, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when { selected -> p.select; hover -> p.surface2; else -> Color.Transparent }, tween(120), label = "rail"
    )
    Row(
        Modifier.fillMaxWidth().height(42.dp).background(bg)
            .hoverable(src).clickable(src, null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(if (selected) p.accent else Color.Transparent))
        Spacer(Modifier.width(16.dp))
        BasicText("%02d".format(index), style = cond(12.sp, if (selected) p.accent else p.faint, FontWeight.Bold), modifier = Modifier.width(22.dp))
        Glyph(f, if (selected) p.ink else p.muted, 16.dp)
        Spacer(Modifier.width(10.dp))
        BasicText(
            f.label, style = body(15.sp, if (selected || hover) p.ink else p.muted, if (selected) FontWeight.Bold else FontWeight.SemiBold),
            modifier = Modifier.weight(1f), maxLines = 1
        )
        BasicText(count.toString(), style = cond(15.sp, if (selected) p.accent else p.faint, FontWeight.Bold), modifier = Modifier.padding(end = 18.dp))
    }
}

@Composable
private fun RailCompact(store: Store, apps: List<AppEntry>) {
    val p = LocalPalette.current
    Column(
        Modifier.width(74.dp).fillMaxHeight().background(p.surface).rightRule(p.line)
            .verticalScroll(rememberScrollState()).padding(vertical = 8.dp)
    ) {
        Filter.entries.forEach { f ->
            val selected = store.filter == f
            val src = remember { MutableInteractionSource() }
            val hover by src.collectIsHoveredAsState()
            Row(
                Modifier.fillMaxWidth().height(60.dp)
                    .background(if (selected) p.select else if (hover) p.surface2 else Color.Transparent)
                    .hoverable(src).clickable(src, null) { store.pickFilter(f) }
            ) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(if (selected) p.accent else Color.Transparent))
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Glyph(f, if (selected) p.accent else p.muted, 20.dp)
                    Spacer(Modifier.height(5.dp))
                    BasicText(apps.count { matches(it, f, store.favorites) }.toString(),
                        style = cond(12.sp, if (selected) p.ink else p.faint, FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun FilterTabs(store: Store, apps: List<AppEntry>) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().background(p.surface).bottomRule(p.line).horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
    ) {
        Filter.entries.forEach { f ->
            Tab("${f.label}  ${apps.count { matches(it, f, store.favorites) }}", store.filter == f, Modifier.height(44.dp)) { store.pickFilter(f) }
        }
    }
}

@Composable
private fun Tab(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    Box(
        modifier.hoverable(src).clickable(src, null, onClick = onClick).padding(horizontal = 10.dp)
            .drawBehind {
                if (selected) drawRect(p.accent, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
            },
        contentAlignment = Alignment.Center
    ) {
        BasicText(text, style = body(14.sp, if (selected || hover) p.ink else p.muted, if (selected) FontWeight.Bold else FontWeight.SemiBold), maxLines = 1)
    }
}

// Biblioteca

@Composable
private fun Library(
    store: Store, apps: List<AppEntry>?, usage: Map<String, UsageInfo>, sysOk: Boolean,
    query: String, onQuery: (String) -> Unit,
    launch: (AppEntry) -> Unit, onRequestUsage: () -> Unit, showDetail: (AppEntry) -> Unit,
) {
    val p = LocalPalette.current
    val collator = remember { Collator.getInstance(ES).apply { strength = Collator.PRIMARY } }
    val all = apps.orEmpty()
    val q = query.trim()
    val shown = remember(all, store.filter, store.sort, store.favorites, usage, q) {
        val base = if (q.isNotEmpty()) all.filter { it.label.contains(q, true) || it.pkg.contains(q, true) }
                   else all.filter { matches(it, store.filter, store.favorites) }
        val byName = base.sortedWith { a, b -> collator.compare(a.label, b.label) }
        when (store.sort) {
            Sort.NAME -> byName
            Sort.RECENT -> byName.sortedByDescending { usage[it.pkg]?.last ?: 0L }
            Sort.MOST -> byName.sortedWith(
                compareByDescending<AppEntry> { usage[it.pkg]?.foregroundMs ?: 0L }.thenByDescending { usage[it.pkg]?.opens ?: 0 })
            Sort.INSTALLED -> byName.sortedByDescending { it.installed }
        }
    }
    val showcase = q.isEmpty() && store.filter == Filter.ALL
    val recent = remember(all, usage) {
        all.filter { it.source != Source.SYSTEM && (usage[it.pkg]?.last ?: 0L) > 0L }
            .sortedByDescending { usage[it.pkg]!!.last }.take(10)
    }
    val favs = remember(all, store.favorites) {
        all.filter { it.pkg in store.favorites }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
    var hideHint by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    LaunchedEffect(store.filter, q, store.sort) { grid.scrollToItem(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val paneW = maxWidth
        val tight = paneW < 620.dp
        val pad = if (tight) 14.dp else 26.dp
        Column(Modifier.fillMaxSize().padding(start = pad, end = pad, top = if (tight) 14.dp else 22.dp)) {
            if (tight) {
                Title(store, q, shown.size, true)
                Spacer(Modifier.height(10.dp))
                SearchField(query, onQuery, Modifier.fillMaxWidth())
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) { Title(store, q, shown.size, false) }
                    Spacer(Modifier.width(16.dp))
                    SearchField(query, onQuery, Modifier.width(minOf(340.dp, paneW * 0.45f)))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Caption("Ordenar", Modifier.padding(end = 4.dp))
                Sort.entries.forEach { s -> Tab(s.label, store.sort == s, Modifier.height(38.dp)) { store.pickSort(s) } }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            if (!sysOk && !hideHint) UsageHint(tight, onRequestUsage) { hideHint = true }

            if (apps == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText("Cargando apps", style = body(16.sp, p.muted))
                }
            } else {
                val gap = if (tight) 10.dp else 14.dp
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (tight) 124.dp else 152.dp),
                    state = grid,
                    contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val cardW = if (tight) 220.dp else 256.dp
                    if (showcase && recent.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h-recent") { Section("Continuar", recent.size) }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "recent") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                items(recent, key = { "r-" + it.pkg }) { a ->
                                    val u = usage[a.pkg]
                                    val sub = ago(u?.last ?: 0L) + if ((u?.foregroundMs ?: 0L) >= 60_000) ", " + duration(u!!.foregroundMs) else ""
                                    ListCard(a, sub, cardW, onClick = { launch(a) }, onMore = { showDetail(a) })
                                }
                            }
                        }
                    }
                    if (showcase && favs.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h-favs") { Section("Favoritas", favs.size) }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "favs") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                items(favs, key = { "f-" + it.pkg }) { a ->
                                    ListCard(a, sourceLabel(a.source) + (if (a.vr) ", VR" else ", 2D"), cardW,
                                        onClick = { launch(a) }, onMore = { showDetail(a) })
                                }
                            }
                        }
                    }
                    if (showcase && (recent.isNotEmpty() || favs.isNotEmpty())) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h-all") { Section("Todas", shown.size) }
                    }
                    if (shown.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "empty") { EmptyState(q.isNotEmpty(), store.filter) }
                    }
                    items(shown, key = { it.pkg }) { a ->
                        AppTile(a, a.pkg in store.favorites, onClick = { launch(a) }, onMore = { showDetail(a) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(store: Store, q: String, count: Int, tight: Boolean) {
    val p = LocalPalette.current
    Column {
        Caption(if (q.isNotEmpty()) "Resultados" else "Biblioteca")
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(
                if (q.isNotEmpty()) q else store.filter.label,
                style = cond(if (tight) 30.sp else 38.sp, p.ink), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(10.dp))
            BasicText(count.toString(), style = cond(if (tight) 18.sp else 22.sp, p.accent), modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun UsageHint(tight: Boolean, onEnable: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(R6).background(p.surface).border(1.dp, p.line, R6),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height(if (tight) 96.dp else 58.dp).background(p.accent))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            BasicText("Falta el acceso al uso", style = body(15.sp, p.ink, FontWeight.Bold))
            BasicText("Sin él, Recientes y Más usadas solo cuentan lo que abres desde aquí.",
                style = body(13.sp, p.muted, FontWeight.Medium))
        }
        Spacer(Modifier.width(10.dp))
        Btn("Activar", BtnKind.PRIMARY, onClick = onEnable)
        CloseButton(onDismiss, Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun Section(title: String, count: Int) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        BasicText(title.uppercase(ES), style = cond(14.sp, p.ink, spacing = 0.12.em))
        Spacer(Modifier.width(8.dp))
        BasicText(count.toString(), style = cond(14.sp, p.faint, FontWeight.Bold))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(p.line))
    }
}

@Composable
private fun EmptyState(searching: Boolean, f: Filter) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(if (searching) "Sin resultados" else "Vacío", style = cond(24.sp, p.ink))
        Spacer(Modifier.height(6.dp))
        BasicText(
            when {
                searching -> "Prueba con otra palabra o con parte del paquete."
                f == Filter.FAVS -> "Abre el detalle de una app y añádela a favoritas."
                else -> "Aquí aparecerán las apps de este tipo cuando las instales."
            },
            style = body(14.sp, p.muted, FontWeight.Medium)
        )
    }
}

// Tarjetas

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(a: AppEntry, fav: Boolean, onClick: () -> Unit, onMore: () -> Unit) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    val edge by animateColorAsState(if (hover) p.lineStrong else p.line, tween(140), label = "edge")
    Column(
        Modifier.clip(R8).background(p.surface).border(1.dp, edge, R8)
            .hoverable(src)
            .combinedClickable(interactionSource = src, indication = null, onLongClick = onMore, onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.22f)) {
            Backdrop(a, hover, fadeTo = p.surface)
            AppIcon(a, hover, Modifier.fillMaxWidth(0.5f).aspectRatio(1f).align(Alignment.Center).offset(y = (-4).dp))
            Chip(if (a.vr) "VR" else "2D", Modifier.align(Alignment.TopStart).padding(8.dp))
            if (fav) Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(R4).background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center
            ) { Canvas(Modifier.size(12.dp)) { drawPath(star(size.minDimension / 2), p.accent) } }
            if (hover) MoreButton(onMore, Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 11.dp)) {
            BasicText(a.label, style = body(14.sp, p.ink, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(sourceColor(a.source)))
                Spacer(Modifier.width(6.dp))
                BasicText(sourceLabel(a.source).uppercase(ES), style = cond(11.sp, p.muted, FontWeight.Bold, 0.08.em))
            }
        }
    }
}

// Fondo hecho con el propio icono desenfocado, velado para que el icono destaque.
@Composable
private fun BoxScope.Backdrop(a: AppEntry, hover: Boolean, fadeTo: Color, horizontal: Boolean = false) {
    val p = LocalPalette.current
    val veil by animateFloatAsState(
        if (p.dark) (if (hover) 0.18f else 0.32f) else (if (hover) 0.12f else 0.24f), tween(160), label = "veil"
    )
    Image(a.backdrop, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
    Box(Modifier.matchParentSize().background((if (p.dark) Color.Black else Color.White).copy(alpha = veil)))
    Box(
        Modifier.matchParentSize().background(
            if (horizontal) Brush.horizontalGradient(0.5f to Color.Transparent, 1f to fadeTo)
            else Brush.verticalGradient(0.55f to Color.Transparent, 1f to fadeTo)
        )
    )
}

@Composable
private fun AppIcon(a: AppEntry, hover: Boolean, modifier: Modifier, corner: Dp = 16.dp) {
    val scale by animateFloatAsState(if (hover) 1.07f else 1f, tween(160), label = "icon")
    val shape = RoundedCornerShape(corner)
    Image(
        a.icon, a.label,
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (hover) 14.dp else 8.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
    )
}

@Composable
private fun Chip(text: String, modifier: Modifier) {
    BasicText(
        text, style = cond(11.sp, Color(0xFFF2EFEA), FontWeight.Bold, 0.1.em),
        modifier = modifier.clip(R4).background(Color.Black.copy(alpha = 0.42f)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListCard(a: AppEntry, sub: String, w: Dp, onClick: () -> Unit, onMore: () -> Unit) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    val edge by animateColorAsState(if (hover) p.lineStrong else p.line, tween(120), label = "edge")
    Row(
        Modifier.width(w).height(72.dp).clip(R8).background(p.surface).border(1.dp, edge, R8)
            .hoverable(src)
            .combinedClickable(interactionSource = src, indication = null, onLongClick = onMore, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(84.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Backdrop(a, hover, fadeTo = p.surface, horizontal = true)
            AppIcon(a, hover, Modifier.size(44.dp), corner = 11.dp)
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            BasicText(a.label, style = body(14.sp, p.ink, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            BasicText(sub, style = body(12.sp, p.muted, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (hover) MoreButton(onMore, Modifier.padding(end = 8.dp)) else Spacer(Modifier.width(10.dp))
    }
}

@Composable
private fun MoreButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    Box(
        modifier.size(28.dp).clip(R4)
            .background(if (hover) p.accent else p.raised)
            .border(1.dp, if (hover) p.accent else p.lineStrong, R4)
            .hoverable(src).clickable(src, null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val dot = if (hover) p.onAccent else p.ink
        Canvas(Modifier.size(14.dp, 4.dp)) {
            for (i in 0..2) drawCircle(dot, 1.6.dp.toPx(), Offset(size.width * (0.15f + i * 0.35f), size.height / 2))
        }
    }
}

@Composable
private fun sourceColor(s: Source): Color {
    val p = LocalPalette.current
    return when (s) {
        Source.STORE -> p.steel
        Source.SIDELOAD -> p.accent
        Source.SYSTEM -> p.muted
    }
}

fun sourceLabel(s: Source) = when (s) {
    Source.STORE -> "Tienda"
    Source.SIDELOAD -> "Desconocida"
    Source.SYSTEM -> "Sistema"
}

// Detalle

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailSheet(a: AppEntry, store: Store, usage: UsageInfo?, sysOk: Boolean, onOpen: () -> Unit, onClose: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val fav = a.pkg in store.favorites
    val date = remember { SimpleDateFormat("d MMM yyyy", ES) }

    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, 660.dp)
        val tight = w < 520.dp
        val rows = listOf(
            "Origen" to sourceLabel(a.source),
            "Instalada por" to a.installer,
            "Tipo" to if (a.vr) "Realidad virtual" else "Panel 2D",
            "Versión" to a.version,
            "Tamaño del APK" to bytes(a.apkBytes),
            "Instalada" to date.format(Date(a.installed)),
            "Actualizada" to date.format(Date(a.updated)),
            "Última vez" to if (usage == null || usage.last == 0L) "Sin abrir" else ago(usage.last),
            "Tiempo de uso, último año" to when {
                !sysOk -> "Falta el acceso al uso"
                usage == null || usage.foregroundMs == 0L -> "Sin registro"
                else -> duration(usage.foregroundMs)
            },
            "Abierta desde KyrnDeck" to (usage?.opens ?: 0).let { "$it ${if (it == 1) "vez" else "veces"}" },
        )
        Column(
            Modifier.width(w).heightIn(max = maxHeight).clip(R6).background(p.surface).border(1.dp, p.lineStrong, R6)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .verticalScroll(rememberScrollState())
        ) {
            Box(Modifier.fillMaxWidth()) {
            Backdrop(a, hover = false, fadeTo = p.surface)
            Row(Modifier.fillMaxWidth().padding(if (tight) 16.dp else 22.dp)) {
                AppIcon(a, false, Modifier.size(if (tight) 64.dp else 88.dp), corner = if (tight) 14.dp else 18.dp)
                Spacer(Modifier.width(if (tight) 14.dp else 18.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(a.label, style = cond(if (tight) 24.sp else 30.sp, p.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    BasicText(a.pkg, style = body(13.sp, p.faint, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        BasicText(sourceLabel(a.source).uppercase(ES), style = cond(12.sp, sourceColor(a.source), FontWeight.Bold, 0.08.em))
                        BasicText("  /  ", style = cond(12.sp, p.line, FontWeight.Bold))
                        BasicText(if (a.vr) "VR" else "2D", style = cond(12.sp, if (a.vr) p.green else p.muted, FontWeight.Bold, 0.08.em))
                        if (fav) {
                            BasicText("  /  ", style = cond(12.sp, p.line, FontWeight.Bold))
                            BasicText("FAVORITA", style = cond(12.sp, p.accent, FontWeight.Bold, 0.08.em))
                        }
                    }
                }
                CloseButton(onClose, Modifier)
            }
            }
            rows.chunked(if (tight) 1 else 2).forEach { pair ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                Row(Modifier.fillMaxWidth()) {
                    pair.forEachIndexed { i, (label, value) ->
                        if (i > 0) Box(Modifier.width(1.dp).height(56.dp).background(p.line))
                        Column(Modifier.weight(1f).height(56.dp).padding(horizontal = 18.dp), verticalArrangement = Arrangement.Center) {
                            BasicText(label.uppercase(ES), style = cond(11.sp, p.faint, FontWeight.Bold, 0.1.em), maxLines = 1)
                            Spacer(Modifier.height(2.dp))
                            BasicText(value, style = body(14.sp, p.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (pair.size == 1 && !tight) Spacer(Modifier.weight(1f))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            FlowRow(
                Modifier.fillMaxWidth().padding(if (tight) 14.dp else 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Btn("Abrir", BtnKind.PRIMARY, onClick = onOpen)
                Btn(if (fav) "Quitar de favoritas" else "Añadir a favoritas", BtnKind.GHOST) { store.toggleFav(a.pkg) }
                Btn("Ajustes de Android", BtnKind.GHOST) {
                    runCatching {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.pkg, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                if (a.source != Source.SYSTEM) Btn("Desinstalar", BtnKind.DANGER) {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    Box(
        modifier.size(32.dp).clip(R4).background(if (hover) p.surface2 else Color.Transparent)
            .hoverable(src).clickable(src, null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val c = if (hover) p.ink else p.muted
        Canvas(Modifier.size(11.dp)) {
            val w = 1.8.dp.toPx()
            drawLine(c, Offset.Zero, Offset(size.width, size.height), w, StrokeCap.Square)
            drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Square)
        }
    }
}

enum class BtnKind { PRIMARY, GHOST, DANGER }

@Composable
fun Btn(text: String, kind: BtnKind, onClick: () -> Unit) {
    val p = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    val (bg, fg, edge) = when (kind) {
        BtnKind.PRIMARY -> Triple(if (hover) p.accent.copy(alpha = 0.85f) else p.accent, p.onAccent, p.accent)
        BtnKind.GHOST -> Triple(if (hover) p.surface2 else Color.Transparent, p.ink, if (hover) p.lineStrong else p.line)
        BtnKind.DANGER -> Triple(if (hover) p.redSoft else Color.Transparent, p.red, if (hover) p.red else p.line)
    }
    Box(
        Modifier.height(40.dp).clip(R4).background(bg).border(1.dp, edge, R4)
            .hoverable(src).clickable(src, null, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) { BasicText(text, style = body(14.sp, fg, FontWeight.Bold), maxLines = 1) }
}

// Controles

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = body(15.sp, p.ink), cursorBrush = SolidColor(p.accent),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier.height(42.dp).clip(R4).background(p.surface)
                    .border(1.dp, if (value.isEmpty()) p.line else p.accent, R4).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(15.dp)) {
                    val w = 1.8.dp.toPx()
                    drawCircle(p.muted, size.width * 0.33f, Offset(size.width * 0.42f, size.height * 0.42f), style = Stroke(w))
                    drawLine(p.muted, Offset(size.width * 0.67f, size.height * 0.67f), Offset(size.width, size.height), w, StrokeCap.Square)
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) BasicText("Buscar", style = body(15.sp, p.faint, FontWeight.Medium))
                    inner()
                }
                if (value.isNotEmpty()) CloseButton({ onChange("") }, Modifier)
            }
        }
    )
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    BasicText(text.uppercase(ES), style = cond(11.sp, p.faint, FontWeight.Bold, 0.16.em), modifier = modifier)
}

private fun Modifier.rightRule(c: Color) =
    drawBehind { drawLine(c, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), 1f) }

private fun Modifier.bottomRule(c: Color) =
    drawBehind { drawLine(c, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f) }

// Dibujos

@Composable
private fun Logo(s: Dp) {
    val p = LocalPalette.current
    Canvas(Modifier.size(s)) {
        val g = size.width * 0.12f
        val t = (size.width - g) / 2
        drawRect(p.accent, Offset(0f, 0f), Size(t, t))
        drawRect(p.lineStrong, Offset(t + g, 0f), Size(t, t))
        drawRect(p.lineStrong, Offset(0f, t + g), Size(t, t))
        drawRect(p.muted, Offset(t + g, t + g), Size(t, t))
    }
}

private fun DrawScope.star(r: Float): Path = Path().apply {
    val c = center
    for (i in 0 until 10) {
        val ang = -PI / 2 + i * PI / 5
        val rad = if (i % 2 == 0) r else r * 0.45f
        val x = c.x + (rad * cos(ang)).toFloat()
        val y = c.y + (rad * sin(ang)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
private fun Glyph(f: Filter, c: Color, s: Dp) {
    Canvas(Modifier.size(s)) {
        val w = size.width
        val st = Stroke(width = w * 0.09f, cap = StrokeCap.Square)
        when (f) {
            Filter.ALL -> {
                val t = w * 0.42f; val g = w * 0.16f
                drawRect(c, Offset(0f, 0f), Size(t, t), style = st)
                drawRect(c, Offset(t + g, 0f), Size(t, t), style = st)
                drawRect(c, Offset(0f, t + g), Size(t, t), style = st)
                drawRect(c, Offset(t + g, t + g), Size(t, t))
            }
            Filter.FAVS -> drawPath(star(w * 0.5f), c, style = st)
            Filter.STORE -> {
                drawRect(c, Offset(w * 0.1f, w * 0.34f), Size(w * 0.8f, w * 0.6f), style = st)
                drawArc(c, 180f, 180f, false, Offset(w * 0.3f, w * 0.08f), Size(w * 0.4f, w * 0.5f), style = st)
            }
            Filter.SIDELOAD -> {
                drawLine(c, Offset(w / 2, w * 0.04f), Offset(w / 2, w * 0.62f), st.width)
                drawLine(c, Offset(w * 0.26f, w * 0.4f), Offset(w / 2, w * 0.64f), st.width)
                drawLine(c, Offset(w * 0.74f, w * 0.4f), Offset(w / 2, w * 0.64f), st.width)
                drawLine(c, Offset(w * 0.06f, w * 0.92f), Offset(w * 0.94f, w * 0.92f), st.width)
            }
            Filter.VR -> {
                drawRect(c, Offset(w * 0.04f, w * 0.28f), Size(w * 0.92f, w * 0.46f), style = st)
                drawCircle(c, w * 0.1f, Offset(w * 0.3f, w * 0.51f))
                drawCircle(c, w * 0.1f, Offset(w * 0.7f, w * 0.51f))
            }
            Filter.FLAT -> {
                drawRect(c, Offset(w * 0.06f, w * 0.14f), Size(w * 0.88f, w * 0.58f), style = st)
                drawLine(c, Offset(w * 0.3f, w * 0.9f), Offset(w * 0.7f, w * 0.9f), st.width)
            }
            Filter.SYSTEM -> {
                drawCircle(c, w * 0.18f, center, style = st)
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    drawLine(c, Offset(center.x + (w * 0.32f * cos(a)).toFloat(), center.y + (w * 0.32f * sin(a)).toFloat()),
                        Offset(center.x + (w * 0.46f * cos(a)).toFloat(), center.y + (w * 0.46f * sin(a)).toFloat()), st.width)
                }
            }
        }
    }
}

// Formatos

fun ago(t: Long): String {
    if (t <= 0L) return "nunca"
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 1 -> "ahora"
        m < 60 -> "hace $m min"
        m < 60 * 24 -> "hace ${m / 60} h"
        m < 60 * 24 * 30 -> "hace ${m / 60 / 24} d"
        else -> SimpleDateFormat("d MMM yyyy", ES).format(Date(t))
    }
}

fun bytes(b: Long): String = when {
    b <= 0 -> "-"
    b < 1_000_000 -> "${b / 1000} KB"
    b < 1_000_000_000 -> String.format(ES, "%.1f MB", b / 1e6)
    else -> String.format(ES, "%.1f GB", b / 1e9)
}
