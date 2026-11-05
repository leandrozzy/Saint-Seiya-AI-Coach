package com.saintseiya.coach.core

import java.text.Normalizer

object Norm {
    fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    /** Chave estável: minúsculas, sem acentos, só letras/dígitos. */
    fun key(s: String): String = fold(s).filter { it.isLetterOrDigit() }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val c = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + c)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    fun similarity(a: String, b: String): Double {
        val ka = key(a); val kb = key(b)
        if (ka.isEmpty() || kb.isEmpty()) return 0.0
        return 1.0 - levenshtein(ka, kb).toDouble() / maxOf(ka.length, kb.length)
    }

    /**
     * Interpreta números do jogo: "1,234,567", "1.234", "12.5K", "1,2M".
     * Separador seguido de exatamente 3 dígitos (sem sufixo) = milhar.
     */
    fun parseNumber(raw: String): Long? {
        val s = raw.trim().replace(" ", "")
        val m = Regex("^(\\d[\\d.,]*)([kKmMbB]?)$").matchEntire(s) ?: return null
        val digits = m.groupValues[1]
        val suffix = m.groupValues[2].lowercase()
        val mult = when (suffix) { "k" -> 1_000.0; "m" -> 1_000_000.0; "b" -> 1_000_000_000.0; else -> 1.0 }
        val seps = digits.count { it == '.' || it == ',' }
        val value: Double = if (seps == 0) {
            digits.toDouble()
        } else {
            val last = digits.lastIndexOfAny(charArrayOf('.', ','))
            val tail = digits.length - last - 1
            if (suffix.isEmpty() && tail == 3) digits.filter { it.isDigit() }.toDouble()
            else if (suffix.isNotEmpty() && seps == 1) digits.replace(',', '.').toDouble()
            else if (tail == 3) digits.filter { it.isDigit() }.toDouble()
            else return null
        }
        return (value * mult).toLong()
    }
}
