package com.zaluea.a17boost

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val BG = Color.parseColor("#0E1014")
    private val CARD = Color.parseColor("#181B22")
    private val CARD2 = Color.parseColor("#20242D")
    private val TEXT = Color.parseColor("#F2F4F8")
    private val MUTED = Color.parseColor("#9AA3B2")
    private val GOOD = Color.parseColor("#3DDC84")
    private val WARN = Color.parseColor("#FFB020")
    private val BAD = Color.parseColor("#FF5A5F")
    private val ACCENT = Color.parseColor("#4C8DFF")

    private lateinit var content: LinearLayout
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var busy = false
    private var benchText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            clipToPadding = false
        }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        setContentView(scroll)
        window.decorView.setBackgroundColor(BG)

        // Handle the status bar / gesture bar (edge-to-edge on Android 15).
        val pad = dp(16)
        content.setPadding(pad, pad + dp(24), pad, pad + dp(24))
        scroll.setOnApplyWindowInsetsListener { _, ins ->
            val (top, bottom) = if (Build.VERSION.SDK_INT >= 30) {
                val b = ins.getInsets(WindowInsets.Type.systemBars())
                b.top to b.bottom
            } else {
                @Suppress("DEPRECATION")
                ins.systemWindowInsetTop to ins.systemWindowInsetBottom
            }
            content.setPadding(pad, pad + top, pad, pad + bottom)
            ins
        }
    }

    override fun onResume() {
        super.onResume()
        refresh() // re-scan whenever the user comes back from a Settings screen
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ scanning

    private fun refresh() {
        if (busy) return
        busy = true
        if (content.childCount == 0) showLoading()
        worker.execute {
            val result = runCatching { Scanner(this).scan() }
            main.post {
                busy = false
                if (isFinishing || isDestroyed) return@post
                result.onSuccess { render(it) }.onFailure {
                    content.removeAllViews()
                    content.addView(text("Scan failed: ${it.message}", 15f, BAD))
                }
            }
        }
    }

    private fun showLoading() {
        content.removeAllViews()
        content.addView(header())
        content.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(80), 0, 0)
            addView(ProgressBar(this@MainActivity))
            addView(text("Checking what's slowing your phone…", 15f, MUTED).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0)
            })
        })
    }

    private fun render(s: Scan) {
        content.removeAllViews()
        content.addView(header())
        content.addView(healthCard(s))

        // Quick actions
        content.addView(row(
            button("⚡ Free up RAM", ACCENT) { freeRam() },
            button("↻ Rescan", CARD2) { showLoading(); refresh() },
        ).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(12) })

        // ---- Lag causes ----
        content.addView(section("What's causing the most lag",
            "Ranked from biggest cause to smallest. Fix the top ones first."))
        if (s.issues.isEmpty()) {
            content.addView(card().apply {
                addView(text("✅ No big problems found", 17f, GOOD, bold = true))
                addView(text("Your phone looks healthy right now. If it still feels slow, run the speed test below and check the extra tips at the bottom.", 14f, MUTED).apply { topPad(6) })
            })
        } else {
            s.issues.forEachIndexed { i, issue -> content.addView(issueCard(i + 1, issue)) }
        }

        // ---- Heaviest apps ----
        content.addView(section("Heaviest apps",
            if (s.hasUsage) "Apps using the most background time, cache and storage."
            else "Turn on app analysis (above) to see every app. Showing known heavy apps only."))
        if (s.hogs.isEmpty()) {
            content.addView(card().apply {
                addView(text(if (s.hasUsage) "No app is hogging your phone right now. 👍" else "No known heavy apps found.", 14f, MUTED))
            })
        } else {
            s.hogs.forEach { content.addView(hogCard(it)) }
        }

        // ---- Speed test ----
        content.addView(section("Speed test",
            "Measures how fast your processor is running right now vs. its best. A big drop means it's overheating or being held back."))
        content.addView(card().apply {
            val t = text(benchSummary(), 14f, MUTED)
            benchText = t
            addView(t)
            addView(button("▶ Run speed test (2 sec)", CARD2) { runBench() }.apply { topPad(10) })
        })

        // ---- Static tips ----
        content.addView(section("More fixes for Galaxy A17", "Things the app can't check but that often help."))
        content.addView(card().apply {
            TIPS.forEachIndexed { i, (title, body) ->
                if (i > 0) addView(divider())
                addView(text(title, 15f, TEXT, bold = true))
                addView(text(body, 13.5f, MUTED).apply { topPad(3) })
            }
        })

        content.addView(text("Rescans automatically when you come back from Settings.", 12f, MUTED).apply {
            gravity = Gravity.CENTER; topPad(18)
        })
    }

    // ------------------------------------------------------------------ cards

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), 0, dp(4), dp(14))
        addView(text("A17 Speed Doctor", 26f, TEXT, bold = true))
        addView(text("${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}", 13f, MUTED))
    }

    private fun healthCard(s: Scan) = card().apply {
        val color = scoreColor(s.health)
        val verdict = when {
            s.health >= 85 -> "Running well"
            s.health >= 65 -> "A bit slow"
            s.health >= 40 -> "Slow"
            else -> "Very slow"
        }
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text("${s.health}", 54f, color, bold = true))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, 0, 0)
                addView(text("Speed health", 13f, MUTED))
                addView(text(verdict, 20f, TEXT, bold = true))
                val n = s.issues.count { it.severity >= 50 }
                addView(text(if (n == 0) "No major lag causes" else "$n major lag cause${if (n > 1) "s" else ""} found", 13f, MUTED))
            })
        })
        addView(bar(s.health, color).apply { topPad(12) })

        val ramUsed = 100 - (s.ramAvail * 100 / s.ramTotal).toInt()
        val storUsed = 100 - (s.storFree * 100 / s.storTotal).toInt()
        val grid = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        grid.addView(row(
            stat("RAM used", "$ramUsed%", "${Scanner.fmt(s.ramAvail)} free", level(ramUsed, 70, 85)),
            stat("Storage used", "$storUsed%", "${Scanner.fmt(s.storFree)} free", level(storUsed, 75, 88)),
        ))
        grid.addView(row(
            stat("Temperature", "${"%.1f".format(s.batteryTemp)}°C", if (s.batteryPct >= 0) "Battery ${s.batteryPct}%" else "",
                if (s.batteryTemp >= 40) BAD else if (s.batteryTemp >= 37) WARN else GOOD),
            stat("Since restart", Scanner.dur(s.uptimeMs).let { if (s.uptimeMs >= 86_400_000L) "${s.uptimeMs / 86_400_000L}d" else it },
                "${s.userApps} apps installed", if (s.uptimeMs >= 3 * 86_400_000L) WARN else GOOD),
        ).apply { topPad(8) })
        addView(grid)
    }

    private fun issueCard(rank: Int, i: Issue) = card().apply {
        val color = when {
            i.severity >= 65 -> BAD
            i.severity >= 40 -> WARN
            else -> ACCENT
        }
        val label = when {
            i.severity >= 65 -> "BIG CAUSE"
            i.severity >= 40 -> "MEDIUM"
            else -> "SMALL"
        }
        (background as GradientDrawable).setStroke(dp(1), adjustAlpha(color, 0.45f))

        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text("#$rank", 15f, Color.BLACK, bold = true).apply {
                gravity = Gravity.CENTER
                background = round(color, dp(8))
                setPadding(dp(8), dp(2), dp(8), dp(2))
            })
            addView(text(label, 11f, color, bold = true).apply { setPadding(dp(10), 0, 0, 0); letterSpacing = 0.08f })
        })
        addView(text(i.title, 17f, TEXT, bold = true).apply { topPad(8) })
        addView(bar(i.severity, color).apply { topPad(8) })
        addView(text(i.detail, 14f, MUTED).apply { topPad(8) })
        addView(text("What to do", 13f, color, bold = true).apply { topPad(10) })
        addView(text(i.todo, 14f, TEXT).apply { topPad(2) })
        if (i.fix != Fix.NONE) {
            addView(button(i.fixLabel, if (i.severity >= 40) color else CARD2,
                if (i.severity >= 40) Color.BLACK else TEXT) { runFix(i.fix) }.apply { topPad(12) })
        }
    }

    private fun hogCard(h: Hog) = card().apply {
        val color = scoreColor(100 - h.score)
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(h.label, 16f, TEXT, bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text("lag score ${h.score}", 12f, color, bold = true))
        })
        addView(bar(h.score, color).apply { topPad(6) })
        h.reasons.forEach { addView(text("• $it", 13.5f, MUTED).apply { topPad(4) }) }
        addView(button("Manage app (force stop · clear cache · restrict)", CARD2) {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", h.pkg, null)))
        }.apply { topPad(10) })
    }

    // ------------------------------------------------------------------ actions

    private fun runFix(f: Fix) {
        when (f) {
            Fix.FREE_RAM -> freeRam()
            Fix.STORAGE -> open(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
            Fix.DEV_OPTIONS -> open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            Fix.ABOUT_PHONE -> open(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
            Fix.BATTERY_SAVER -> open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            Fix.DEVICE_CARE -> openDeviceCare()
            Fix.WALLPAPER -> open(Intent(Intent.ACTION_SET_WALLPAPER))
            Fix.USAGE_ACCESS -> open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            Fix.APP_LIST -> open(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
            Fix.DISPLAY -> open(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            Fix.NONE -> Unit
        }
    }

    private fun open(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
        }
    }

    /** Samsung's "Device care" screen; falls back to normal Settings on other phones. */
    private fun openDeviceCare() {
        val tries = listOf(
            Intent().setComponent(ComponentName("com.samsung.android.lool",
                "com.samsung.android.sm.ui.dashboard.SmartManagerDashBoardActivity")),
            packageManager.getLaunchIntentForPackage("com.samsung.android.lool"),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (t in tries) {
            if (t == null) continue
            try { startActivity(t); return } catch (_: Exception) {}
        }
    }

    private fun freeRam() {
        Toast.makeText(this, "Freeing RAM…", Toast.LENGTH_SHORT).show()
        worker.execute {
            val am = getSystemService(ActivityManager::class.java)
            val sc = Scanner(this)
            val before = sc.ramInfo().availMem
            val pkgs = runCatching { packageManager.getInstalledApplications(0) }.getOrDefault(emptyList())
            for (ai in pkgs) {
                if (ai.packageName == packageName) continue
                runCatching { am.killBackgroundProcesses(ai.packageName) }
            }
            SystemClock.sleep(1200)
            val after = sc.ramInfo().availMem
            val freed = (after - before).coerceAtLeast(0)
            main.post {
                if (isFinishing) return@post
                Toast.makeText(this,
                    if (freed > 20L * 1_048_576L) "Freed ${Scanner.fmt(freed)} of RAM" else "RAM was already about as free as it gets",
                    Toast.LENGTH_LONG).show()
                refresh()
            }
        }
    }

    private fun benchSummary(): String {
        val p = getSharedPreferences("bench", MODE_PRIVATE)
        val last = p.getLong("last", 0)
        val best = p.getLong("best", 0)
        if (last == 0L) return "Not run yet. Run it once when the phone is cool to set a baseline, then again whenever it feels slow."
        val pct = (last * 100 / best.coerceAtLeast(1)).toInt()
        val verdict = when {
            pct >= 90 -> "Full speed. If it still feels slow, the cause is RAM, storage or background apps (see above)."
            pct >= 75 -> "Slightly slower than its best — probably warm or busy with background apps."
            else -> "Running much slower than its best! It's probably hot (throttling) or Power saving is on. Let it cool down and close games/camera."
        }
        return "Last result: $last pts  ·  Best: $best pts\nRunning at $pct% of its best speed.\n$verdict"
    }

    private fun runBench() {
        benchText?.text = "Testing… keep the screen on."
        Thread {
            val score = Bench.run(2000)
            val p = getSharedPreferences("bench", MODE_PRIVATE)
            val best = maxOf(p.getLong("best", 0), score)
            p.edit().putLong("last", score).putLong("best", best).apply()
            main.post { benchText?.text = benchSummary() }
        }.start()
    }

    // ------------------------------------------------------------------ view helpers

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun View.topPad(v: Int) {
        val lp = (layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(v)
        layoutParams = lp
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        setLineSpacing(0f, 1.15f)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun round(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat()
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(CARD, dp(18))
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(12) }
    }

    private fun section(title: String, sub: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(18), dp(4), dp(10))
        addView(text(title, 20f, TEXT, bold = true))
        addView(text(sub, 13f, MUTED).apply { topPad(2) })
    }

    private fun button(label: String, bg: Int, fg: Int = TEXT, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14.5f
        setTextColor(fg)
        typeface = Typeface.DEFAULT_BOLD
        background = round(bg, dp(14))
        stateListAnimator = null
        minHeight = dp(48)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setOnClickListener { onClick() }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        views.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = dp(8)
            })
        }
    }

    private fun stat(label: String, value: String, sub: String, color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(CARD2, dp(14))
        setPadding(dp(12), dp(10), dp(12), dp(10))
        addView(text(label, 12f, MUTED))
        addView(text(value, 20f, color, bold = true))
        if (sub.isNotEmpty()) addView(text(sub, 12f, MUTED))
    }

    private fun bar(pct: Int, color: Int) = FrameLayout(this).apply {
        background = round(CARD2, dp(4))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8))
        val fill = View(this@MainActivity).apply { background = round(color, dp(4)) }
        addView(fill, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        post {
            val full = this.width
            val lp = fill.layoutParams
            lp.width = full * pct.coerceIn(2, 100) / 100
            fill.layoutParams = lp
        }
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(CARD2)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(12); bottomMargin = dp(12)
        }
    }

    private fun scoreColor(health: Int) = when {
        health >= 80 -> GOOD
        health >= 55 -> WARN
        else -> BAD
    }

    private fun level(used: Int, warnAt: Int, badAt: Int) = when {
        used >= badAt -> BAD
        used >= warnAt -> WARN
        else -> GOOD
    }

    private fun adjustAlpha(color: Int, a: Float) =
        Color.argb((255 * a).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        val TIPS = listOf(
            "Turn off RAM Plus" to "Settings › Device care › Memory › RAM Plus › Off, then restart. RAM Plus uses slow storage as fake RAM; on budget phones turning it off often makes app switching smoother.",
            "Update the software" to "Settings › Software update › Download and install. Samsung often ships speed and heat fixes. Also update apps in the Play Store.",
            "Use Auto optimization" to "Settings › Device care › ⋮ › Automation › turn on Auto optimize daily and Auto restart.",
            "Put apps you rarely use to sleep" to "Settings › Battery › Background usage limits › Put unused apps to sleep (on) and add apps to Deep sleeping apps.",
            "Keep the home screen light" to "Remove widgets you don't need (weather, news, Google feed). Swipe right on the home screen and turn off Google Discover / Samsung Free if you don't use it.",
            "Turn off Motion smoothness if it gets hot" to "Settings › Display › Motion smoothness › Standard. 60Hz feels a bit less smooth but runs cooler, which avoids throttling.",
            "Clear the keyboard and Google app cache" to "Settings › Apps › Samsung Keyboard / Google / Play Store › Storage › Clear cache. A laggy keyboard is often fixed by this.",
            "Don't use \"cleaner\" or \"booster\" apps" to "Other cleaner/antivirus apps run in the background all the time and make phones slower. Uninstall them — Device care already does the job.",
            "Last resort: factory reset" to "If it's still slow after everything above, back up (Samsung Cloud / Google One / Smart Switch) and reset: Settings › General management › Reset › Factory data reset.",
        )
    }
}
