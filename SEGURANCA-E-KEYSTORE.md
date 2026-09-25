# Segurança e keystore

Este documento explica **como a assinatura do APK funciona neste projeto**, onde
ficam a chave e as senhas, o que **nunca** pode ir para o Git e — o mais
importante — como **guardar o projeto** sem perder (nem vazar) o ativo mais
valioso do repositório: a chave privada `arkz-release.jks`.

| Assunto | Onde está |
|---|---|
| Relatar uma vulnerabilidade no app | [SECURITY.md](SECURITY.md) |
| Compilar, testar e publicar | [README.md](README.md) |
| Histórico de versões | [CHANGELOG.md](CHANGELOG.md) |
| Como contribuir | [CONTRIBUTING.md](CONTRIBUTING.md) |

---

## 1. Resumo rápido

| Arquivo (na raiz) | O que é | Vai para o Git? | Se for perdido |
|---|---|---|---|
| `arkz-release.jks` | **chave privada** de assinatura (RSA 4096) | **não** — no `.gitignore` | **irrecuperável** (sem backup): o app já publicado deixa de receber atualizações com a mesma identidade |
| `keystore.properties` | senhas do keystore + alias | **não** — no `.gitignore` | recriável **somente** se você ainda souber as senhas |
| `local.properties` | caminho do SDK **desta máquina** | **não** — no `.gitignore` | o Android Studio recria ao abrir o projeto |
| `dist/*.apk` | APK assinado, pronto para instalar/distribuir | ignorado (`*.apk`) | reconstruível a partir do código + `arkz-release.jks` |
| `app/build/`, `build/`, `.gradle/`, `.kotlin/` | saídas de build e caches locais | não (gerados) | descartáveis: o Gradle refaz |
| código, docs, `gradle/`, `tools/` | o projeto em si | **sim** | recuperável do GitHub |

> **Regra de ouro:** faça backup de `arkz-release.jks` e guarde as senhas em um
> gerenciador de senhas. Todo o resto é descartável ou regenerável.

---

## 2. Por que a chave é o ativo mais valioso

No Android, a **identidade de um app** é o par `applicationId` +
**assinatura**. O `applicationId` aqui é `com.arkz.armodelviewer`.

* O Android **recusa** instalar uma atualização assinada por outra chave
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) e trata outro `applicationId` como
  **outro aplicativo** (a base instalada recomeça do zero).
* A Play Store exige a **mesma chave** (ou a *upload key* cadastrada no Play App
  Signing) para aceitar uma versão nova do mesmo app.
* Assinar **não** é proteger o código: o APK é aberto, o código é GPL-3.0 e
  qualquer pessoa pode lê-lo. A assinatura serve para **provar a origem** — e é
  por isso que quem tem a chave pode publicar um APK que o aparelho aceita como
  legítimo.

Consequências práticas:

* **Não existe senha "esquecida" para recuperar**: o JKS é criptografado com
  `storePassword`/`keyPassword`; sem elas o arquivo é inútil.
* **Não recrie o keystore "do zero" com o mesmo nome**: um keystore novo tem
  outro certificado (outro *fingerprint*) e não atualiza o app já instalado.
* **Guarde a chave junto com o projeto, mas fora do controle de versão** —
  exatamente o arranjo usado aqui (`arkz-release.jks` na raiz, ignorado pelo
  Git).

---

## 3. A chave deste projeto (dados reais, conferidos)

| Item | Valor |
|---|---|
| Arquivo | `arkz-release.jks` (raiz do projeto) |
| Tipo | JKS, entrada `PrivateKeyEntry` (`keytool -list`) |
| Alias | `arkz` |
| Chave | **RSA 4096 bits**, assinatura `SHA384withRSA` |
| Titular / emissor | `CN=Ark-Z Arquitetura Ltda, OU=ArkZ ARModelViewer, O=Ark-Z Arquitetura Ltda, C=BR` (autoassinado) |
| Número de série | `ca6e1b3d2d09193b` |
| Validade | 20/09/2026 → **12/09/2056** (≈ 30 anos) |
| SHA-1 | `5A:02:B7:C5:4E:0F:3F:51:8D:C3:14:9D:5C:2C:F4:AB:D0:9C:89:FC` |
| SHA-256 | `03:6D:8B:55:0B:C0:00:78:09:8F:89:1E:03:30:D4:2F:18:DF:DD:D4:39:D6:06:90:EF:4A:E8:EF:DC:C8:F9:87` |

O APK da versão 1.0.0 é assinado **somente com o esquema v2** (*APK Signature
Scheme v2*, sem v1/JAR), por **1 signer**, com esse mesmo certificado — o
`SHA-256` acima é o "número de identidade" que você deve comparar em qualquer
cópia futura da chave.

### Como conferir em 3 comandos

```bash
# 1) O keystore abre, as senhas conferem e o certificado e o esperado?
keytool -list -v -keystore arkz-release.jks -alias arkz
#    procure por "SHA256:" e compare com a tabela acima

# 2) O APK esta assinado, e por quem?
apksigner verify --print-certs dist/ArkZ-ARModelViewer-1.0.0.apk
#    esperado: "certificate DN: CN=Ark-Z Arquitetura Ltda, ..."
#    e o mesmo SHA-256 (036d8b55... sem os dois-pontos, em minusculas)

# 3) Quais esquemas de assinatura o APK usa?
apksigner verify --verbose dist/ArkZ-ARModelViewer-1.0.0.apk
#    esperado: "Verified using v2 scheme (APK Signature Scheme v2): true"
#    e v1/v3/v4/...: false
```

`keytool` vem com o JDK (ou com o JBR do Android Studio) e `apksigner` com o SDK
(`%LOCALAPPDATA%\Android\Sdk\build-tools\<versão>\`). No PowerShell:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -list -v -keystore arkz-release.jks -alias arkz
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat" verify --print-certs dist\ArkZ-ARModelViewer-1.0.0.apk
```

O mesmo SHA-256 aparece no README (seção *Assinatura dos APKs publicados*), para
quem baixar o APK das *releases* poder conferir.

---

## 4. Como a assinatura entra no build

Tudo está em `app/build.gradle.kts`. Primeiro as credenciais são lidas do
arquivo local:

```kotlin
// Credenciais de assinatura do APK de release. Ficam em keystore.properties, na
// raiz do projeto e FORA do controle de versao (veja .gitignore). Sem esse arquivo
// o build continua funcionando, mas o release sai sem assinatura.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
```

Depois elas alimentam a configuração de assinatura de release:

```kotlin
signingConfigs {
    // So e criada quando keystore.properties existe: assim o repositorio
    // publico compila (sem assinatura) em qualquer maquina.
    if (keystorePropertiesFile.exists()) {
        create("release") {
            storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }
}
```

E `keystore.properties` (formato `chave=valor`) fica assim — aqui sem a senha
real, obviamente:

```properties
storeFile=arkz-release.jks      # caminho relativo a raiz do projeto
storePassword=<senha do keystore>
keyAlias=arkz
keyPassword=<senha da chave>
```

Comportamento a saber:

* **Com** `keystore.properties` → `:app:assembleRelease` gera
  `app/build/outputs/apk/release/app-release.apk` **assinado**.
* **Sem** o arquivo → o build continua e gera `app-release-unsigned.apk`, que
  **não instala** no aparelho (serve para validar a compilação e o
  `lintVitalRelease`). É exatamente o que acontece em qualquer clone novo do
  repositório público — de propósito.
* O `storeFile` é relativo à **raiz do projeto**: o caminho absoluto da chave é
  detalhe da máquina, não do repositório.
* Aqui `storePassword` e `keyPassword` são a **mesma senha**; o JKS aceita
  senhas diferentes para o arquivo e para a chave, mas uma senha só reduz o
  risco de esquecer uma delas.

---

## 5. O que o Git pode ver (e o que nunca pode)

Trecho do `.gitignore` que protege a assinatura:

```gitignore
# Assinatura do APK: NUNCA versionar (contém a chave privada)
*.jks
*.keystore
keystore.properties
```

E o `.gitattributes` marca a chave como **binária** (`*.jks binary`,
`*.keystore binary`), para o Git nunca converter fim de linha e corromper o
arquivo.

Confira **antes de cada publicação**:

```bash
# 1) nenhum destes pode aparecer (deve sair vazio)
git ls-files | findstr /i "jks keystore.properties"

# 2) o que o Git esta ignorando agora (aparecem a chave e os arquivos locais)
git status --ignored --porcelain

# 3) varredura simples por segredos no que esta versionado
git grep -n -i "storePassword\|keyPassword\|password=" HEAD
```

Cuidados:

* **Nunca** use `git add -f arkz-release.jks` (ou `git add -f
  keystore.properties`) para "forçar" um commit. O `.gitignore` está certo: ele
  é a sua proteção.
* Se a chave **já foi comitada alguma vez**, `git rm --cached` **não resolve** —
  o conteúdo continua no histórico (`git log --all -- arkz-release.jks`). O
  caminho correto é considerar a chave **comprometida** e agir como na §7;
  reescrever o histórico (`git filter-repo`) só é válido se o repositório ainda
  **não** foi compartilhado.
* Não coloque `keystore.properties` em *issues*, *gists*, prints de tela,
  relatórios de erro ou anexos de conversa: ele contém senha em **texto puro**.
* Arquivos que **parecem** inúteis mas são **obrigatórios** no repositório:
  `gradle/wrapper/gradle-wrapper.jar` (é o que faz o `gradlew` funcionar) e
  `gradle/wrapper/gradle-wrapper.properties` (a versão do Gradle, 9.7.1).

---

## 6. Backup da chave: o que fazer hoje

A chave existe **só nesta máquina**. Se este computador falhar hoje, a
identidade do app vai junto. Faça o backup agora — leva dois minutos:

1. **Reúna o que precisa ser guardado**: `arkz-release.jks` +
   `keystore.properties` (as senhas) + o SHA-256 desta documentação (§3).
2. **Separe os dois itens em locais diferentes**: um arquivo JKS protegido por
   uma senha e um gerenciador de senhas (Bitwarden, 1Password, KeePassXC…).
   Guardar a senha *junto* com o keystore anula a proteção.
3. **3-2-1**: 3 cópias, em 2 mídias diferentes, 1 **fora do computador**
   (pendrive/HD externo em outro local, cofre físico ou nuvem com criptografia
   de ponta a ponta).
4. **Criptografe o backup.** Se for enviar o keystore para qualquer nuvem, use um
   contêiner criptografado — ZIP com AES-256 (7-Zip) é o mais simples:

   ```powershell
   # cria arkz-keystore-backup.zip com AES-256 (a senha e pedida no console)
   7z a -tzip -mem=AES256 arkz-keystore-backup.zip arkz-release.jks keystore.properties
   ```

   O `.zip` resultante **pode** ser guardado na nuvem; a senha dele, **não** no
   mesmo lugar (vai para o gerenciador de senhas).
5. **Nunca** mande o `.jks` nem a senha por e-mail, WhatsApp, Telegram ou
   "Notas" compartilhadas.
6. **Teste a restauração** — backup não testado é esperança, não é backup:
   copie o `.jks` para uma pasta temporária e rode
   `keytool -list -v -keystore <copia>.jks -alias arkz`; o `SHA256` mostrado tem
   de ser **idêntico** ao da tabela da §3.
7. **Anote o fingerprint** (SHA-256) em papel, junto do cofre. É o que permite
   provar, anos depois, que a chave guardada é a oficial — e detectar uma troca
   silenciosa.

> **Cenário de perda sem backup:** você não consegue mais publicar atualização
> do `com.arkz.armodelviewer`. Restam apenas caminhos ruins — publicar como
> **app novo** (outro `applicationId`, perdendo a base instalada e as
> avaliações) ou, se o app estiver no **Play Console com Play App Signing**,
> pedir o *upload key reset* (a chave da Google continua assinando; só a chave de
> upload é trocada).

---

## 7. Trocar as senhas, vazamentos e rotação

### Trocar a senha **sem perder a identidade** (recomendado)

O keystore **não** é reescrito: só a senha muda. O certificado, o alias e o
`SHA-256` continuam os mesmos, então as atualizações do app seguem válidas.

```bash
# 1) senha do arquivo (keystore) — pede a antiga e a nova
keytool -storepasswd -keystore arkz-release.jks

# 2) senha da chave do alias arkz — pede a antiga e a nova
keytool -keypasswd -alias arkz -keystore arkz-release.jks
```

Depois **atualize `keystore.properties`** (`storePassword` e `keyPassword`),
confira que o build de release continua assinando
(`apksigner verify --print-certs`) e guarde a senha nova no gerenciador de
senhas.

> A senha atual está em **texto puro** em `keystore.properties` (é o formato que
> o Gradle lê). Enquanto o arquivo não for comitado, o risco é o de qualquer
> arquivo local. Se você quiser mais rigor: use uma senha longa (20+ caracteres),
> **diferente** de qualquer outra senha sua, e troque-a se ela já tiver sido
> copiada, colada em conversa, print ou backup sem criptografia.

### Se a chave (ou a senha) vazar

Assuma o pior: quem tem `arkz-release.jks` + senha pode publicar um APK que
aparelhos já instalados aceitam como atualização legítima — **não existe
revogação** de certificado autoassinado no Android.

| Situação | O que fazer |
|---|---|
| Só a senha apareceu (o `.jks` está seguro) | troque a senha imediatamente (veja acima) e refaça os backups |
| O `.jks` **e** a senha vazaram, app **na Play Store** com Play App Signing | peça *upload key reset* no Play Console: a chave de assinatura real é da Google, então a identidade se mantém |
| O `.jks` **e** a senha vazaram, distribuição fora da Play Store | gere um **keystore novo** e passe a publicar com ele, avisando os usuários que precisam desinstalar/reinstalar (a atualização direta deixa de instalar) |

Gerar um keystore novo (só quando necessário — gera **outra** identidade):

```bash
keytool -genkeypair -v -keystore arkz-release-nova.jks -alias arkz \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Ark-Z Arquitetura Ltda, OU=ArkZ ARModelViewer, O=Ark-Z Arquitetura Ltda, C=BR"
```

Depois: atualize `keystore.properties`, guarde a chave nova (§6), troque os
dados da tabela da §3 (o `SHA-256` muda), avise no [CHANGELOG](CHANGELOG.md) — e
**mantenha a chave antiga** enquanto houver versões antigas do app em uso.

---

## 8. Assinar sem deixar a senha no disco (opcional)

Formas alternativas, do mais simples ao mais rigoroso:

1. **Assistente do Android Studio** — *Build → Generate Signed App Bundle / APK*:
   escolha o `arkz-release.jks` no momento do build e digite as senhas. Nada é
   gravado em disco (evite marcar "remember passwords" em máquina
   compartilhada). É o caminho indicado no README para quem não quer manter
   `keystore.properties`.
2. **Variáveis de ambiente** em vez de arquivo — a ideia é não deixar a senha em
   texto puro na pasta do projeto. O `app/build.gradle.kts` de hoje lê
   `keystore.properties`; se você quiser trocar, o padrão é:

   ```kotlin
   // Exemplo (NAO e o que o projeto usa hoje): mesmas propriedades da
   // signingConfig, porem vindas do ambiente/CI. Assim nenhuma senha fica
   // escrita no disco durante o build.
   storeFile = file(System.getenv("ARKZ_STORE_FILE") ?: "arkz-release.jks")
   storePassword = System.getenv("ARKZ_STORE_PASSWORD")
   keyAlias = System.getenv("ARKZ_KEY_ALIAS") ?: "arkz"
   keyPassword = System.getenv("ARKZ_KEY_PASSWORD")
   ```

   Em CI (GitHub Actions etc.), o equivalente são *secrets* do repositório,
   injetados só na execução — nunca em `.yml` comitado.
3. **Keystore em mídia removível** — a chave fica em um pendrive criptografado e
   `storeFile` aponta para ele apenas durante a publicação.

Qualquer que seja a opção, **não** elimine o backup da §6: a chave continua
sendo o item insubstituível.

---

## 9. Segurança do app (o que o APK carrega)

A chave privada **não** entra no APK: dentro dele vai apenas o **certificado
público** (usado para a verificação da assinatura, e que pode ser lido por
qualquer um — é o esperado). A política de segurança do aplicativo em si
(escopo de relatos, o que o app coleta, etc.) está no [SECURITY.md](SECURITY.md);
aqui ficam os pontos que se **verificam** no arquivo publicado:

| Verificação | Resultado neste projeto |
|---|---|
| Permissões no manifesto do projeto | apenas `CAMERA` (+ `uses-feature` de câmera/ARCore) |
| Permissões no APK (manifest *merged*) | `CAMERA`, `INTERNET`, `VIBRATE`, `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — as três últimas vêm das bibliotecas (ARCore/SceneView/AndroidX), não do código do app |
| Contas, telemetria, anúncios | não existem: sem servidor, sem `Analytics`, sem `Crashlytics` |
| Dados do usuário | ficam no aparelho: capturas em `Pictures/ArkZ ARModelViewer` (MediaStore) e marcadores/textos no armazenamento privado do app |
| Modelos carregados | copiados para o **cache privado** do app antes de abrir, com detecção de formato pelo conteúdo; **não** há execução de script embutido em modelo/imagem |
| URL/intents externos | endereços fixos no código (`util/AppLinks.kt`): site, e-mail, Play Store, compartilhar |
| `isMinifyEnabled` | `false` no release: **não** há R8/ProGuard/obfuscação — e isso não é problema, porque **não há segredo dentro do APK** (obfuscação não protege chave nenhuma; a proteção real é o `.gitignore` + backup) |
| `android:allowBackup` | `true`: o backup do sistema pode levar preferências e marcadores personalizados para a conta Google do usuário; não há credencial de desenvolvedor ali. Para bloquear, use `android:allowBackup="false"` |

Confira você mesmo no APK publicado:

```bash
aapt2 dump badging dist/ArkZ-ARModelViewer-1.0.0.apk         # pacote, SDK e permissoes
apksigner verify --print-certs dist/ArkZ-ARModelViewer-1.0.0.apk   # assinatura/certificado
```

`aapt2` e `apksigner` ficam em `%LOCALAPPDATA%\Android\Sdk\build-tools\<versão>\`.

> O APK é **aberto por natureza**: qualquer um pode descompactá-lo e ler o
> código (que é GPL-3.0). O que a assinatura garante é que o arquivo **não foi
> alterado** depois de sair daqui — vale rodar `apksigner verify` em qualquer
> `.apk` antes de distribuí-lo por um canal alternativo (site, pen drive,
> WhatsApp) e comparar o SHA-256 do arquivo com o das notas do *release*.

---

## 10. Guardar o projeto: limpeza dos arquivos temporários

Para arquivar/transportar o projeto, o que **pesa** e não serve para nada são as
saídas de build e os caches locais. Foram removidos:

```powershell
gradlew.bat --stop                             # fecha os daemons do Gradle (nao travam arquivos)
Remove-Item app\build  -Recurse -Force         # saidas do modulo app  (~419 MB)
Remove-Item build      -Recurse -Force         # saidas da raiz
Remove-Item .gradle    -Recurse -Force         # cache/estado local do Gradle
Remove-Item .kotlin    -Recurse -Force         # sessoes do compilador Kotlin 2.x
Remove-Item .idea\workspace.xml -Force         # estado local do Android Studio
git gc --quiet                                 # compacta o .git (objetos soltos -> pack)
```

| Item | Antes | Depois |
|---|---|---|
| `app/` | 419,0 MB | **0,34 MB** |
| `.gradle/` + `build/` + `.kotlin/` | 3,5 MB | **0** |
| `.git/` | 9,36 MB (192 objetos soltos) | **9,13 MB** (2 *packs*) |
| projeto (sem `dist/`) | ≈ 433 MB | **≈ 20 MB** |

**Nunca apague** (não é regenerável ou é necessário para compilar/publicar):

* `arkz-release.jks` e `keystore.properties` — a identidade do app (§1 a §6);
* `local.properties` — atalho para o SDK nesta máquina (pequeno e útil);
* `gradle/wrapper/gradle-wrapper.jar` e `...properties` — sem eles o `gradlew`
  não roda (parecem "arquivo binário inútil", mas são o build inteiro);
* `dist/ArkZ-ARModelViewer-1.0.0.apk` — APK assinado da versão publicada;
* `tools/`, `docs/`, `images/`, `3d_models/` — material do projeto e das
  *releases*.

Para voltar a compilar depois da limpeza (o Gradle baixa/regenera tudo):

```bash
gradlew.bat :app:assembleDebug     # APK de depuracao
gradlew.bat :app:assembleRelease   # APK de release assinado (precisa do keystore.properties)
```

Ao **zipar** o projeto para guardar, decida conscientemente:

* cópia **sem** a chave (para nuvem pública/GitHub): exclua `arkz-release.jks`,
  `keystore.properties`, `local.properties`, `.idea/` e `dist/`;
* cópia **com** a chave (backup pessoal): mantenha tudo, mas grave o ZIP em mídia
  criptografada (§6) — nunca em serviço de arquivos aberto.

---

## 11. Referências

* Android — *Sign your app*:
  <https://developer.android.com/studio/publish/app-signing>
* Android — *Play App Signing*:
  <https://support.google.com/googleplay/android-developer/answer/9842756>
* `keytool` (JDK): <https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html>
* `apksigner` (Android SDK Build Tools):
  <https://developer.android.com/tools/apksigner>
* GitHub — *Removing sensitive data from a repository*:
  <https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository>

---

## Checklist rápido

* [ ] `arkz-release.jks` **não** está no Git (`git ls-files | findstr /i jks`
      vazio).
* [ ] `keystore.properties` **não** está no Git e não foi copiado para conversa
      ou print.
* [ ] Backup da chave feito **hoje**, criptografado, em local separado das
      senhas, com restauração testada (§6).
* [ ] `SHA-256` da chave guardado fora do computador e igual ao da §3.
* [ ] `apksigner verify --print-certs` do APK publicado mostra
      `CN=Ark-Z Arquitetura Ltda` e o mesmo `SHA-256`.
* [ ] Antes de zipar/guardar: `app/build`, `build`, `.gradle`, `.kotlin` e
      `.idea/workspace.xml` removidos (ou já ignorados pelo Git).

