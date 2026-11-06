package com.saintseiya.coach.capture

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Log LOCAL do texto reconhecido pelo OCR (sem imagens), para calibrar o reconhecimento com o jogo real.
 * Desligável em Configurações > Diagnóstico. Limitado em tamanho. Nunca sai do aparelho sem você compartilhar.
 */
object DiagLog {
    private const val MAX = 300_000
    private fun file(ctx: Context) = File(ctx.filesDir, "ocr-diagnostico.log")
    private val fmt get() = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US)

    @Synchronized fun append(ctx: Context, kind: String, score: Double, size: String, lines: List<String>, applied: String) {
        val f = file(ctx)
        val sb = StringBuilder()
        sb.append("=== ").append(fmt.format(Date())).append(" | ").append(kind).append(" | score ").append("%.1f".format(score))
            .append(" | ").append(lines.size).append(" linhas | ").append(size).append(" | ").append(applied).append('\n')
        lines.take(80).forEach { sb.append(it.replace('\n', ' ')).append('\n') }
        f.appendText(sb.toString())
        if (f.length() > MAX) f.writeText(f.readText().takeLast(MAX * 2 / 3))
    }

    @Synchronized fun read(ctx: Context): String = file(ctx).takeIf { it.exists() }?.readText().orEmpty()
    @Synchronized fun clear(ctx: Context) { file(ctx).delete() }
}
