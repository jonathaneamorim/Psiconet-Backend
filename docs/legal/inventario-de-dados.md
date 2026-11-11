# Inventário de dados pessoais, cookies e terceiros

> Levantado **a partir do código** (backend + frontend) em 2026-10-02, branch `feature/legal-consentimento-sessao`.
> É a base factual dos documentos de `docs/legal/`. Atualize este arquivo **antes** de mudar qualquer
> documento legal sempre que o sistema passar a coletar um dado novo, criar um cookie ou integrar um terceiro.
> Bases legais e prazos são **sugestões técnicas — validar com jurídico**.

## 1. Como a plataforma funciona (papéis e fluxos reais)

| Tema | O que o sistema faz de fato |
|---|---|
| Natureza | **Intermediação tecnológica**: busca de psicólogos, pedido de conexão, vínculo de tratamento, agenda, registro de cobranças, prontuário eletrônico e suporte. A Psiconet **não presta serviço psicológico**. |
| Atendimento | Presencial (endereço informado) ou on-line: **Sala Psiconet** (vídeo integrado via provedor SFU/WebRTC — LiveKit, ADR 016; sem gravação) ou **link externo** colado pelo psicólogo (Google Meet, Zoom etc.). |
| Pagamentos | **Não processados pela plataforma.** O psicólogo cadastra a chave Pix; o paciente paga fora da plataforma e envia o **comprovante** (upload); o psicólogo aprova/rejeita; o paciente pode contestar. |
| Prontuário | Mantido pelo psicólogo titular do vínculo, **cifrado** (AES-256-GCM por campo, chave por prontuário). Admin nunca vê conteúdo clínico. O paciente **solicita** acesso e o psicólogo libera (leitura/PDF) ou nega. |
| Suporte | Chamados (protocolo) atendidos pela administração ("Equipe Psiconet"). |
| Administração | Admins gerenciam usuários (status), outros admins, comunicados e chamados. |
| Cadastro | Público para paciente e psicólogo; admins só são criados por outro admin. |

## 2. Cookies e armazenamento no navegador

| Nome | Finalidade | Categoria | Duração | Flags | Quem define |
|---|---|---|---|---|---|
| `psiconet_token` | Token de acesso (JWT) da sessão autenticada | Estritamente necessário | Igual à validade do token de acesso (padrão **15 min**); sem "manter conectado" é cookie de sessão | `HttpOnly`, `Secure` (produção), `SameSite=Lax`, `Path=/` | Servidor Next (`cookieService`) |
| `psiconet_refresh` | Renova o token de acesso sem novo login (sessão por dispositivo, rotativo) | Estritamente necessário | Sem "manter conectado": **cookie de sessão** (some ao fechar o navegador; no servidor expira após 2 h sem uso e 12 h no total). Com "manter conectado": **7 dias** sem uso, renovado a cada uso, limite absoluto de **30 dias** | `HttpOnly`, `Secure` (produção), `SameSite=Lax`, `Path=/` | Servidor Next (`cookieService`) |
| `psiconet_consent` | Guarda a escolha do banner de cookies (versão da política, categorias, data) | Estritamente necessário | **180 dias** | `Secure` (produção), `SameSite=Lax`, `Path=/` (sem `HttpOnly` para que scripts futuros possam respeitar a escolha) | Servidor Next (Server Action) |

- **`localStorage`, `sessionStorage`, IndexedDB: não utilizados** (o editor de anotações clínicas proíbe explicitamente gravar conteúdo no navegador).
- O backend é *stateless* e **não define cookies**.
- `nextjs-toploader` e `react-hot-toast` não gravam nada no navegador.

## 3. Terceiros

| Item | Situação |
|---|---|
| Analytics, pixels, tags de marketing, chat de terceiros | **Nenhum.** |
| Fontes | Raleway via `next/font/google`, **baixada no build e servida pelo próprio domínio** (sem requisição ao Google em tempo de execução). |
| Scripts / iframes / embeds / CDNs | **Nenhum** em tempo de execução. |
| Mapas | Apenas **link de saída** para o Google Maps no detalhe de consulta presencial (o navegador só acessa o Google se o usuário clicar). |
| Videoconferência | **Sala Psiconet:** provedor de vídeo (LiveKit Cloud ou auto-hospedado — `[A PREENCHER: contrato/DPA, região]`) recebe a mídia **em trânsito**, IP/dados técnicos, nome de sala opaco e id interno do usuário; **sem nome, e-mail ou dado clínico; sem gravação**. Navegador conecta direto ao provedor (origens em `VIDEO_CSP_ORIGINS`). O SDK **não grava cookies nem `localStorage`** na configuração usada (verificado no `livekit-client` 2.22.4 / `@livekit/components-react` 2.9.24). **Link externo:** informado pelo psicólogo; a política do serviço escolhido se aplica. |
| E-mail transacional | Servidor SMTP configurável (`MAIL_HOST`, padrão `smtp.gmail.com`). Se for Google Workspace/Gmail, há **transferência internacional** (ver Política de Privacidade). |
| Hospedagem/banco | `[A PREENCHER: provedor de hospedagem e região]`. |

## 4. Dados pessoais tratados

Legenda de base legal (LGPD, **validar com jurídico**): *Contrato* = execução de contrato (art. 7º, V);
*Obrigação* = cumprimento de obrigação legal/regulatória (art. 7º, II); *LI* = legítimo interesse (art. 7º, IX);
*Exercício de direitos* (art. 7º, VI); *Consentimento específico e destacado* para dado sensível (art. 11, I);
*Tutela da saúde* por profissional de saúde (art. 11, II, "f").

| Dado | Onde (tabela/entidade) | Finalidade | Base legal sugerida | Quem acessa | Retenção sugerida | Sensível? |
|---|---|---|---|---|---|---|
| Nome, e-mail, senha (hash BCrypt) | `usuario` | Conta, login, comunicação | Contrato | Titular; admin (nome/e-mail) | Enquanto a conta existir + prazo de defesa (5 anos após encerramento) | Não |
| CPF | `usuario.cpf` | Identificação única, antifraude | Contrato / LI | Titular; admin vê **mascarado** (`•••.456.789-••`) | Idem conta | Não (mas de alto risco) |
| Data de nascimento | `usuario.data_nascimento` | Elegibilidade (maioridade), identificação | Contrato | Titular, admin | Idem conta | Não |
| Telefone, endereço/localização | `usuario` (`Location`) | Perfil, contato, consulta presencial | Contrato | Titular; vínculo (conforme tela) | Idem conta | Não |
| Foto de perfil | `uploads/avatars`, `usuario.foto` (URL pública) | Identificação visual | Consentimento (envio opcional) | **Público** (URL sem autenticação) | Até remoção/troca | Não |
| CRP, especialidades, bio, experiência, endereço do consultório, chave Pix | `psicologo` | Perfil profissional, cobrança | Contrato | Público (perfil) / pacientes vinculados (Pix) | Idem conta | Não |
| Conexões e **vínculo de tratamento** | `conexao`, `paciente_piscologo` | Relacionar paciente e psicólogo | Contrato + **Consentimento específico** (revela que a pessoa faz terapia) | As duas partes | Vínculo: mesmo prazo do prontuário | **Sim (saúde)** |
| Consultas e recorrências | `agendamento`, `regra_recorrencia` | Agenda | Contrato + Consentimento específico | As duas partes | Mesmo prazo do prontuário | **Sim (saúde)** |
| Pagamentos e **comprovantes** (arquivo) | `pagamento`, `uploads/receipts` | Controle financeiro entre as partes | Contrato | As duas partes | 5 anos (fiscal/defesa) | Não |
| Prontuário, anotações, adendos | `prontuario`, `anotacoes_sessao`, `anotacao_adendo` (cifrados) | Registro profissional obrigatório | Tutela da saúde (psicólogo) / Obrigação (Resolução CFP nº 001/2009 — validar) | **Só o psicólogo titular**; paciente mediante liberação | Mínimo de **5 anos** (Resolução CFP — validar); **sem exclusão física** | **Sim (saúde)** |
| Solicitações de acesso ao prontuário | `solicitacao_acesso_prontuario` | Direito de acesso do titular | Exercício de direitos | Paciente e psicólogo | Mesmo prazo do prontuário | Sim |
| Auditoria clínica (ator, ação, **IP**, data) | `auditoria_clinica` (imutável) | Rastreabilidade/segurança | Obrigação / LI | Restrito (operação) | Mesmo prazo do prontuário | Não (metadado) |
| Auditoria administrativa (ator, ação, **IP**, detalhes) | `log_alteracao` (imutável) | Rastreabilidade | LI / Obrigação | Restrito | 5 anos | Não |
| Notificações e preferências | `notifications`, `preferencia_notificacao` | Avisos na plataforma | Contrato | Titular | 1 ano após leitura (sugestão) | Não (mensagens nunca contêm conteúdo clínico) |
| Chamados e mensagens | `chamado`, `chamado_mensagem` | Suporte, exercício de direitos | Contrato / Exercício de direitos | Titular, admins | 5 anos após encerramento | Pode conter (texto livre) |
| Código de redefinição de senha (hash) | `codigo_redefinicao_senha` | Recuperação de acesso | Contrato | Ninguém (hash) | Até expirar/uso (30 min) | Não |
| **Sessões** (hash do refresh token, IP, navegador resumido, datas) | `sessao_usuario` | Manter login, segurança, revogação | Contrato / LI | Titular (futuro: lista de dispositivos) | Expiração + 6 meses (Marco Civil, art. 15 — validar) | Não |
| **Consentimentos** (documento, versão, data do servidor, IP, navegador resumido, origem) | `consentimento_usuario` (imutável) | **Prova do aceite** | Obrigação / Exercício de direitos | Titular (histórico) | Enquanto a conta existir + 5 anos | Não |
| Presença em sessão de vídeo (consulta, usuário, papel, evento, horário) | `log_sessao_video` | Auditoria e detecção de comparecimento (só registro) | Contrato / LI | Restrito (operação) | Mesmo prazo do prontuário (sugestão — validar) | **Sim (saúde: revela atendimento)** |
| Mídia da sessão de vídeo (áudio/vídeo) | **Não armazenada** — trafega navegador ↔ provedor de vídeo | Realizar a sessão | Contrato + Consentimento específico | As duas partes, em tempo real | **Nenhuma (não gravada)** | Sim |
| IP para rate limit | Memória (Bucket4j) | Antiabuso | LI | Ninguém (memória volátil) | Minutos | Não |
| E-mails enviados | Provedor SMTP | Avisos transacionais (nunca conteúdo clínico, texto de chamado ou nome de paciente para psicólogo) | Contrato | Provedor SMTP | Política do provedor | Não |

## 5. Lacunas encontradas (registradas como pendência)

- **Fotos de perfil são públicas** por URL (necessário para `<img>` sem token). Documentado na Política.
- Não existe fluxo automatizado de **eliminação/anonimização de conta**: o pedido é feito pelo chamado
  "Privacidade / LGPD" e tratado manualmente (prontuário tem guarda obrigatória e não pode ser apagado).
- Não há rotina automática de **expurgo** (notificações antigas, sessões expiradas, logs) — pendência.
- `notificationApiClient.ts` (front) lê o token via `document.cookie`, o que não funciona com cookie `HttpOnly`;
  o arquivo **não é importado** em nenhum lugar (código morto) — candidato a remoção.
