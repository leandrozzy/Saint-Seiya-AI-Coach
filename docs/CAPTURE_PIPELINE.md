# Pipeline de captura
MediaProjection (consentimento do Android; FGS tipo mediaProjection; callback `onStop` encerra a sessão com motivo) → ImageReader (≤1600 px) a cada N s → dHash 9x8; se Hamming < limiar, descarta → OCR ML Kit on-device → `ScreenAnalyzer` → `Repository.ingest` (transação: observação, cavaleiros, recursos, batalha, contadores).
Regras: nome só vira "possuído" em tela própria (lista/ficha/formação) com nível lido; banner/adversário/Pick&Ban nunca. Nível não regride sem confirmação (vira pendência). Correção manual trava o campo. Sessão sem tela legível = "Nenhum dado capturado".
**Limitação:** palavras-chave de classificação são heurísticas; calibrar com o jogo real.
