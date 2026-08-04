# App Android do VoxAI

Casca nativa que abre `https://hglucena.github.io/voxai/` num WebView. O teste em
si continua sendo o mesmo `teste.html` do site.

## Por que casca e não app empacotado

O teste depende de dois serviços online (extração de medidas acústicas e
classificação do grau), então nem uma versão com as telas empacotadas rodaria
offline. Deixando o conteúdo no site, qualquer ajuste no questionário ou no texto
chega ao celular sem precisar publicar um APK novo.

O código nativo existe para resolver o que o navegador não resolve sozinho:
concede a permissão de microfone nas duas camadas que o WebView exige, faz o botão
voltar do sistema andar no histórico da página, manda links externos para o
navegador e troca a página de erro do Chromium por um aviso de "sem conexão".

## Como sai o APK

Não precisa de Android Studio. O GitHub Actions compila em
[`.github/workflows/apk.yml`](../.github/workflows/apk.yml) e publica o resultado
como release. O botão do site aponta para o link fixo
`releases/latest/download/voxai.apk`, que sempre serve a build mais recente.

Para gerar uma versão nova: Actions → "APK do VoxAI" → Run workflow. Um push que
toque em `android/` também dispara o build. O `versionCode` vem do número da
execução, então cada release é sempre maior que o anterior.

## Assinatura

A chave está nos secrets do repositório (`VOXAI_KEYSTORE_B64` e
`VOXAI_KEYSTORE_PASS`) e o original fica em `pibic/voxai-assinatura/`, fora do
repositório. O Android só instala uma atualização por cima do app se ela vier
assinada com essa mesma chave; sem ela, quem já tem o VoxAI instalado precisa
desinstalar antes.

## Onde mexer

| Arquivo | O quê |
| --- | --- |
| `app/src/main/java/br/ufpb/voxai/MainActivity.java` | WebView, permissão de microfone, botão voltar, tela sem conexão |
| `app/src/main/AndroidManifest.xml` | permissões e orientação da tela |
| `app/build.gradle` | SDK mínimo (24, Android 7.0), versão, assinatura |
| `app/src/main/res/drawable/ic_launcher_marca.png` | ícone (gerado a partir de `icon-512.png`) |

A URL que o app abre está em `MainActivity.INICIO`, junto com `HOST`, que decide
o que fica dentro do app e o que vai para o navegador. Mudar o endereço do site
exige mexer nas duas constantes.
