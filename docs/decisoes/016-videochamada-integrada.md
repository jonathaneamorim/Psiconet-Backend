# ADR 016 — Videochamada integrada (Sala Psiconet)

- **Status:** aceito
- **Data:** 2026-10-23
- **Afeta:** backend e frontend (ADR 003 do front referencia este)

## Contexto
Toda consulta on-line dependia de um link externo (Meet/Zoom) colado pelo psicólogo. O modelo já
tinha `meetingProvider = IN_APP` e `roomId` ("preparado para futuras") sem uso. Queremos uma sala
integrada para sessões entre psicólogo e paciente — **dados sensíveis de saúde** — sem que a
Psiconet passe a operar mídia em tempo real (signaling, STUN/TURN, SFU, escala).

Restrições: o JWT de sessão está em cookie `HttpOnly` (o navegador não autentica direto na API);
o backend é a autoridade de permissão; **nenhuma gravação** de áudio/vídeo; LGPD.

## Alternativas consideradas (verificadas em 2026-10-23, preços públicos em USD)

| Critério | **LiveKit** (Cloud ou self-host) | Daily | Jitsi as a Service (8x8) | Twilio Video |
|---|---|---|---|---|
| Situação | Ativo; servidor código aberto (Apache-2.0) | Ativo | Ativo | Ativo (Twilio reverteu o fim do produto anunciado para 2026) |
| Preço | Build grátis: 5.000 participante·min + 50 GB/mês. Ship US$ 50/mês: 150 mil min, depois **US$ 0,0005/participante·min**; banda US$ 0,12/GB após 250 GB | 10 mil min grátis/mês, depois **US$ 0,004/participante·min** | Por usuário ativo/mês: grátis até 25 MAU; US$ 99 (300 MAU), US$ 499 (1.500 MAU) | **US$ 0,004/participante·min** (salas em grupo) |
| Auto-hospedagem / residência de dados | **Sim** (servidor próprio no Brasil); na Cloud, *region pinning* só no plano Scale | Não | Jitsi Meet é auto-hospedável, mas o JaaS não | Não |
| Token emitido pelo backend | JWT HS256 documentado (fácil sem SDK) | Token REST | JWT RS256 | JWT |
| React 19 / Next 16 | `@livekit/components-react` 2.9.24 (peer `react >=18`) + `livekit-client` 2.22.4 | `@daily-co/daily-react` 0.26 (React 19 ok) | `@jitsi/react-sdk` (iframe) | `twilio-video` (sem componentes React oficiais) |
| Limite de 2 na sala | `max_participants` na sala e no token | `max_participants` | Configurável via moderação | `maxParticipants` |
| Webhooks de presença | Sim (assinados, JWT + SHA-256 do corpo) | Sim | Sim | Sim |
| Gravação | Só se pedida (Egress); **desligada por padrão** | Só se pedida | Só se pedida | Só se pedida |
| Plano de saída | Self-host do mesmo servidor | — | Jitsi self-host (outro protocolo) | — |

Fontes: livekit.com/pricing, daily.co/pricing/video-sdk, twilio.com/en-us/video/pricing,
changelog da Twilio ("Twilio Video will remain a standalone product"), registros npm e Maven Central.

## Decisão
1. **Provedor: LiveKit**, começando pela **LiveKit Cloud** (plano grátis/Ship) e com caminho direto
   para **auto-hospedagem no Brasil** (mesmo protocolo) se o jurídico exigir residência de dados.
2. **Abstração `VideoProvider`** (`infra/video`): `ensureRoom`, `issueToken`, `closeRoom`,
   `parseWebhook`. Implementação única `LiveKitVideoProvider`, escolhida por `VIDEO_PROVIDER`.
3. **Sem o SDK Java oficial** (`io.livekit:livekit-server` 0.16.0): ele traz Kotlin 2.3, OkHttp 5,
   Retrofit 3, Protobuf 4 e Jackson 2.22, que conflitam com o BOM do Spring Boot 3.3.5. O protocolo
   usado é pequeno e coberto por testes de contrato: token JWT HS256 via `java-jwt` (já no projeto),
   API Twirp/JSON (`CreateRoom`, `DeleteRoom`) via `RestClient` e verificação do webhook
   (JWT do cabeçalho `Authorization` + claim `sha256` = SHA-256 base64 do corpo bruto).
4. **Fluxo de entrada:** o front chama `POST /appointments/{id}/video/join` por **Server Action**;
   o backend valida e devolve `{ serverUrl, token, role, ... }`; o navegador conecta direto ao
   provedor. O token fica **só em memória**.
5. **Regras de acesso (backend):**
   - só o psicólogo e o paciente **do vínculo da consulta**; terceiro ou consulta inexistente ⇒ **404**
     (mesma resposta, anti-IDOR — padrão do projeto; preferido ao 403 sugerido para não confirmar
     a existência de consultas de terceiros);
   - só `VIDEO_CALL + IN_APP`, situação `ACCEPTED` ou `COMPLETED` (o job de ciclo de vida conclui a
     consulta no fim do horário, mas a sala segue aberta pela tolerância), nunca `CANCELLED`/`NO_SHOW`;
     vínculo ativo;
   - **janela:** abre `VIDEO_JOIN_OPENS_BEFORE` (30 min; era 10 min até a revisão de 2026-10-23, ampliado
     para dar tempo de testar câmera/microfone e aguardar com folga) antes do início e fecha
     `VIDEO_JOIN_CLOSES_AFTER` (15 min) após o fim. Fora dela: 409 com `code`
     `VIDEO_WINDOW_NOT_OPEN` (mensagem com o horário de abertura) ou `VIDEO_WINDOW_CLOSED`;
   - rate limit `video-join` (20 / 10 min por usuário e por IP) e log de cada emissão
     (`log_sessao_video.TOKEN_ISSUED` + log de aplicação **sem o token**).
6. **Token mínimo:** `roomJoin` na sala da consulta, `canPublish`/`canSubscribe`,
   `canPublishSources = [camera, microphone]`, `canPublishData = false` (sem chat), sem
   `roomAdmin`/`roomRecord`/`roomCreate`/`roomList`, validade `VIDEO_TOKEN_TTL` (10 min; o provedor
   renova a sessão já conectada), identidade = id do usuário, **sem nome, e-mail ou metadados**.
   `roomConfig.maxParticipants = 2` no token e `max_participants = 2` no `CreateRoom`.
7. **Sala:** nome `psn_<roomId>`, onde `roomId` = 128 bits de `SecureRandom` (base64url), gerado na
   criação da consulta e **em cada ocorrência materializada** da recorrência (nunca copiado da regra).
   `roomId` nunca sai em DTO.
8. **Anfitrião e sala de espera:** o psicólogo é o anfitrião (`role = HOST`) e é o único que pode
   **encerrar para todos** (`POST /appointments/{id}/video/end` ⇒ `DeleteRoom`). O paciente pode
   entrar dentro da janela e **aguarda** na sala com a tela "Aguardando o psicólogo entrar…" até o
   psicólogo conectar; como a sala só admite as duas identidades da consulta, não há exposição a
   terceiros. Quando o psicólogo entra pela primeira vez, o paciente recebe a notificação
   `VIDEO_PARTICIPANT_JOINED`. Sala de espera com admissão manual fica no backlog.
   **Ciclo de vida da sala (revisão de 2026-10-23):**
   - a sala **nasce quando alguém entra** (`CreateRoom` idempotente no `/video/join`; o `roomConfig` do
     token reforça). Sem ninguém conectado, o provedor a descarta em `empty-timeout` (1 min);
   - **todos saíram ⇒ sala fechada** após `departure-timeout` (5 s, folga para recarregar a página; o
     LiveKit trata 0 como o padrão de 20 s). Nova entrada dentro da janela **reabre** a sala (mesmo
     `roomId`, credencial nova);
   - **sozinho por `VIDEO_ALONE_TIMEOUT` (15 min) ⇒ desconectado**, esteja esperando a outra pessoa ou
     depois que ela saiu. O front avisa nos 2 últimos minutos e desconecta; o backend garante
     (`VideoRoomWatchdog`, a cada 1 min, consulta as salas `psn_*` no provedor e remove pelo
     `RemoveParticipant` quem segue sozinho após o limite + 1 min). Estado em memória, como o rate limit:
     reiniciar a API só recomeça a contagem. A pessoa pode entrar de novo enquanto a janela estiver aberta.
   - **limite de horário** `VIDEO_HARD_STOP_AFTER_END` (60 min após o fim, nunca antes do fechamento da
     entrada): a janela só controla a **entrada**, então sem isso uma sessão conectada (ou duas contas
     combinadas) poderia durar indefinidamente, consumindo o provedor. A credencial traz `hardStopAt`; o front
     avisa nos 5 últimos minutos e sai; o mesmo job encerra a sala (`DeleteRoom`) 1 min depois. Sala `psn_*`
     sem consulta correspondente também é encerrada.
   - **robustez:** cada execução do job tem orçamento de 20 s (o resto fica para a próxima) e os jobs
     agendados rodam num pool de 3 threads (`spring.task.scheduling.pool.size`), para um provedor lento não
     atrasar lembretes, ciclo de vida ou pagamentos.
   - **webhook público com teto de corpo** (`psiconet.video.webhook-max-body-size`, 64 KB): recusado com
     413 `PAYLOAD_TOO_LARGE` antes de ser lido para a memória (a API é exposta sem proxy). O resto da API tem
     teto de 2 MB (`psiconet.http.max-body-size`, `RequestSizeLimitFilter`); uploads seguem `spring.servlet.multipart.*`.
   - Verificado contra um LiveKit 1.13.10 local: campos snake_case no Twirp; `ListParticipants` e
     `RemoveParticipant` exigem `roomAdmin` com `room` no token de servidor.
9. **Webhook** `POST /webhooks/video` (público só no `SecurityConfig`, validado pela assinatura;
   inválido ⇒ 401): `participant_joined`, `participant_left`/`participant_connection_aborted` e
   `room_finished` viram linhas em `log_sessao_video` (idempotente pelo id do evento). **Só registra:
   não altera status de consulta nem pagamentos** nesta entrega.
10. **Lembretes:** job a cada 1 min envia `VIDEO_SESSION_REMINDER` (notificação + e-mail
    `lembrete-sessao-video`) ~15 min antes, para as duas partes, respeitando a preferência
    "agendamento"; marcação atômica (`lembrete_video_enviado_em`) evita duplicidade.
    Sem nome da outra parte nem conteúdo clínico.
11. **Feature flag** `psiconet.video.enabled` (`VIDEO_ENABLED`, padrão `false`). Desligada **ou sem
    URL/chave/segredo**: `GET /video/config` devolve `enabled=false`, a opção some do agendamento,
    criar `IN_APP` é recusado (400 em `meetingProvider`), entrar devolve 503 `VIDEO_UNAVAILABLE`, o job
    de lembrete não roda e o link externo segue funcionando. Consultas `IN_APP` já criadas continuam na
    agenda (sem botão de entrada efetivo).
12. **Link externo** passa a ser validado no backend: só `http`/`https` com host, sem credenciais
    na URL, sem espaços, até 255 caracteres; sem esquema recebe `https://`. Provedor omitido em
    `VIDEO_CALL` = `EXTERNAL_LINK` (compatibilidade).
13. **Fora do escopo (decisão):** gravação/transcrição (**proibidas** nesta entrega), chat,
    compartilhamento de tela. O token nem permite canal de dados ou tela.
14. **Fuso:** como o resto do sistema, `LocalDateTime` no fuso do servidor. Em produção o container
    da API precisa rodar em `America/Sao_Paulo` (`TZ`), senão a janela e os lembretes ficam
    deslocados — mesmo risco que já existe para o job de ciclo de vida.

## Custo estimado
Sessão de 50 min com 2 participantes = 100 participante·min.
- **LiveKit Cloud Build (grátis):** 5.000 min/mês ≈ **50 sessões/mês**.
- **LiveKit Ship:** US$ 50/mês cobre 150 mil min (≈ 1.500 sessões). Excedente: US$ 0,0005 × 2 × 60 =
  **US$ 0,06 por sessão-hora** + banda (vídeo 720p adaptativo ≈ 0,5–0,7 GB por participante·hora;
  após 250 GB, US$ 0,12/GB ⇒ ~US$ 0,12–0,17 por sessão-hora).
- Daily / Twilio: US$ 0,004 × 2 × 60 = **US$ 0,48 por sessão-hora** (Daily com 10 mil min grátis).
- Self-host LiveKit: custo fixo de VM (+ TURN) no Brasil, independente do volume; exige operação.

## Consequências
- O **provedor de vídeo é operador de dados** (mídia em trânsito, IP e dados técnicos) e entrou na
  Política de Privacidade 1.1.0 e nos Termos 1.1.0 (ausência de gravação, ambiente privado, proibição
  de gravar sem consentimento). Possível **transferência internacional** na LiveKit Cloud — validar com
  o jurídico (DPA disponível nos planos; *region pinning* só no Scale ou via self-host).
- Navegador acessa o provedor: o front libera **apenas** as origens de `VIDEO_CSP_ORIGINS` em
  `connect-src` e `camera`/`microphone` = `self` **só** em `/call/*`.
- Trocar de provedor: nova implementação de `VideoProvider` + troca do componente de sala no front;
  contrato da API (`/video/join`) e dados (`roomId`, `log_sessao_video`) permanecem.
- Riscos: dependência de terceiro (indisponibilidade ⇒ 503 amigável e link externo como contingência);
  ausência de residência de dados na Cloud; fuso do servidor; buckets de rate limit em memória.

## Backlog sugerido
Gravação com consentimento explícito das duas partes; chat efêmero (não persistido); compartilhamento
de tela; reagendamento preservando `roomId`; sala de espera com admissão manual; métricas de qualidade
(webhooks de qualidade/estatísticas); uso do log de presença para sugerir `NO_SHOW` (com revisão humana).
