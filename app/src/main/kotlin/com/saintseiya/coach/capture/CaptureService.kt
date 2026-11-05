package com.saintseiya.coach.capture

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.saintseiya.coach.CoachApp
import com.saintseiya.coach.MainActivity
import com.saintseiya.coach.core.Hash
import com.saintseiya.coach.data.SessionEntity
import kotlinx.coroutines.*

/**
 * Captura de tela com consentimento explícito (MediaProjection) em foreground service.
 * Só LÊ pixels; não toca no jogo, não usa acessibilidade/overlay.
 */
class CaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var loop: Job? = null
    private var sessionId = 0L
    private var accountId = 0L
    private var ending = false
    private var endReason = "USER"
    private val app get() = CoachApp.instance

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { finish("USER"); return START_NOT_STICKY }
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        if (CaptureState.live.value.running) return START_NOT_STICKY

        startInForeground()
        val code = intent.getIntExtra(EXTRA_CODE, 0)
        @Suppress("DEPRECATION") val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
        if (data == null) { fail("Autorização de captura ausente."); return START_NOT_STICKY }
        scope.launch {
            try {
                accountId = app.prefs.activeAccountId.takeIf { it != 0L } ?: app.repo.ensureAccount()
                sessionId = app.repo.dao().insertSession(SessionEntity(accountId = accountId, source = "LIVE", startedAt = System.currentTimeMillis()))
                CaptureState.setReport(null)
                CaptureState.update { LiveState(running = true, sessionId = sessionId, startedAt = System.currentTimeMillis(), lastMessage = "Capturando. Abra o jogo e navegue.") }
                withContext(Dispatchers.Main) { startProjection(code, data) }
                startLoop()
            } catch (e: Exception) { fail("Falha ao iniciar captura: ${e.message}") }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Leitura do jogo", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, CaptureService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Saint Seiya AI Coach — lendo a tela")
            .setContentText("Toque para abrir o Coach. Use Encerrar para parar.")
            .setContentIntent(open).setOngoing(true)
            .addAction(0, "Encerrar sessão", stop).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTIF_ID, n)
    }

    private fun startProjection(code: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mpm.getMediaProjection(code, data)
        projection = mp
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { // usuário revogou ou o sistema encerrou
                Handler(Looper.getMainLooper()).post { finish("PROJECTION_REVOKED") }
            }
        }, Handler(Looper.getMainLooper()))
        val dm = resources.displayMetrics
        val scale = (1600f / maxOf(dm.widthPixels, dm.heightPixels)).coerceAtMost(1f)
        val w = (dm.widthPixels * scale).toInt().coerceAtLeast(320)
        val h = (dm.heightPixels * scale).toInt().coerceAtLeast(320)
        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        display = mp.createVirtualDisplay("coach-capture", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
    }

    private fun startLoop() {
        val pipeline = Pipeline(app.repo)
        var lastHash: Long? = null
        loop = scope.launch {
            while (isActive) {
                delay(app.prefs.intervalMs)
                val image = try { reader?.acquireLatestImage() } catch (e: Exception) { null } ?: continue
                var bmp: Bitmap? = null
                try {
                    val plane = image.planes[0]
                    val rowPad = plane.rowStride - plane.pixelStride * image.width
                    val full = Bitmap.createBitmap(image.width + rowPad / plane.pixelStride, image.height, Bitmap.Config.ARGB_8888)
                    full.copyPixelsFromBuffer(plane.buffer)
                    bmp = if (rowPad > 0) Bitmap.createBitmap(full, 0, 0, image.width, image.height).also { full.recycle() } else full
                } finally { image.close() }
                CaptureState.update { it.copy(framesSeen = it.framesSeen + 1) }
                incSession { it.copy(framesSeen = it.framesSeen + 1) }
                try {
                    val h = pipeline.hash(bmp!!)
                    val prev = lastHash
                    if (prev != null && Hash.distance(prev, h) < app.prefs.changeThreshold) continue // tela não mudou
                    lastHash = h
                    CaptureState.update { it.copy(framesChanged = it.framesChanged + 1) }
                    val res = pipeline.process(accountId, sessionId, bmp, h)
                    CaptureState.update {
                        it.copy(framesProcessed = it.framesProcessed + 1, knightsNew = it.knightsNew + res.knightsNew,
                            knightsUpdated = it.knightsUpdated + res.knightsUpdated, lastKind = res.kind.label)
                    }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    app.prefs.lastError = "OCR/persistência: ${e.message}"
                    CaptureState.update { it.copy(lastMessage = "Erro ao processar uma tela (veja Diagnóstico).") }
                } finally { bmp?.recycle() }
            }
        }
    }

    private suspend fun incSession(f: (SessionEntity) -> SessionEntity) {
        val d = app.repo.dao(); val s = d.session(sessionId) ?: return; d.updateSession(f(s))
    }

    private fun fail(msg: String) {
        app.prefs.lastError = msg
        CaptureState.update { it.copy(running = false, lastMessage = msg) }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    @Synchronized private fun finish(reason: String) {
        if (ending) return
        ending = true; endReason = reason
        loop?.cancel()
        runCatching { display?.release() }; runCatching { reader?.close() }; runCatching { projection?.stop() }
        val sid = sessionId
        runBlocking(Dispatchers.IO) {
            if (sid != 0L) {
                val d = app.repo.dao(); val s = d.session(sid)
                if (s != null) {
                    val done = s.copy(endedAt = System.currentTimeMillis(), endReason = reason)
                    d.updateSession(done)
                    CaptureState.setReport(SessionReport(sid, reason, done.framesSeen, done.framesProcessed, done.framesReadable, done.knightsNew, done.knightsUpdated, done.issuesOpened))
                }
            }
        }
        CaptureState.update { it.copy(running = false, lastMessage = null) }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    override fun onDestroy() { if (!ending) finish("SYSTEM"); scope.cancel(); super.onDestroy() }

    companion object {
        const val ACTION_START = "coach.START"
        const val ACTION_STOP = "coach.STOP"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CH = "capture"
        private const val NOTIF_ID = 42

        fun start(ctx: Context, code: Int, data: Intent) {
            val i = Intent(ctx, CaptureService::class.java).setAction(ACTION_START).putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data)
            ctx.startForegroundService(i)
        }
        fun stop(ctx: Context) {
            // se o serviço já morreu, garante que o estado não fique "rodando"
            if (!CaptureState.live.value.running) return
            ctx.startService(Intent(ctx, CaptureService::class.java).setAction(ACTION_STOP))
        }
    }
}
