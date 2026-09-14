# Bateria Android

App Android autônomo, horizontal e offline.

## Recursos
- trava em landscape
- tela cheia/imersiva
- bateria visual interativa
- hotspots invisíveis sobre as peças
- feedback visual no ponto tocado
- multitouch
- metrônomo
- volume
- 6 variações de timbre sintetizado
- sem servidor, sem a-Shell, sem Internet

## APK via GitHub Actions
O arquivo `.github/workflows/build-apk.yml` gera automaticamente um APK de debug instalável.

1. Envie esta pasta para um repositório GitHub.
2. Abra **Actions → Build APK → Run workflow**.
3. Ao terminar, baixe o artefato **Bateria-APK**.
4. Dentro dele estará `app-debug.apk`.

O APK de debug é assinado automaticamente pela infraestrutura de build do Android e pode ser instalado por sideload.
