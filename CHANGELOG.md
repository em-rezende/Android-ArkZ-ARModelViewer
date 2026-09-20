# Changelog

Todas as mudanças relevantes do **ArkZ ARModelViewer** são registradas neste
arquivo. O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/)
e o projeto usa [Versionamento Semântico](https://semver.org/lang/pt-BR/).

## [1.0.0] — 2026-09-20

Primeira versão pública: o fluxo completo de RA funcionando de ponta a ponta —
carregar um modelo 3D, ter um marcador de imagem (QR code) impresso e ver o
modelo fixo sobre ele, com ajuste fino de rotação, tamanho e altura.

### Adicionado

**Realidade aumentada**
- Renderização de modelos 3D ancorados em **marcadores de imagem** com ARCore
  *Augmented Images*, via [SceneView](https://github.com/sceneview/sceneview)
  `io.github.sceneview:arsceneview:4.38.0` (Filament).
- Imagens de referência embutidas (`marker_a.png`, `marker_b.png`) e suporte a
  **marcadores personalizados** criados pelo usuário.
- Escala automática pela largura do marcador detectado, além de pinça e gestos
  de câmera (orbitar / zoom / pan).

**Modelos**
- Carregamento de `.glb`, `.gltf`, `.obj`, `.ply`, `.stl` e `.3mf` pelo seletor
  do Android; o arquivo é copiado para o cache do app antes de abrir.
- Detecção do formato pelo *sniffing* do conteúdo (não apenas pela extensão) e
  conversão de `.obj` quando necessário.
- Estados de carregamento (carregando / pronto / falhou) com aviso na barra de
  status e no Logcat (`ArkZARModelViewer`).

**Interface (Jetpack Compose / Material 3)**
- Barra de ferramentas flutuante: reiniciar, escolher marcador, ajustes do
  modelo, escala automática, capturar tela e sair, além do menu ⋮.
- Painel **Ajustes do modelo** com sliders de **rotação X/Y/Z**
  (inclinar/girar/rolar), **tamanho (maior dimensão)** e **elevação Z (plano do
  marcador)**, com **Redefinir** — efeito imediato, sem sair da RA.
- Menu ⋮ com **Ajuda** (automática na primeira execução), **Sobre**,
  **Idioma**, **Enviar feedback por e-mail**, **Avaliar no Google Play**,
  **Compartilhar o app**, **Qualidade da captura**, **Exportar folha de
  impressão do marcador** e **Copiar diagnóstico**.
- Avisos de estado na tela (marcador detectado, modelo carregado, escala
  aplicada, erros) em vez de diálogos bloqueantes.

**Marcadores**
- Diálogo **Gerenciar marcador**: lista os embutidos e os do usuário, com
  seleção, salvamento em PNG e exclusão.
- Diálogo **Criar/Carregar marcador**: gera uma figura pseudo-QR a partir do
  nome (para imprimir sem depender de gerador externo) ou importa uma imagem
  que já está no aparelho.
- **Exportar folha de impressão**: PNG com o marcador, o rótulo e as instruções
  em escala 100%.

**Captura de tela**
- Captura da vista em RA (câmera + modelo, **sem** a interface) em três
  qualidades: Padrão (1280 px), Alta (1920 px) e Máxima (resolução da tela).
- Gravação em `Pictures/ArkZ ARModelViewer` com o som de disparo do sistema.

**Internacionalização**
- Interface em **8 idiomas** (pt-BR — padrão, pt-PT, en, es, fr, it, de e
  zh-CN), com **troca de idioma dentro do app** (menu ⋮ → Idioma).

**Ferramentas de desenvolvimento**
- `tools/generate-assets.ps1`: regera ícones do launcher, marcadores
  alternativos e as folhas de impressão dos marcadores.
- `tools/inspect-model-formats.ps1`: mostra o formato que o SceneView realmente
  detecta em cada arquivo de modelo.
- `tools/publish-github.ps1`: publica o projeto (repositório + release) usando o
  GitHub CLI, quando disponível.

**Build**
- Android Gradle Plugin 9.4.0 / Gradle 9.7.1, `minSdk 24`, `targetSdk 36` e
  `compileSdk 37` (exigência do SceneView 4.38.0), Kotlin + Compose.
- `applicationId` / `namespace` `com.arkz.armodelviewer`.
- Assinatura do APK de release por `keystore.properties` (fora do controle de
  versão) — sem o arquivo, o build continua funcionando e gera um APK sem
  assinatura.

**Projeto e documentação**
- Licença **GNU GPL-3.0** com aviso de copyright e cabeçalho GPL-3.0 nos
  **14 arquivos Kotlin**.
- [`CONTRIBUTING.md`](CONTRIBUTING.md), [`SECURITY.md`](SECURITY.md) e modelos de
  *issue* em `.github/ISSUE_TEMPLATE/` (Bug e Ideia).
- `tools/publish-github.ps1` (repositório + release pelo GitHub CLI) e
  `tools/generate-social-preview.ps1` (imagem de compartilhamento 1280×640).

### Notas

- O release no GitHub traz o **APK assinado** `ArkZ-ARModelViewer-1.0.0.apk`
  (45,8 MB, SHA-256 `2ff2546a…56b4`), assinado com a chave de release do projeto
  (RSA 4096, APK Signature Scheme v2, `CN=Ark-Z Arquitetura Ltda`). Compilar do
  código-fonte continua possível — veja *Como compilar e instalar* no README.
- Requer aparelho com suporte a **ARCore** + Google Play Services for AR.

[1.0.0]: https://github.com/em-rezende/Android-ArkZ-ARModelViewer/releases/tag/v1.0.0
