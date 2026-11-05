package com.saintseiya.coach.core

/** dHash 9x8 sobre luminância já reduzida (9 colunas x 8 linhas = 72 valores). */
object Hash {
    fun dHash(luma9x8: IntArray): Long {
        require(luma9x8.size == 72)
        var h = 0L
        var bit = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            if (luma9x8[y * 9 + x] > luma9x8[y * 9 + x + 1]) h = h or (1L shl bit)
            bit++
        }
        return h
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}
