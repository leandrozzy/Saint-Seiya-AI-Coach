package com.saintseiya.coach.core

import java.io.File
import kotlin.test.Test

/** Replay opcional: define VIDEO_FRAMES=/caminho/frames.txt (texto OCR por frame do vídeo do jogo). Não roda no CI. */
class VideoReplayTest {
    @Test fun replay() {
        val path = System.getenv("VIDEO_FRAMES") ?: return
        val out = StringBuilder()
        var sec = 0; var cur = ArrayList<TextLine>()
        fun flush() {
            if (cur.isEmpty()) return
            val a = ScreenAnalyzer.analyze(cur, 1224, 540, null)
            out.append("%4d:%02d  %-16s score=%.1f  lines=%d  total=%s\n".format(sec / 60, sec % 60, a.kind.name, a.kindScore, a.lineCount,
                a.resources.joinToString { "${it.label}=${it.value}" }))
            cur = ArrayList()
        }
        File(path).forEachLine { l ->
            if (l.startsWith("#\t")) { flush(); sec = l.substringAfter('\t').toInt() }
            else { val p = l.split('\t'); if (p.size >= 5) cur.add(TextLine(p[4], p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt())) }
        }
        flush()
        File(System.getenv("VIDEO_OUT") ?: "/tmp/replay.txt").writeText(out.toString())
    }
}
