package com.kyrnforge.kyrndeck

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings

data class UsageInfo(val last: Long, val opens: Int, val foregroundMs: Long)

object SystemUsage {
    private const val YEAR_MS = 365L * 24 * 60 * 60 * 1000

    fun granted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    fun load(ctx: Context): Map<String, Pair<Long, Long>> = runCatching {
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val now = System.currentTimeMillis()
        usm.queryAndAggregateUsageStats(now - YEAR_MS, now)
            .mapValues { (_, s) -> s.lastTimeUsed to s.totalTimeInForeground }
            .filterValues { it.first > 0L || it.second > 0L }
    }.getOrDefault(emptyMap())

    fun merge(own: Map<String, Usage>, sys: Map<String, Pair<Long, Long>>): Map<String, UsageInfo> =
        (own.keys + sys.keys).associateWith { pkg ->
            val o = own[pkg]; val s = sys[pkg]
            UsageInfo(maxOf(o?.last ?: 0L, s?.first ?: 0L), o?.count ?: 0, s?.second ?: 0L)
        }

    fun requestAccess(ctx: Context): Boolean {
        val pkgUri = Uri.parse("package:${ctx.packageName}")
        val tries = listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkgUri),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        )
        for (i in tries) {
            if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
        }
        return false
    }
}

fun duration(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "menos de 1 min"
        min < 60 -> "$min min"
        min % 60 == 0L -> "${min / 60} h"
        else -> "${min / 60} h ${min % 60} min"
    }
}
