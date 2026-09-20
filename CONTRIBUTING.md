# Como contribuir

Obrigado pelo interesse no **ArkZ ARModelViewer**! O projeto é pequeno e mantido
por uma pessoa, então o mais útil é **relatar problemas** e **sugerir melhorias**
com contexto.

## Antes de abrir uma issue

1. Leia a seção **Solução de problemas** do [README](README.md) — os casos mais
   comuns (marcador não reconhecido, modelo muito grande/pequeno, onde a captura é
   salva, idioma errado) já estão documentados.
2. Confira se você está na versão mais recente das
   [releases](https://github.com/em-rezende/Android-ArkZ-ARModelViewer/releases).
3. Tenha em mãos o **diagnóstico do próprio app**: menu ⋮ → **Copiar
   diagnóstico** (traz versão, aparelho, modelo/marcador atual e o último status).

## Relatando um problema

Use o modelo de *issue* **Bug** com: aparelho e versão do Android, versão do app,
arquivo de modelo (formato e tamanho aproximado), marcador usado (embutido ou
personalizado) e o que aconteceu. O Logcat ajuda muito:

```bash
adb logcat -s ArkZARModelViewer
```

## Sugerindo uma melhoria

Abra uma *issue* **Ideia** descrevendo **o problema que você tem hoje**, não só a
solução imaginada — isso deixa o objetivo claro e permite soluções melhores.

## Enviando código

1. Faça um *fork* e crie uma branch a partir de `main`
   (`git checkout -b fix/descricao-curta`).
2. Mantenha o estilo do projeto:
   * comentários e KDoc **em português**;
   * texto da interface em `app/src/main/res/values/strings.xml` (padrão pt-BR)
     **e** nos oito idiomas ao lado (`values-en/`, `values-pt-rPT/`, `values-es/`,
     `values-fr/`, `values-de/`, `values-it/`, `values-zh-rCN/` e `values-pt-rBR/`).
     Uma string nova sem tradução cai no padrão automaticamente, mas num app com
     tradução completa isso aparece como texto em português no meio de outra
     língua;
   * nada de acento nos scripts `.ps1` (o PowerShell 5.1 lê arquivos sem BOM como
     ANSI);
   * textos longos quebrados em ~80 colunas, como no restante do repositório.
3. Valide antes de enviar:

   ```bash
   gradlew.bat :app:assembleDebug
   gradlew.bat :app:lintVitalRelease
   ```

4. Atualize o [CHANGELOG.md](CHANGELOG.md) (adicione uma seção *Não publicado* no
   topo) quando a mudança for visível para o usuário.
5. Abra o *pull request* explicando **o que** muda e **por quê**; se der, com
   capturas de tela da cena de RA.

## Regras rápidas

* Um assunto por *pull request* — fica mais fácil revisar e reverter.
* **Nunca** comite credenciais ou a chave de assinatura (`*.jks`, `*.keystore`,
  `keystore.properties`): o `.gitignore` já cobre, e sem a chave original não é
  possível publicar atualizações do mesmo app.
* Modelos 3D de teste grandes: informe um link, não anexe ao repositório.
* A licença não se negocia: o projeto é **GPL-3.0** (veja [LICENSE](LICENSE)) e as
  contribuições entram sob os mesmos termos.
