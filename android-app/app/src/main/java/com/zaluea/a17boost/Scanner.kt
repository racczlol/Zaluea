package com.zaluea.a17boost

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.WallpaperManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.Settings
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What the "Fix" button on a lag cause opens. */
enum class Fix {
    FREE_RAM, STORAGE, DEV_OPTIONS, ABOUT_PHONE, BATTERY_SAVER, DEVICE_CARE,
    WALLPAPER, USAGE_ACCESS, APP_LIST, DISPLAY, NONE
}

data class Issue(
    val title: String,
    val detail: String,
    val todo: String,
    val severity: Int,           // 0..100, higher = causes more lag
    val fix: Fix,
    val fixLabel: String,
)

data class Hog(
    val pkg: String,
    val label: String,
    val score: Int,
    val reasons: List<String>,
)

data class Scan(
    val ramTotal: Long,
    val ramAvail: Long,
    val storTotal: Long,
    val storFree: Long,
    val batteryTemp: Float,
    val batteryPct: Int,
    val uptimeMs: Long,
    val userApps: Int,
    val hasUsage: Boolean,
    val issues: List<Issue>,
    val hogs: List<Hog>,
    val health: Int,
)

/** Apps that are well known to hammer RAM / run lots of background services on budget phones. */
private val HEAVY_APPS = mapOf(
    "com.facebook.katana" to "Facebook is one of the heaviest apps on Android. Try Facebook Lite or use facebook.com in Chrome.",
    "com.facebook.orca" to "Messenger keeps services running all the time. Restrict its background use.",
    "com.facebook.appmanager" to "Facebook App Manager runs in the background just to update Facebook apps. Disable it.",
    "com.facebook.system" to "Facebook App Installer runs in the background. Disable it if you can.",
    "com.facebook.services" to "Facebook Services runs in the background. Disable it if you can.",
    "com.zhiliaoapp.musically" to "TikTok uses lots of RAM and storage. Try TikTok Lite and clear its cache often.",
    "com.ss.android.ugc.trill" to "TikTok uses lots of RAM and storage. Try TikTok Lite and clear its cache often.",
    "com.instagram.android" to "Instagram is heavy on RAM and cache. Clear its cache regularly.",
    "com.snapchat.android" to "Snapchat is very heavy on budget phones (camera + background services).",
    "com.roblox.client" to "Roblox makes the phone hot, which slows everything down. Close it fully after playing.",
    "com.supercell.clashofclans" to "Games keep running in the background. Close them fully after playing.",
    "com.tencent.ig" to "PUBG Mobile makes the phone hot and fills storage. Close it fully after playing.",
    "com.dts.freefireth" to "Free Fire makes the phone hot. Close it fully after playing.",
    "com.dts.freefiremax" to "Free Fire MAX is heavy for this phone. Try regular Free Fire or lower settings.",
    "com.activision.callofduty.shooter" to "Call of Duty Mobile is very heavy for this phone. Use low graphics settings.",
    "com.miHoYo.GenshinImpact" to "Genshin Impact is too heavy for this phone and makes it very hot.",
    "com.netflix.mediaclient" to "Netflix downloads can fill up your storage. Delete downloaded episodes you've watched.",
    "com.spotify.music" to "Spotify's offline downloads and cache can take several GB.",
    "com.whatsapp" to "WhatsApp media can fill storage fast. Use WhatsApp › Settings › Storage and data › Manage storage.",
)

class Scanner(private val ctx: Context) {

    fun hasUsageAccess(): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun ramInfo(): ActivityManager.MemoryInfo {
        val am = ctx.getSystemService(ActivityManager::class.java)
        return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    }

    fun scan(): Scan {
        val issues = mutableListOf<Issue>()

        // ---------- RAM ----------
        val mi = ramInfo()
        val ramFreePct = (mi.availMem * 100 / max(1L, mi.totalMem)).toInt()
        val ramSev = when {
            mi.lowMemory || ramFreePct < 12 -> 90
            ramFreePct < 20 -> 70
            ramFreePct < 30 -> 45
            else -> 0
        }
        if (ramSev > 0) issues += Issue(
            "Not enough free RAM",
            "Only ${fmt(mi.availMem)} of ${fmt(mi.totalMem)} RAM is free ($ramFreePct%). When RAM runs out, Android has to keep killing and reloading apps — that's the stutter you feel when switching apps or opening the keyboard.",
            "Tap \"Free up RAM\". Close apps you're not using in the Recents screen (Close all). Restrict or uninstall the apps listed under \"Heaviest apps\" below. Also try turning off RAM Plus: Settings › Device care › Memory › RAM Plus › Off, then restart.",
            ramSev, Fix.FREE_RAM, "Free up RAM"
        )

        // ---------- Storage ----------
        val st = StatFs(Environment.getDataDirectory().path)
        val storTotal = st.totalBytes
        val storFree = st.availableBytes
        val storFreePct = (storFree * 100 / max(1L, storTotal)).toInt()
        val storSev = when {
            storFreePct < 8 -> 95
            storFreePct < 15 -> 80
            storFreePct < 25 -> 45
            else -> 0
        }
        if (storSev > 0) issues += Issue(
            "Storage is almost full",
            "Only ${fmt(storFree)} free (${storFreePct}%). Phone storage gets MUCH slower when it's nearly full, and apps can't save their caches. This is one of the most common reasons a Galaxy A phone gets slow.",
            "Keep at least 20% free. Delete old videos and downloads, back photos up (Google Photos › Free up space), clear WhatsApp media (WhatsApp › Settings › Storage and data › Manage storage), and uninstall apps you don't use.",
            storSev, Fix.STORAGE, "Open storage"
        )

        // ---------- Heat / thermal throttling ----------
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0) level * 100 / max(1, scale) else -1
        val pm = ctx.getSystemService(PowerManager::class.java)
        val thermal = if (Build.VERSION.SDK_INT >= 29) pm.currentThermalStatus else 0
        val heatSev = max(
            when {
                temp >= 43f -> 90
                temp >= 39f -> 60
                temp >= 37f -> 25
                else -> 0
            },
            when {
                thermal >= 3 -> 95   // SEVERE+
                thermal == 2 -> 75   // MODERATE
                thermal == 1 -> 45   // LIGHT
                else -> 0
            }
        )
        if (heatSev > 0) issues += Issue(
            "Phone is hot — the CPU is slowing itself down",
            "Battery is at ${"%.1f".format(temp)}°C${thermalText(thermal)}. When the phone heats up, the processor throttles (runs slower on purpose) to cool down, so everything lags.",
            "Take the case off while gaming or charging, don't play games while charging, lower the brightness, close the camera and games, and keep it out of the sun. Setting Display › Motion smoothness to Standard also reduces heat.",
            heatSev, Fix.DISPLAY, "Display settings"
        )

        // ---------- Power saving ----------
        if (pm.isPowerSaveMode) issues += Issue(
            "Power saving is ON",
            "Power saving mode limits the CPU speed and background activity to save battery, which makes the phone feel slower.",
            "Turn off Power saving when you need speed (swipe down › Power saving). Turn it back on when the battery is low.",
            55, Fix.BATTERY_SAVER, "Battery settings"
        )

        // ---------- Uptime ----------
        val uptime = SystemClock.elapsedRealtime()
        val days = uptime / 86_400_000L
        val upSev = when {
            days >= 7 -> 55
            days >= 3 -> 35
            else -> 0
        }
        if (upSev > 0) issues += Issue(
            "Not restarted in $days days",
            "Over time, stuck background tasks and memory leaks build up. A restart clears all of them.",
            "Restart now: hold the Side key + Volume down › Restart. Turn on automatic restarts: Settings › Device care › ⋮ › Automation › Auto restart at set times.",
            upSev, Fix.DEVICE_CARE, "Open Device care"
        )

        // ---------- Animations ----------
        val cr = ctx.contentResolver
        val animScale = listOf(
            Settings.Global.WINDOW_ANIMATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            Settings.Global.ANIMATOR_DURATION_SCALE,
        ).maxOf { runCatching { Settings.Global.getFloat(cr, it, 1f) }.getOrDefault(1f) }
        val devOn = runCatching {
            Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        }.getOrDefault(false)
        if (animScale >= 1f) issues += Issue(
            "Animations are at full length (${trim(animScale)}x)",
            "Every time you open an app or go home, the phone plays an animation first. At 1x this makes the phone FEEL slow even when it isn't.",
            if (devOn) "In Developer options, set Window animation scale, Transition animation scale and Animator duration scale to 0.5x."
            else "1) Settings › About phone › Software information › tap \"Build number\" 7 times.\n2) Go to Settings › Developer options.\n3) Set Window animation scale, Transition animation scale and Animator duration scale to 0.5x.",
            35,
            if (devOn) Fix.DEV_OPTIONS else Fix.ABOUT_PHONE,
            if (devOn) "Developer options" else "Open About phone"
        )

        // ---------- Live wallpaper ----------
        val live = runCatching { WallpaperManager.getInstance(ctx).wallpaperInfo != null }.getOrDefault(false)
        if (live) issues += Issue(
            "Live (moving) wallpaper",
            "A live wallpaper keeps using the CPU/GPU whenever the home screen is visible.",
            "Switch to a normal picture wallpaper.",
            30, Fix.WALLPAPER, "Change wallpaper"
        )

        // ---------- Apps ----------
        val pkgMgr = ctx.packageManager
        val installed = runCatching { pkgMgr.getInstalledApplications(0) }.getOrDefault(emptyList())
        val userApps = installed.filter {
            it.packageName != ctx.packageName && it.enabled &&
                (it.flags and ApplicationInfo.FLAG_SYSTEM == 0 ||
                    it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 ||
                    HEAVY_APPS.containsKey(it.packageName))
        }
        val trulyUserApps = userApps.count { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }

        val hasUsage = hasUsageAccess()
        val hogs = mutableListOf<Hog>()
        var totalCache = 0L
        val bgApps = mutableListOf<Pair<String, Long>>()
        val unused = mutableListOf<String>()

        var day: Map<String, UsageStats> = emptyMap()
        var month: Map<String, UsageStats> = emptyMap()
        val now = System.currentTimeMillis()
        if (hasUsage) {
            val usm = ctx.getSystemService(UsageStatsManager::class.java)
            day = runCatching { usm.queryAndAggregateUsageStats(now - 86_400_000L, now) }.getOrDefault(emptyMap())
            month = runCatching { usm.queryAndAggregateUsageStats(now - 30L * 86_400_000L, now) }.getOrDefault(emptyMap())
        }
        val ssm = if (hasUsage) ctx.getSystemService(StorageStatsManager::class.java) else null

        for (ai in userApps) {
            val pkg = ai.packageName
            val label = runCatching { pkgMgr.getApplicationLabel(ai).toString() }.getOrDefault(pkg)
            var score = 0
            val reasons = mutableListOf<String>()

            if (hasUsage) {
                val d = day[pkg]
                val bgMs = if (Build.VERSION.SDK_INT >= 29 && d != null) d.totalTimeForegroundServiceUsed else 0L
                val bgMin = bgMs / 60_000L
                if (bgMin >= 30) {
                    score += min(50, (bgMin / 10 + 15).toInt())
                    reasons += "Ran in the background for ${dur(bgMs)} in the last 24h"
                    bgApps += label to bgMs
                }

                val stats = runCatching {
                    ssm!!.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle())
                }.getOrNull()
                if (stats != null) {
                    totalCache += stats.cacheBytes
                    val cacheMb = stats.cacheBytes / 1_048_576L
                    if (cacheMb >= 300) {
                        score += min(25, (cacheMb / 100).toInt() + 5)
                        reasons += "${fmt(stats.cacheBytes)} of cache (safe to clear)"
                    }
                    val size = stats.appBytes + stats.dataBytes
                    if (size >= 1_073_741_824L) {
                        score += min(15, (size / 1_073_741_824L * 4).toInt())
                        reasons += "Takes up ${fmt(size + stats.cacheBytes)} of storage"
                    }
                }

                val firstInstall = runCatching { pkgMgr.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(now)
                val lastUsed = month[pkg]?.lastTimeUsed ?: 0L
                val oldEnough = now - firstInstall > 30L * 86_400_000L
                if (oldEnough && lastUsed < now - 30L * 86_400_000L &&
                    ai.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                    unused += label
                    if (score > 0) {
                        score += 10
                        reasons += "You haven't opened it in 30+ days — uninstall it?"
                    }
                }
            }

            HEAVY_APPS[pkg]?.let {
                score += 25
                reasons += it
            }

            if (score >= 15) hogs += Hog(pkg, label, min(100, score), reasons)
        }
        hogs.sortByDescending { it.score }

        // Issues derived from the app scan
        if (bgApps.isNotEmpty()) {
            val top = bgApps.sortedByDescending { it.second }
            val worstH = top.first().second / 3_600_000L
            issues += Issue(
                "${top.size} app${if (top.size > 1) "s" else ""} keep running in the background",
                "These apps kept working in the background in the last 24h, using RAM and CPU while you use other apps: " +
                    top.take(5).joinToString(", ") { "${it.first} (${dur(it.second)})" } + ".",
                "Put them to sleep: Settings › Battery › Background usage limits › Deep sleeping apps › +. Or tap the app under \"Heaviest apps\" › Manage › Battery › Restricted.",
                when {
                    worstH >= 6 -> 70
                    worstH >= 2 -> 55
                    else -> 40
                },
                Fix.DEVICE_CARE, "Open Device care"
            )
        }
        if (totalCache >= 1_500_000_000L) issues += Issue(
            "Apps are hoarding ${fmt(totalCache)} of cache",
            "Cache is temporary data apps keep around. Big caches eat storage and can make those apps slow to open.",
            "Clear cache on the biggest apps (Heaviest apps › Manage › Storage › Clear cache). Don't press \"Clear data\" — that logs you out.",
            if (storFreePct < 25) 50 else 30,
            Fix.APP_LIST, "Open apps"
        )
        if (unused.size >= 8) issues += Issue(
            "${unused.size} apps you haven't used in 30 days",
            "Unused apps still update, run background jobs and take storage: " + unused.take(6).joinToString(", ") +
                if (unused.size > 6) " and more." else ".",
            "Uninstall the ones you don't need (long-press the icon › Uninstall).",
            35, Fix.APP_LIST, "Open apps"
        ) else if (trulyUserApps > 90) issues += Issue(
            "Lots of apps installed ($trulyUserApps)",
            "Every installed app can wake up in the background to sync or update.",
            "Uninstall apps you don't use.",
            30, Fix.APP_LIST, "Open apps"
        )
        val heavyInstalled = userApps.filter { HEAVY_APPS.containsKey(it.packageName) && !it.packageName.startsWith("com.whatsapp") && !it.packageName.startsWith("com.spotify") && !it.packageName.startsWith("com.netflix") }
        if (heavyInstalled.isNotEmpty()) issues += Issue(
            "Heavy apps installed",
            "These apps are known to be hard on 4–6 GB phones: " +
                heavyInstalled.take(6).joinToString(", ") { runCatching { pkgMgr.getApplicationLabel(it).toString() }.getOrDefault(it.packageName) } + ".",
            "Use the Lite versions (Facebook Lite, TikTok Lite), use the website in Chrome instead, or at least add them to Deep sleeping apps. Close games completely after playing.",
            min(60, 25 + heavyInstalled.size * 7), Fix.APP_LIST, "Open apps"
        )

        if (!hasUsage) issues += Issue(
            "Turn on app analysis for a full scan",
            "Without Usage data access, the app can't see which apps run in the background or how much cache they hold — usually a big part of the lag.",
            "Tap the button, find \"A17 Speed Doctor\" in the list and turn it on, then come back.",
            20, Fix.USAGE_ACCESS, "Allow access"
        )

        issues.sortByDescending { it.severity }

        // Health score: the worst problem counts most.
        val sev = issues.filter { it.fix != Fix.USAGE_ACCESS }.map { it.severity }
        val penalty = sev.mapIndexed { i, s ->
            s * when (i) { 0 -> 0.45; 1 -> 0.22; 2 -> 0.12; else -> 0.05 }
        }.sum()
        val health = (100 - penalty).roundToInt().coerceIn(5, 100)

        return Scan(
            mi.totalMem, mi.availMem, storTotal, storFree, temp, batteryPct, uptime,
            trulyUserApps, hasUsage, issues, hogs.take(10), health
        )
    }

    private fun thermalText(t: Int) = when {
        t >= 3 -> " and Android reports SEVERE overheating"
        t == 2 -> " and Android reports the phone is throttling"
        t == 1 -> " and Android reports it's getting warm"
        else -> ""
    }

    private fun trim(f: Float) = if (f == f.toInt().toFloat()) f.toInt().toString() else f.toString()

    companion object {
        fun fmt(bytes: Long): String = when {
            bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
            else -> "${bytes / 1_048_576L} MB"
        }

        fun dur(ms: Long): String {
            val m = ms / 60_000L
            return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
        }
    }
}
