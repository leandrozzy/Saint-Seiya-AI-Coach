package com.saintseiya.coach.capture

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.saintseiya.coach.core.Stage
import com.saintseiya.coach.core.StageStrip
import kotlinx.coroutines.tasks.await

/**
 * Lê a "faixa" do cavaleiro selecionado (telas Gerenciar/Aprimorar...): o nome é texto VERTICAL, então o recorte
 * é girado 90° anti-horário antes do OCR; raridade = letra no losango; estrelas = contagem de pixels dourados.
 * Posições medidas em vídeo real do jogo (ver core/Stage.kt).
 */
class StageReader(private val recognizer: TextRecognizer) {
    private fun crop(b: Bitmap, f: FloatArray): Bitmap {
        val x = (f[0] * b.width).toInt().coerceIn(0, b.width - 2)
        val y = (f[1] * b.height).toInt().coerceIn(0, b.height - 2)
        val w = ((f[2] - f[0]) * b.width).toInt().coerceIn(1, b.width - x)
        val h = ((f[3] - f[1]) * b.height).toInt().coerceIn(1, b.height - y)
        return Bitmap.createBitmap(b, x, y, w, h)
    }

    private suspend fun lines(b: Bitmap): List<Pair<String, Float>> =
        recognizer.process(InputImage.fromBitmap(b, 0)).await().textBlocks.flatMap { it.lines }.map { it.text to (it.confidence.takeIf { c -> c > 0f } ?: 0.7f) }

    suspend fun read(bmp: Bitmap): StageStrip {
        // nome (vertical)
        var name: String? = null; var conf = 0.0
        val strip = crop(bmp, Stage.NAME)
        val m = Matrix().apply { postRotate(-90f); postScale(2f, 2f) } // anti-horário + 2x
        val rot = Bitmap.createBitmap(strip, 0, 0, strip.width, strip.height, m, true)
        try {
            lines(rot).mapNotNull { (t, c) -> Stage.cleanName(t)?.let { it to c } }.maxByOrNull { it.first.length }?.let { name = it.first; conf = it.second.toDouble() }
        } finally { rot.recycle(); strip.recycle() }
        // raridade (letra no losango)
        var rarity: String? = null
        val rc = crop(bmp, Stage.RARITY)
        val big = Bitmap.createScaledBitmap(rc, rc.width * 3, rc.height * 3, true)
        try { rarity = lines(big).firstNotNullOfOrNull { (t, _) -> Stage.cleanRarity(t) } } finally { big.recycle(); rc.recycle() }
        // estrelas (douradas = acesas)
        val x = (Stage.STAR_X * bmp.width).toInt().coerceIn(2, bmp.width - 3)
        val gold = BooleanArray(Stage.STAR_Y.size) { i ->
            val y = (Stage.STAR_Y[i] * bmp.height).toInt().coerceIn(2, bmp.height - 3)
            var r = 0; var g = 0; var b = 0; var n = 0
            for (dx in -2..2) for (dy in -2..2) { val c = bmp.getPixel(x + dx, y + dy); r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++ }
            Stage.isGold(r / n, g / n, b / n)
        }
        return StageStrip(name, rarity, Stage.starsFrom(gold), conf)
    }
}
