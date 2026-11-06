# Release notes
## 0.1.x
Primeira versão: captura com consentimento, OCR local, persistência incremental, 8 abas, correção manual, importação de prints, montador de equipe, atualização in-app.
## build 11
- Corrige captura em paisagem (acompanha a rotação da tela), até 1920 px.
- Botão "Iniciar leitura e abrir o jogo" + seletor do jogo; bolha flutuante (contador, Parar, Coach).
- Leitura mais robusta: "Nome Lv.80" na mesma linha, ficha com nome grande, tela sem palavra-chave com 2+ pares nome+nível.
- Diagnóstico: quadros recebidos, quadro preto, último texto do OCR e log de calibração (copiar/compartilhar).
- Aviso automático de nova versão.
## build 15 — calibrado com o vídeo de gameplay do usuário (20 min, 2448x1080)
- **Reescrita da leitura de cavaleiros.** O jogo não mostra nomes na grade; o app agora lê a **faixa vertical** do cavaleiro selecionado (recorte girado 90°), **estrelas** (cor), **raridade**, **Nível** e os **atributos básicos** (PV/ATQ C/DEF C/ATQ F/DEF F/Velocidade) do painel *Aprimorar*.
- **"Total possuído"** (rodapé de Gerenciar) vira a meta de cobertura: "lidos X de N".
- Inventário: item + **QTDE**. Batalha: tela **WIN**, HUD (Recuar/Pausar/Auto).
- Banco de dados v2 (migração: dados existentes preservados).
- Classificação tolerante a erros de OCR e ancorada na faixa visual do cavaleiro.
