package com.saintseiya.coach.capture

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.saintseiya.coach.core.*
import com.saintseiya.coach.data.IngestResult
import com.saintseiya.coach.data.Repository
import kotlinx.coroutines.tasks.await

/** captura > hash > OCR local (ML Kit, on-device) > análise > reconciliação transacional. Nada sai do aparelho. */
class Pipeline(private val repo: Repository) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val stageReader by lazy { StageReader(recognizer) }
    private val selfOverlay = Regex("^[•●·■↗]")
    private var lastSig: String? = null

    fun luma9x8(bmp: Bitmap): IntArray {
        val small = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        val out = IntArray(72)
        for (y in 0 until 8) for (x in 0 until 9) {
            val c = small.getPixel(x, y)
            out[y * 9 + x] = ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        }
        if (small !== bmp) small.recycle()
        return out
    }

    fun hash(bmp: Bitmap): Long = Hash.dHash(luma9x8(bmp))

    suspend fun ocr(bmp: Bitmap): List<TextLine> {
        val res = recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
        val out = ArrayList<TextLine>()
        for (b in res.textBlocks) for (l in b.lines) {
            val r = l.boundingBox ?: continue
            if (selfOverlay.containsMatchIn(l.text.trim())) continue // contador/botões da bolha do próprio Coach
            out += TextLine(l.text, r.left, r.top, r.right, r.bottom, l.confidence.takeIf { it > 0f } ?: 0.8f)
        }
        return out
    }

    @Volatile var lastTexts: List<String> = emptyList()

    /** Retorna null se o texto da tela é idêntico ao da anterior (nada novo a gravar). */
    suspend fun process(accountId: Long, sessionId: Long, bmp: Bitmap, hash: Long): IngestResult? {
        val lines = ocr(bmp)
        // tela de cavaleiro? (ou sem tipo mas com bastante texto) => lê a faixa vertical do nome
        val pre = ScreenClassifier.classify(lines).first
        val stage = if (pre == ScreenKind.KNIGHT_ROSTER || pre == ScreenKind.KNIGHT_DETAIL || (pre == ScreenKind.UNKNOWN && lines.size >= 8))
            try { stageReader.read(bmp) } catch (e: Exception) { null } else null
        val sig = lines.joinToString("|") { Norm.key(it.text) } + "#" + (stage?.name ?: "") + (stage?.stars ?: "")
        if (sig == lastSig) return null
        lastSig = sig
        lastTexts = lines.map { it.text }.take(60)
        com.saintseiya.coach.CoachApp.instance.prefs.lastSample = lastTexts.joinToString("\n").take(4000)
        val analysis = ScreenAnalyzer.analyze(lines, bmp.width, bmp.height, stage)
        val names = ScreenAnalyzer.allNameCandidates(lines)
        val res = repo.ingest(accountId, sessionId, analysis, names, hash, System.currentTimeMillis())
        val app = com.saintseiya.coach.CoachApp.instance
        if (app.prefs.diagLog) DiagLog.append(app, sig, analysis.kind.name, analysis.kindScore, "${bmp.width}x${bmp.height}", lines.map { it.text },
            "faixa: ${stage?.name ?: "-"} rar=${stage?.rarity ?: "-"} estrelas=${stage?.stars ?: "-"} | cavaleiros ${analysis.knights.map { "${it.rawName} nv${it.level ?: "?"} ${it.stats}" }} (novos ${res.knightsNew}, atualizados ${res.knightsUpdated})")
        return res
    }
}
