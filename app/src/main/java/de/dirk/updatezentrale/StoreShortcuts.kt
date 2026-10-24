package de.dirk.updatezentrale

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.net.Uri
import org.xmlpull.v1.XmlPullParser

/**
 * Reads the static launcher shortcuts another app declares in its manifest
 * (meta-data "android.app.shortcuts"), i.e. the entries shown on icon long-press.
 */
object StoreShortcuts {

    private const val NS = "http://schemas.android.com/apk/res/android"

    data class Shortcut(val id: String, val label: String, val intent: Intent?)

    fun read(ctx: Context, pkg: String): List<Shortcut> {
        val pm = ctx.packageManager
        val res = try {
            pm.getResourcesForApplication(pkg)
        } catch (e: PackageManager.NameNotFoundException) {
            return emptyList()
        }
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
        @Suppress("DEPRECATION")
        val activities = pm.queryIntentActivities(launcher, PackageManager.GET_META_DATA)

        val result = mutableListOf<Shortcut>()
        for (ri in activities) {
            val xmlId = ri.activityInfo.metaData?.getInt("android.app.shortcuts", 0) ?: 0
            if (xmlId == 0) continue
            try {
                res.getXml(xmlId).use { result += parse(it, res, pkg) }
            } catch (e: Exception) {
                // malformed or inaccessible XML: ignore this activity
            }
        }
        return result
    }

    /** Picks the first shortcut whose id or label contains one of the keywords. */
    fun find(list: List<Shortcut>, keywords: List<String>): Shortcut? =
        list.firstOrNull { s ->
            val hay = (s.id + " " + s.label).lowercase()
            s.intent != null && keywords.any { hay.contains(it) }
        }

    private fun parse(p: XmlResourceParser, res: Resources, pkg: String): List<Shortcut> {
        val out = mutableListOf<Shortcut>()
        var id = ""
        var label = ""
        var enabled = true
        var intent: Intent? = null
        var current: Intent? = null

        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "shortcut" -> {
                        id = str(p, res, "shortcutId")
                        label = str(p, res, "shortcutShortLabel").ifEmpty { str(p, res, "shortcutLongLabel") }
                        enabled = p.getAttributeBooleanValue(NS, "enabled", true)
                        intent = null
                    }
                    "intent" -> {
                        current = Intent(str(p, res, "action").ifEmpty { Intent.ACTION_VIEW }).apply {
                            val data = str(p, res, "data")
                            if (data.isNotEmpty()) setData(Uri.parse(data))
                            val target = str(p, res, "targetPackage").ifEmpty { pkg }
                            val cls = str(p, res, "targetClass")
                            if (cls.isNotEmpty()) component = ComponentName(target, cls) else setPackage(target)
                        }
                    }
                    "extra" -> current?.putExtra(str(p, res, "name"), str(p, res, "value"))
                    "categories" -> current?.addCategory(str(p, res, "name"))
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    // The last <intent> of a shortcut is the one that gets launched
                    "intent" -> { intent = current; current = null }
                    "shortcut" -> if (enabled) out += Shortcut(id, label, intent)
                }
            }
        }
        return out
    }

    /** Reads an attribute as text, resolving @string references. */
    private fun str(p: XmlResourceParser, res: Resources, name: String): String {
        val resId = p.getAttributeResourceValue(NS, name, 0)
        if (resId != 0) {
            return try { res.getString(resId) } catch (e: Exception) { "" }
        }
        return p.getAttributeValue(NS, name) ?: ""
    }
}
