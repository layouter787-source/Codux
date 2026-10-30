# Brief para Claude: concluir a integração do Codux

Ajuda a implementar, corrigir e validar as funcionalidades planejadas para o Codux. Lê primeiro o repositório e confirma o estado atual; este documento é contexto, não prova de que qualquer fluxo funciona.

## Repositório e objetivo

- Repositório: <https://github.com/layouter787-source/Codux.git>
- Projeto: aplicativo Android nativo escrito em Kotlin com Jetpack Compose Material 3.
- Objetivo do produto: ambiente de desenvolvimento móvel com IA, com chat ligado ao Codex App Server, histórico, projetos/arquivos SAF, terminal Linux integrado, tema claro/escuro e definições.
- Objetivo atual de integração: usar terminal/emulador e runtime Linux privado dentro do Codux, sem exigir a instalação ou abertura de um segundo app Termux. O runtime deve vir de componentes upstream compatíveis com as respetivas licenças, e o Codex CLI Android deve ser incluído no ambiente quando tecnicamente viável.
- O utilizador pediu que não seja instalado nenhum APK; ele fará a instalação. A autorização para enviar alterações ao remoto já foi dada nesta conversa.

## Arquitetura já identificada

- UI Android em Kotlin/Compose Material 3.
- `CoduxViewModel` mantém estado e liga chat, threads, streaming, aprovações, interrupção de turnos, arquivos e execução de comandos.
- `CodexAppServerClient` liga por WebSocket local autenticado ao App Server.
- `TermuxBridge` inicia processos no runtime privado e o App Server; o launcher já não depende da permissão externa `RUN_COMMAND` do Termux.
- `EmbeddedTermuxRuntime` extrai o bootstrap para `filesDir/usr` e usa `filesDir/home` como home privada.
- Bibliotecas upstream do emulador/PTTY estão sob `third_party/termux-terminal/` e incluem arquivos de licença/notices.
- `ProjectFiles` trabalha com Android SAF. `ProjectWorkspaceSync` espelha a árvore SAF para um caminho POSIX privado durante operações do terminal/Codex e sincroniza alterações de volta.
- O bootstrap atual é apenas ARM64. O fluxo de autenticação planeado usa `codex login --device-auth` no terminal integrado; ainda requer confirmação em dispositivo.

## Estado do código e Git

O último commit conhecido ao criar este ficheiro é `7ec15ba` (`Limit Termux bootstrap build parallelism`), após `773be09` (`Bundle Android Codex CLI in embedded Termux runtime`). Confirma o `git log` e o `git status` antes de alterar coisas.

Existem três diretórios locais não rastreados, usados para inspeção: `.codux-proof/`, `.termux-packages-src/` e `.termux-src/`. Não os apagues nem os adiciones ao Git.

## CI e evidência disponível

- O workflow Android é `.github/workflows/android.yml`. O run `40987300153`, no commit `773be09`, passou os testes unitários e `assembleDebug`, mas baixou o bootstrap publicado anteriormente, que ainda não continha Codex CLI. Portanto, esse run não prova que o novo APK contém Codex.
- O bootstrap Termux sem Codex tinha sido compilado/publicado antes; o workflow atual reutiliza o release `codux-termux-bootstrap-v1` e substitui os assets ao concluir uma nova compilação.
- O workflow `.github/workflows/termux-bootstrap.yml` foi alterado para incluir o pacote Android `codex` do repositório oficial `termux/termux-packages`, fixado no commit `7e1a5db289ad89f97bb295a515ea3096b87eb1cd`.
- O pacote oficial declara licenças Apache-2.0 e MIT e instala `codex` e `codex-code-mode-host`. Preserva os arquivos de licença/notices incluídos pelo pacote.
- A primeira compilação com Codex, run `40987306164`, falhou. A anotação do GitHub foi: “The hosted runner lost communication with the server.” Não foi identificada falha específica de compilador. O log mostra que o build chegou a compilar dependências nativas; não afirmar a causa exata além da anotação.
- Para reduzir consumo de recursos, o workflow agora passa `TERMUX_PKG_MAKE_PROCESSES=2` ao builder. A nova execução `41003891727` estava em progresso na etapa “Build Termux bootstrap for Codux package paths” no último ponto verificado. Confirma o estado atual antes de avançar.
- URL da execução: <https://github.com/layouter787-source/Codux/actions/runs/41003891727>

## Trabalho a concluir

1. Audita os fluxos existentes sem reescrever arquitetura nem remover funcionalidades.
2. Confirma o estado/logs dos workflows atuais. Se a compilação do bootstrap falhar, identifica a causa em logs/annotations e faz uma correção concreta. Se voltar a haver perda de runner, avalia memória/paralelismo e opções de compilação; não assumes que `2` resolveu.
3. Verifica que o ZIP ARM64 tem `bin/codex`, `bin/codex-code-mode-host`, `SYMLINKS.txt`, dependências dinâmicas e notices/licenças. Confirma os caminhos no ZIP real, não apenas no YAML.
4. Confirma que `EmbeddedTermuxRuntime` extrai o formato real do ZIP para `filesDir/usr`, preserva links, permissões executáveis e home/autenticação em atualização. Atualizar o prefixo não deve apagar a pasta `home` nem credenciais do utilizador.
5. Audita a compatibilidade do Codex CLI/App Server com Android ARM64: ambiente, dependências, login, processo em foreground/background, DNS/rede e mensagens de erro. Não inventes APIs nem afirmes autenticação real sem teste em dispositivo.
6. Depois de publicar o bootstrap validado, dispara manualmente `.github/workflows/android.yml`, confirma o download com checksum, os testes, `assembleDebug` e o artefacto APK resultante. Não instales APK.
7. Revisa o restante escopo descrito no README: chat/histórico/configurações, SAF e editor com salvamento seguro/diffs, pedidos explícitos de autorização do terminal, streaming de output, stdin, cancelamento e timeout. Acrescenta/corrige testes onde fizer sentido e verifica regressões.
8. Podes fazer commits e push para `main`, autorização já concedida. Nunca exponhas tokens/passwords nem adiciones os três diretórios locais não rastreados.

## Critério de comunicação

Distingue claramente entre código compilado, runtime incluído no artefacto e comportamento testado num dispositivo. Só declara sucesso quando a execução real correspondente estiver verde e o artefacto correto tiver sido inspecionado. No relatório final, lista arquivos alterados, testes/workflows reais, links dos artefactos e limitações de dispositivo ainda não verificadas.

## Atualização após o PR de revisão (2026-10-30)

Foi aberto o PR #1 (`fix/embedded-termux-runtime`, head `edafc4b`) com testes Android verdes. Os três commits de app foram revistos e aplicados a `main` como commits `feaf55e`, `52161e7` e `028e8a3`:

- `targetSdk` passou a 28 para permitir executar os binários extraídos em `filesDir` segundo a restrição W^X documentada pelo Android. Isto é uma build de sideload; não é adequada para distribuição pelo Google Play, que exige targets recentes.
- O ambiente integrado escolhe o nome atual/legado de `termux-exec`, passa variáveis Android/TLS necessárias e preserva `home` ao substituir o prefixo.
- O terminal integrado recebeu redesenho de atualização da tela, teclado, clipboard, teclas ESC/TAB/Ctrl/setas, dimensão de texto em px e login shell.

A proposta do PR para `.github/workflows/termux-bootstrap.yml` foi portada ao workflow ativo, mas **não foi feito merge do PR**. Ela adiciona espaço em disco e swap, reduz paralelismo e LTO, imprime diagnóstico em falha, guarda o ZIP para inspeção e corrige a extração da versão do CLI/SIGPIPE em `grep -q`. A validação foi reforçada para exigir executáveis, `termux-exec`, certificados TLS e `LICENSE`/`NOTICE` do Codex.

O run `41003891727` foi cancelado após se decidir aplicar estas correções ao workflow; não o classificar como falha do novo workflow. O próximo passo é enviar estas alterações, despachar uma nova execução de bootstrap e aguardar a compilação real. O APK ainda deve ser recompilado com o bootstrap publicado e inspecionado antes de alegar que contém Codex.
