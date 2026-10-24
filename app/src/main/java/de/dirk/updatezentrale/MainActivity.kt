package de.dirk.updatezentrale

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Outline
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainActivity : Activity() {

    private companion object {
        const val PKG_PLAY = "com.android.vending"
        const val PKG_GALAXY = "com.sec.android.app.samsungapps"
        const val PKG_GMS = "com.google.android.gms"
        const val PKG_MAINLINE = "com.google.android.modulemetadata"
        const val PKG_WEARABLE = "com.samsung.android.app.watchmanager"
        const val PATCH_WARN_DAYS = 60L
        val PLAY_KEYWORDS = listOf("update", "aktualis", "myapps", "my_apps", "my apps", "meine apps", "manage", "verwalten")
        val GALAXY_KEYWORDS = listOf("update", "aktualis")
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }

    private lateinit var ui: Ui
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this, Palette.of(this))
        window.decorView.setBackgroundColor(ui.p.bg)
        lightSystemBars()

        content = ui.vertical().apply { setPadding(ui.dp(16), 0, ui.dp(16), 0) }
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            addView(content)
        }
        // Edge-to-edge: keep content clear of status and navigation bar
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top + ui.dp(16), bars.right, bars.bottom + ui.dp(16))
            } else {
                // Android 10 is not edge-to-edge; the system already keeps bars clear
                v.setPadding(0, ui.dp(16), 0, ui.dp(16))
            }
            insets
        }
        setContentView(scroll)
    }

    // Rebuild on every resume so returning from a store shows fresh versions
    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        content.removeAllViews()
        header()

        content.addView(ui.section("App-Updates"))
        val stores = ui.horizontal()
        stores.addView(ui.weighted(storeTile("Google Play", PKG_PLAY, R.drawable.ic_card_play) {
            open(
                // Store's own long-press shortcut ("My apps"), then legacy action, then start page
                storeShortcut(PKG_PLAY, PLAY_KEYWORDS),
                Intent("com.google.android.finsky.VIEW_MY_DOWNLOADS").setPackage(PKG_PLAY),
                packageManager.getLaunchIntentForPackage(PKG_PLAY)
            )
        }))
        stores.addView(ui.spacer(w = 12))
        stores.addView(ui.weighted(storeTile("Galaxy Store", PKG_GALAXY, R.drawable.ic_card_bag) {
            open(
                // Store's own long-press shortcut ("Update apps"), then deep link, then start page
                storeShortcut(PKG_GALAXY, GALAXY_KEYWORDS),
                Intent(Intent.ACTION_VIEW, Uri.parse("samsungapps://UpdateList/")).setPackage(PKG_GALAXY),
                packageManager.getLaunchIntentForPackage(PKG_GALAXY)
            )
        }))
        content.addView(stores)

        content.addView(ui.section("Google-System"))
        content.addView(actionCard(
            R.drawable.ic_card_shield,
            "Google Play-Systemupdate",
            version(PKG_MAINLINE)?.let { "Modulstand $it" } ?: "In den Einstellungen prüfen"
        ) {
            open(Intent("android.settings.MODULE_UPDATE_SETTINGS"), Intent(Settings.ACTION_SECURITY_SETTINGS))
        })
        content.addView(componentsCard())

        content.addView(ui.section("Wearables"))
        content.addView(actionCard(
            R.drawable.ic_card_watch,
            "Galaxy Wearable",
            version(PKG_WEARABLE)?.let { "Version ${it.substringBefore(" ")} · Ring & Watch: Einstellungen → Software-Update" }
                ?: "Nicht installiert"
        ) {
            open(
                packageManager.getLaunchIntentForPackage(PKG_WEARABLE),
                Intent(Intent.ACTION_VIEW, Uri.parse("samsungapps://ProductDetail/$PKG_WEARABLE")).setPackage(PKG_GALAXY)
            )
        })

        content.addView(ui.section("Gerät"))
        content.addView(systemCard())
    }

    /** One entry of the Google system list: label, package, and what tapping opens. */
    private data class Component(val name: String, val pkg: String, val opensSettings: Boolean = false)

    // Google system components distributed via Play; only installed ones are shown
    private val googleComponents = listOf(
        Component("Google Play-Dienste", PKG_GMS),
        Component("Android System WebView", "com.google.android.webview"),
        Component("Speech Services von Google", "com.google.android.tts"),
        Component("Google Play-Dienste für AR", "com.google.ar.core"),
        Component("Carrier Services", "com.google.android.ims"),
        Component("Private Compute Services", "com.google.android.as.oss"),
        Component("Android System SafetyCore", "com.google.android.safetycore"),
        Component("Android System Key Verifier", "com.google.android.contactkeys"),
        Component("Android Auto", "com.google.android.projection.gearhead")
    )

    private fun componentsCard(): View {
        val card = ui.card()
        val head = ui.horizontal().apply { setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(10)) }
        head.addView(ui.badge(R.drawable.ic_card_grid))
        head.addView(ui.spacer(w = 14))
        val t = ui.vertical()
        t.addView(ui.text("Google-Systemkomponenten", 16f, bold = true))
        t.addView(ui.text("Tippen öffnet die Play-Seite", 13f, ui.p.muted))
        head.addView(ui.weighted(t))
        card.addView(head)

        val present = googleComponents.mapNotNull { c -> version(c.pkg)?.let { c to it } }
        if (present.isEmpty()) {
            card.addView(ui.text("Keine Komponenten gefunden", 14f, ui.p.muted).apply {
                setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(16))
            })
        }
        present.forEachIndexed { i, (c, ver) ->
            card.addView(ui.divider(insetStart = 16))
            val row = ui.horizontal().apply { setPadding(ui.dp(16), ui.dp(12), ui.dp(12), ui.dp(12)) }
            val label = ui.vertical()
            label.addView(ui.text(c.name, 15f))
            // GMS versions carry a long build suffix in brackets; keep the readable part
            label.addView(ui.text(ver.substringBefore(" "), 13f, ui.p.muted))
            row.addView(ui.weighted(label))
            row.addView(ui.chevron())
            // Last row keeps the card's bottom corners
            val radius = if (i == present.lastIndex) 20 else 0
            ui.clickable(row, ui.p.surface, radius) {
                if (c.opensSettings) {
                    open(Intent("android.settings.MODULE_UPDATE_SETTINGS"), Intent(Settings.ACTION_SECURITY_SETTINGS))
                } else {
                    open(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${c.pkg}")).setPackage(PKG_PLAY))
                }
            }
            card.addView(row)
        }
        return card
    }

    // --- sections ---

    private fun header() {
        val row = ui.horizontal()
        row.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(ui.dp(52), ui.dp(52))
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.3f)
            }
            clipToOutline = true
            // Long-press on the icon: shortcut diagnosis for troubleshooting
            setOnLongClickListener { showDiagnosis(); true }
        })
        row.addView(ui.spacer(w = 14))
        val titles = ui.vertical()
        titles.addView(ui.text("Update-Zentrale", 24f, bold = true).apply {
            setOnLongClickListener { showDiagnosis(); true }
        })
        titles.addView(ui.text("${deviceName()} · Stand ${LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))} Uhr", 14f, ui.p.muted))
        row.addView(ui.weighted(titles))
        content.addView(row)

        val patch = patchAge()
        val chip = when {
            patch == null -> ui.chip("Sicherheitspatch ${Build.VERSION.SECURITY_PATCH}", ui.p.muted, ui.p.divider)
            patch > PATCH_WARN_DAYS -> ui.chip("Sicherheitspatch veraltet · $patch Tage", ui.p.danger, ui.p.dangerContainer)
            else -> ui.chip("Sicherheitspatch aktuell · $patch Tage", ui.p.ok, ui.p.okContainer)
        }
        chip.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = ui.dp(16) }
        content.addView(chip)
    }

    private fun storeTile(name: String, pkg: String, icon: Int, onClick: () -> Unit): View {
        val v = version(pkg)
        val tile = ui.vertical().apply { setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(14)) }
        tile.addView(ui.badge(icon))
        tile.addView(ui.text(name, 16f, bold = true).apply { setPadding(0, ui.dp(12), 0, 0) })
        tile.addView(ui.text(v?.let { "Version ${it.substringBefore(" ")}" } ?: "Nicht installiert", 13f, ui.p.muted))
        tile.addView(ui.text(if (v != null) "Updates öffnen" else "–", 14f, ui.p.accent, bold = true).apply {
            setPadding(0, ui.dp(12), 0, 0)
        })
        if (v != null) ui.clickable(tile, ui.p.surface, 20, onClick) else {
            tile.background = ui.rounded(ui.p.surface, 20)
            tile.alpha = 0.6f
        }
        return tile
    }

    private fun actionCard(icon: Int, title: String, subtitle: String, onClick: () -> Unit): View {
        val row = ui.horizontal().apply { setPadding(ui.dp(16), ui.dp(14), ui.dp(12), ui.dp(14)) }
        row.addView(ui.badge(icon))
        row.addView(ui.spacer(w = 14))
        val t = ui.vertical()
        t.addView(ui.text(title, 16f, bold = true))
        t.addView(ui.text(subtitle, 13f, ui.p.muted))
        row.addView(ui.weighted(t))
        row.addView(ui.chevron())
        ui.clickable(row, ui.p.surface, 20, onClick)
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = ui.dp(12) }
        return row
    }

    private fun systemCard(): View {
        val card = ui.card()
        val head = ui.horizontal().apply { setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(6)) }
        head.addView(ui.badge(R.drawable.ic_card_phone))
        head.addView(ui.spacer(w = 14))
        head.addView(ui.text("System", 16f, bold = true))
        card.addView(head)

        val facts = buildList {
            add("Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            oneUiVersion()?.let { add("One UI" to it) }
            add("Sicherheitspatch" to (runCatching { LocalDate.parse(Build.VERSION.SECURITY_PATCH).format(DATE) }
                .getOrDefault(Build.VERSION.SECURITY_PATCH)))
            add("Build" to Build.DISPLAY)
        }
        facts.forEach { (k, v) ->
            val r = ui.horizontal().apply { setPadding(ui.dp(16), ui.dp(6), ui.dp(16), ui.dp(6)) }
            r.addView(ui.text(k, 14f, ui.p.muted).apply { minWidth = ui.dp(130) })
            r.addView(ui.weighted(ui.text(v, 14f)))
            card.addView(r)
        }

        val button = ui.text("Software-Update suchen", 15f, ui.p.onAccentContainer, bold = true).apply {
            gravity = android.view.Gravity.CENTER
            setPadding(0, ui.dp(12), 0, ui.dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(16)) }
        }
        ui.clickable(button, ui.p.accentContainer, 100) {
            open(Intent("android.settings.SYSTEM_UPDATE_SETTINGS"), Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        }
        card.addView(button)
        return card
    }

    // --- data ---

    private fun patchAge(): Long? = runCatching {
        ChronoUnit.DAYS.between(LocalDate.parse(Build.VERSION.SECURITY_PATCH), LocalDate.now())
    }.getOrNull()

    /** User-visible device name (e.g. "Galaxy S25"), falls back to the model code. */
    private fun deviceName(): String =
        Settings.Global.getString(contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: Build.MODEL

    /** Samsung-only: derives the One UI version from Build.VERSION.SEM_PLATFORM_INT. */
    private fun oneUiVersion(): String? = runCatching {
        val sem = Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null)
        val major = (sem - 90000) / 10000
        val minor = (sem % 10000) / 100
        if (major in 1..30) "$major.$minor" else null
    }.getOrNull()

    private fun installed(pkg: String) = version(pkg) != null

    private fun version(pkg: String): String? = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(pkg, 0)
        }
        info.versionName ?: "?"
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    // --- actions ---

    private fun storeShortcut(pkg: String, keywords: List<String>): Intent? =
        StoreShortcuts.find(StoreShortcuts.read(this, pkg), keywords)?.intent

    /** Starts the first intent that resolves; shows a toast if none does. */
    private fun open(vararg candidates: Intent?) {
        for (intent in candidates.filterNotNull()) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                // try next candidate
            } catch (e: SecurityException) {
                // target activity not exported, try next candidate
            }
        }
        Toast.makeText(this, "Konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
    }

    /** Lists all store shortcuts found, for troubleshooting. */
    private fun showDiagnosis() {
        val report = buildString {
            for (pkg in listOf(PKG_PLAY, PKG_GALAXY)) {
                appendLine("== $pkg ${version(pkg) ?: "-"}")
                val list = StoreShortcuts.read(this@MainActivity, pkg)
                if (list.isEmpty()) appendLine("(keine statischen Verknüpfungen)")
                list.forEach { s ->
                    appendLine("• ${s.id} | ${s.label}")
                    appendLine("  ${s.intent?.toUri(0) ?: "kein Intent"}")
                }
                appendLine()
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Diagnose")
            .setMessage(report)
            .setPositiveButton("Kopieren") { _, _ ->
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Diagnose", report))
            }
            .setNegativeButton("Schließen", null)
            .show()
    }

    private fun lightSystemBars() {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT >= 30) {
            val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (night) 0 else flags, flags)
        }
    }
}
