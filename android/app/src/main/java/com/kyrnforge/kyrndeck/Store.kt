package com.kyrnforge.kyrndeck

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode(val label: String) { AUTO("Auto"), LIGHT("Claro"), DARK("Oscuro") }
enum class Sort(val label: String) { NAME("A-Z"), RECENT("Recientes"), MOST("Más usadas"), INSTALLED("Nuevas") }
enum class Filter(val label: String) {
    ALL("Todas"), FAVS("Favoritas"), STORE("Tienda"), SIDELOAD("Desconocidas"),
    VR("Juegos VR"), FLAT("Apps 2D"), SYSTEM("Sistema")
}

data class Usage(val last: Long, val count: Int)

class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("kyrndeck", Context.MODE_PRIVATE)
    private val usagePrefs = ctx.getSharedPreferences("kyrndeck_usage", Context.MODE_PRIVATE)

    var theme by mutableStateOf(enumOr(prefs.getString("theme", null), ThemeMode.AUTO))
        private set
    var sort by mutableStateOf(enumOr(prefs.getString("sort", null), Sort.NAME))
        private set
    var filter by mutableStateOf(enumOr(prefs.getString("filter", null), Filter.ALL))
        private set
    var favorites by mutableStateOf(prefs.getStringSet("favs", null)?.toSet() ?: emptySet())
        private set
    val usage = mutableStateMapOf<String, Usage>().apply {
        usagePrefs.all.forEach { (k, v) ->
            val parts = (v as? String)?.split(',') ?: return@forEach
            if (parts.size == 2) put(k, Usage(parts[0].toLongOrNull() ?: 0, parts[1].toIntOrNull() ?: 0))
        }
    }

    fun pickTheme(m: ThemeMode) { theme = m; prefs.edit().putString("theme", m.name).apply() }
    fun pickSort(s: Sort) { sort = s; prefs.edit().putString("sort", s.name).apply() }
    fun pickFilter(f: Filter) { filter = f; prefs.edit().putString("filter", f.name).apply() }

    fun toggleFav(pkg: String) {
        favorites = if (pkg in favorites) favorites - pkg else favorites + pkg
        prefs.edit().putStringSet("favs", favorites).apply()
    }

    fun opened(pkg: String) {
        val u = Usage(System.currentTimeMillis(), (usage[pkg]?.count ?: 0) + 1)
        usage[pkg] = u
        usagePrefs.edit().putString(pkg, "${u.last},${u.count}").apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, def: T): T =
        runCatching { enumValueOf<T>(name!!) }.getOrDefault(def)
}
