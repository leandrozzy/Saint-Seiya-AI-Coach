# Diretrizes do projeto
- Nunca fingir funcionalidade: marcar REAL/PARCIAL/INDISPONÍVEL (Diagnóstico + docs/ROADMAP.md).
- Desconhecido = null, nunca 0. Nunca apagar dado não visto. Posse só por tela própria com nível lido (Reconciler).
- Sem automação do jogo, sem acessibilidade, sem segredos no Git/APK, sem custo obrigatório.
- Room: nunca `fallbackToDestructiveMigration`; subir `version` exige Migration. Assinatura fixa + versionCode = run number do CI.
- Lógica nova vai em `core/` com testes (`./gradlew :core:test -PcoreOnly` roda sem Android SDK).
