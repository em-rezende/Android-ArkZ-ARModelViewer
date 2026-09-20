# Política de segurança

## Versões com suporte

| Versão | Suporte |
|---|---|
| 1.0.x (atual) | ✅ correções de segurança |
| anterior à 1.0.0 | ❌ não houve versão pública |

## Como relatar uma vulnerabilidade

**Não abra uma issue pública** para falhas de segurança. Envie um e-mail para
**emrezende@gmail.com** com:

* descrição do problema e do impacto (o que um atacante consegue fazer);
* versão do app (menu ⋮ → **Sobre**) e aparelho/versão do Android;
* passos para reproduzir (ou um PoC/vídeo);
* se possível, uma sugestão de correção.

Confirmação de recebimento em até **7 dias**. A correção é publicada como uma nova
versão nas [releases](https://github.com/em-rezende/Android-ArkZ-ARModelViewer/releases)
e registrada no [CHANGELOG](CHANGELOG.md) — com crédito ao relator, se ele quiser.

## Escopo

O app **não coleta nem envia dados**: não há contas, servidor, anúncios ou
telemetria. Os modelos carregados, as capturas de tela e os marcadores
personalizados ficam no próprio aparelho (galeria e armazenamento privado do app),
e a única permissão pedida é a **câmera** (exigida pelo ARCore).

Relatos que fazem sentido aqui:

* *crash* ou consumo de memória descontrolado com arquivos de modelo malformados
  (`.glb`, `.obj`, `.ply`, `.stl`, `.3mf`);
* caminhos que escapem do sandbox do app ao salvar capturas/marcadores ou ao ler
  arquivos escolhidos no seletor do Android;
* problemas nos *intents* externos (abrir site, e-mail, Play Store, compartilhar) —
  por exemplo, abrir algo diferente do endereço exibido na tela;
* qualquer forma de execução de código a partir de um arquivo de modelo ou imagem
  de marcador (o app **não** interpreta scripts embutidos nesses arquivos).

Não é escopo: aparelho sem ARCore, câmera com defeito, marcador mal impresso e
dúvidas de uso (use as *issues* ou a seção *Solução de problemas* do README).
