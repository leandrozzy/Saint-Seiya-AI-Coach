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
            out += TextLine(l.text, r.left, r.top, r.right, r.bottom, l.confidence.takeIf { it > 0f } ?: 0.8f)
        }
        return out
    }

    @Volatile var lastTexts: List<String> = emptyList()

    suspend fun process(accountId: Long, sessionId: Long, bmp: Bitmap, hash: Long): IngestResult {
        val lines = ocr(bmp)
        lastTexts = lines.map { it.text }.take(60)
        com.saintseiya.coach.CoachApp.instance.prefs.lastSample = lastTexts.joinToString("\n").take(4000)
        val analysis = ScreenAnalyzer.analyze(lines, bmp.width, bmp.height)
        val names = ScreenAnalyzer.allNameCandidates(lines)
        return repo.ingest(accountId, sessionId, analysis, names, hash, System.currentTimeMillis())
    }
}
