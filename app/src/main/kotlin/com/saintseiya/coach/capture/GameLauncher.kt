package com.saintseiya.coach.capture

import android.content.Context
import android.content.Intent
import com.saintseiya.coach.core.Norm

object GameLauncher {
    data class AppInfo(val label: String, val pkg: String)

    fun launchable(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private val hints = listOf("saint", "seiya", "cavaleiros", "caballeros", "zodiac", "knights of", "awakening")
    fun guess(all: List<AppInfo>): List<AppInfo> = all.filter { a ->
        val f = Norm.fold(a.label) + " " + a.pkg.lowercase()
        hints.any { f.contains(it) }
    }

    fun label(ctx: Context, pkg: String): String? = try {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { null }

    fun launch(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try { ctx.startActivity(i); true } catch (e: Exception) { false }
    }
}
