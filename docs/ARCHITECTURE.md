# Arquitetura
- `core` (Kotlin/JVM, sem Android): `ScreenClassifier` → `ScreenAnalyzer` (linhas OCR → leituras) → `Reconciler` (regras de posse/merge) → `TeamBuilder`/`Recommender`. `Norm` (normalização, fuzzy, números), `Hash` (dHash).
- `app`: Compose (8 abas; trilho em paisagem, barra compacta em retrato), `AppViewModel`, Room (`Repository.ingest` transacional), `CaptureService` (MediaProjection, foreground), `Pipeline` (ML Kit), `Updater` (GitHub Releases).
- Dados: histórico append-only (`screen_observation`, `knight_history`) + estado atual (`knight`). Sem imagens nem texto bruto.
