package com.saintseiya.coach.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveState(
    val running: Boolean = false,
    val sessionId: Long? = null,
    val framesSeen: Int = 0,
    val framesChanged: Int = 0,
    val framesProcessed: Int = 0,
    val knightsNew: Int = 0,
    val knightsUpdated: Int = 0,
    val lastKind: String? = null,
    val lastMessage: String? = null,
    val startedAt: Long = 0,
    val framesAcquired: Int = 0,      // quadros realmente recebidos do Android
    val captureSize: String? = null,  // ex.: 1600x720
    val lastLineCount: Int = 0,
    val lastScore: Double = 0.0,
    val lastText: List<String> = emptyList(),
    val lastFrameBlack: Boolean = false,
)

/** Resumo mostrado ao encerrar. Nunca diz "sucesso" quando nada foi capturado. */
data class SessionReport(val sessionId: Long, val reason: String, val seen: Int, val processed: Int, val readable: Int, val newK: Int, val updK: Int, val issues: Int) {
    val noData get() = processed == 0 || (newK == 0 && updK == 0 && readable == 0)
}

object CaptureState {
    private val _live = MutableStateFlow(LiveState())
    val live = _live.asStateFlow()
    private val _report = MutableStateFlow<SessionReport?>(null)
    val report = _report.asStateFlow()
    fun update(f: (LiveState) -> LiveState) { _live.value = f(_live.value) }
    fun setReport(r: SessionReport?) { _report.value = r }
}
