# Fontes de conhecimento do jogo
Pesquisa em 2026-11-05 (busca web): não foi encontrada fonte confiável e atual com roster/mecânicas verificáveis (resultados eram lojas de apps, notícias antigas e páginas de baixa qualidade). Confirmado apenas: jogo gratuito da Tencent, Android/iOS, modo Galactic Duel com Pick & Ban (descrições de loja/imprensa, sem data de patch). Versão na App Store: 1.6.58 (atualizada em mar/2026, conteúdo da atualização não confirmado).
Por isso **o app não traz catálogo embutido**: o catálogo é aprendido do que você confirma. Fontes a verificar manualmente antes de codificar regras: anigacha.com/saint-seiya-awakening, yukishi.wixsite.com/website/builds, o vídeo indicado — checar data/servidor.

## Fonte primária de calibração: vídeo de gameplay do próprio usuário (2026-11-06)
Vídeo de ~20 min (2448x1080, UI em PT-BR, servidor global v1.6.58) analisado frame a frame (extração a cada 2 s + OCR + inspeção visual). O que foi **observado** (não é regra de jogo):
- **Casa dos Cavaleiros › Gerenciar:** grade de cartas com letra de raridade (SS/S/A/B/C), rótulo "lv.NN (Máx.)" e estrelas; **sem nomes nas cartas**. Rodapé: "Total possuído: 113". Botões: Casa dos Cavaleiros, Cosmo, Aprimorar, Configurações, Informações, Guia, Desbloquear.
- **Nome do cavaleiro selecionado:** texto **vertical** (lido de cima para baixo) numa faixa em x≈45–48 % da largura, com a letra de raridade num losango acima e as estrelas (douradas = acesas) ao lado. Ex.: "Shura de Capricórnio", "Thane da Terra Flamejante".
- **Aprimorar:** painel "Atributo" com "Nível N", "Atributos Básicos" (3 linhas × 2 colunas: PV | ATQ F., ATQ C. | DEF F., DEF C. | Veloci.) e "Atributos de Luta". Abas: Subir de Nível, Renascimento, Sétimo Sentido, Habilidade, Vínculo.
- **Inventário:** grade de itens sem rótulo; item selecionado mostra nome (truncado) e "QTDE: N".
- **Formação:** 6 posições com cartas "lv.NN" sem nomes; botão Salvar.
- **Batalha PvE:** HUD com "Ataque Básico", "Passiva", "Bloqueado", "Rodada N" vertical. **Vitória:** tela "WIN" (inglês) com "+60", "+2520" e botão "Dados".
- Barra de moedas: ícones sem rótulo (ordem varia por tela) → não lida.
- Não aparecem no vídeo: tela de Cosmo, derrota, Pick & Ban.
- **Revisão completa (todas as 393 telas distintas do vídeo):** além do acima, existem **Cosmo Divino** (roda de slots, painel de atributos do Cosmo, botões "Desequipar Tudo" e "Inventário de Cosmos"), aba **Habilidade** (lista "Detalhes da Habilidade": nome + "Nível N /M" + "Subir de Nível"), **Renascimento** ("Detalhes de Renascimento"), **Sétimo Sentido** (roda), **Vínculo**, **Guia de Cavaleiros**, **Templo dos Cavaleiros**, Mercado, Eventos, Legião, Amigos, Diário. Mensagem de vitória em modo história: "Você venceu a luta. Saída automática em N segundos". Nomes de uma palavra existem (ex.: "Hypnos").
