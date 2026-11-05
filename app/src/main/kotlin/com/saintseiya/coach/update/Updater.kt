package com.saintseiya.coach.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.saintseiya.coach.BuildConfig
import com.saintseiya.coach.CoachApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val tag: String, val versionCode: Int, val name: String, val notes: String, val apkUrl: String, val apiAssetUrl: String?, val size: Long)

sealed class UpdateCheck {
    data class Available(val info: ReleaseInfo) : UpdateCheck()
    data object UpToDate : UpdateCheck()
    data class Failed(val reason: String) : UpdateCheck()
}

/**
 * Atualização in-app via GitHub Releases (grátis). O workflow publica um release por build com tag `build-<N>`,
 * onde N == versionCode. Se o repositório for privado, o usuário informa um token somente-leitura (guardado criptografado).
 * O Android só instala por cima se a assinatura for a mesma e o versionCode maior — ambos garantidos pelo CI.
 */
object Updater {
    private fun conn(url: String, token: String?, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "SaintSeiyaAICoach")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
        }

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        val prefs = CoachApp.instance.prefs
        try {
            val c = conn("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest", prefs.githubToken, "application/vnd.github+json")
            val code = c.responseCode
            if (code == 404) return@withContext UpdateCheck.Failed("Release não encontrado (404). Se o repositório é privado, informe um token de leitura em Configurações > Atualizações.")
            if (code == 401 || code == 403) return@withContext UpdateCheck.Failed("Acesso negado ($code). Verifique o token ou o limite de requisições.")
            if (code != 200) return@withContext UpdateCheck.Failed("GitHub respondeu HTTP $code.")
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = j.optString("tag_name")
            val vc = Regex("build-(\\d+)").find(tag)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@withContext UpdateCheck.Failed("Tag de release inesperada: $tag")
            prefs.lastUpdateCheck = System.currentTimeMillis()
            if (vc <= BuildConfig.VERSION_CODE) return@withContext UpdateCheck.UpToDate
            val assets = j.optJSONArray("assets")
            var url: String? = null; var api: String? = null; var size = 0L
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); api = a.optString("url"); size = a.optLong("size"); break }
            }
            if (url == null) return@withContext UpdateCheck.Failed("Release $tag sem APK anexado.")
            UpdateCheck.Available(ReleaseInfo(tag, vc, j.optString("name"), j.optString("body"), url, api, size))
        } catch (e: Exception) {
            UpdateCheck.Failed("Sem conexão ou erro: ${e.message}")
        }
    }

    /** Baixa o APK para o cache; progresso 0..1. Para repo privado usa a URL da API (octet-stream + token). */
    suspend fun download(ctx: Context, info: ReleaseInfo, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        try {
            val token = CoachApp.instance.prefs.githubToken
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val out = File(dir, "coach-${info.versionCode}.apk")
            val c = if (!token.isNullOrBlank() && info.apiAssetUrl != null) conn(info.apiAssetUrl, token, "application/octet-stream")
                    else conn(info.apkUrl, token, "application/octet-stream")
            c.instanceFollowRedirects = true
            if (c.responseCode !in 200..299) return@withContext Result.failure(Exception("HTTP ${c.responseCode}"))
            val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
            c.inputStream.use { ins -> out.outputStream().use { os ->
                val buf = ByteArray(64 * 1024); var done = 0L
                while (true) { val n = ins.read(buf); if (n < 0) break; os.write(buf, 0, n); done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f)) }
            } }
            Result.success(out)
        } catch (e: Exception) { Result.failure(e) }
    }

    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
