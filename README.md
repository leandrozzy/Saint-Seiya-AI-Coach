# Saint Seiya AI Coach Pro

App Android nativo (Kotlin + Jetpack Compose) para uso pessoal: **copiloto de leitura e recomendação** para *Saint Seiya Awakening: Knights of the Zodiac*.
Ele lê só a tela que você autoriza (MediaProjection + OCR local ML Kit), guarda o que foi realmente visto e sugere equipes/evolução **apenas com seus cavaleiros confirmados**. Não automatiza cliques, não intercepta tráfego, não usa acessibilidade. 100% gratuito e local-first.

## Instalar (sem computador)
1. No GitHub do repositório → **Releases** → baixe `SaintSeiyaAICoach.apk` do release mais recente (ou **Actions** → última execução → artefato).
2. Abra o APK no celular e permita "instalar apps desconhecidos" para o navegador/gerenciador.
3. **Atualizar depois:** dentro do app, *Configurações → Atualizações → Verificar atualização → Baixar → Instalar por cima*. Seus dados são mantidos (mesma assinatura + `versionCode` crescente). Repositório privado: veja [docs/UPDATES.md](docs/UPDATES.md).

## Uso
Hoje → **Iniciar leitura** → autorize → abra o jogo → navegue (lista de cavaleiros, fichas, formação, inventário) → volte e **Encerrar sessão**. Veja *Minha Conta* para o que foi lido, confirme "Pendências" e use *Equipes*/*Evolução*. Alternativa: **Importar prints**.

## Estado honesto (REAL / PARCIAL / INDISPONÍVEL)
Está listado dentro do app em *Configurações → Diagnóstico* e em [docs/ROADMAP.md](docs/ROADMAP.md). Resumo: captura, OCR, persistência incremental, correção manual, importação, montador (nível+função) e atualização in-app são reais; classificação de telas e leitura nome+nível são **heurísticas a calibrar no seu aparelho**; Cosmos/habilidades/sentidos/copiloto de batalha/catálogo verificado/eventos **não existem ainda**.

## Desenvolvimento
- `core/`: lógica pura (JVM) testada: classificação, parsing, reconciliação, equipes, recomendações. `./gradlew :core:test`
- `app/`: Android (Compose, Room, ML Kit). CI: `.github/workflows/build.yml`.
- Docs: [ARCHITECTURE](docs/ARCHITECTURE.md) · [CAPTURE_PIPELINE](docs/CAPTURE_PIPELINE.md) · [TEST_PLAN](docs/TEST_PLAN.md) · [PRIVACY](docs/PRIVACY.md) · [GAME_KNOWLEDGE_SOURCES](docs/GAME_KNOWLEDGE_SOURCES.md) · [UPDATES](docs/UPDATES.md) · [ROADMAP](docs/ROADMAP.md)
