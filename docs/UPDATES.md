# Atualizações do app
O Android só atualiza por cima se (1) a **assinatura é a mesma** e (2) o `versionCode` é maior. O CI usa `versionCode = número da execução` e assina com uma chave fixa vinda dos **secrets** do repositório.

## Configurar a chave (uma vez)
```
keytool -genkeypair -keystore coach.jks -alias coach -keyalg RSA -keysize 2048 -validity 36500
base64 -w0 coach.jks   # copie a saída
```
Em GitHub → Settings → Secrets and variables → Actions, crie: `COACH_KEYSTORE_BASE64` (a saída acima) e `COACH_KEYSTORE_PASSWORD` (a senha usada para store e key; alias deve ser `coach`). **Guarde o .jks**: perdê-lo impede atualizar por cima.
Sem esses secrets o CI compila com chave temporária e NÃO publica release.

## Fluxo
Cada push gera release `build-N` com `SaintSeiyaAICoach.apk`. No app: Configurações → Atualizações. Repositório privado: crie token fine-grained só-leitura (Contents: Read) e cole no app (guardado criptografado).
