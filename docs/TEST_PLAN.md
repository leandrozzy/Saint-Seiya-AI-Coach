# Plano de testes
**Automático (CI):** `:core:test` (22 testes: números, chaves/acentos, hash, classificação, pareamento nome/nível, recursos, regras de posse, não-regressão de nível, idempotência, travas manuais, equipes só-possuídos, recomendações) + build release + lint.
**No aparelho (ainda NÃO validado — depende de você):**
1. Instalar; Hoje → Iniciar leitura → autorizar; abrir o jogo na lista de cavaleiros; voltar; Encerrar. Esperado: relatório + cavaleiros em Minha Conta.
2. Fechar/reabrir o app: dados mantidos. 3. Segunda sessão: não apaga o que não viu.
4. Iniciar e encerrar sem navegar: "Nenhum dado capturado". 5. Revogar pela barra do Android: sessão encerrada com motivo.
6. Banner/adversário não gera "possuído". 7. Paisagem e retrato. 8. Atualização in-app com 2 builds.
Pendente: testes instrumentados Room/migração; medição de bateria.

## Roteiro único de validação (build 15)
1. Hoje › Iniciar leitura e abrir o jogo (Tela inteira).
2. Cavaleiros › Gerenciar: deixe ~2 s. Esperado: Minha Conta mostra "Lidos: 0 de 113" (o total vem do jogo).
3. Toque em um cavaleiro › Aprimorar; toque na seta › ao lado do personagem ~10 vezes (1 s cada). Esperado: cavaleiros confirmados com nome, nível, ★, raridade e atributos.
4. Inventário: toque em 5 itens. Esperado: Recursos mostra item + QTDE.
5. Faça uma batalha até a tela WIN. Esperado: Batalhas › Histórico registra vitória.
6. Configurações › Diagnóstico › Log de calibração › Copiar parte 1/N e cole no chat se algo falhar.
