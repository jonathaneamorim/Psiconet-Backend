---
tipo: PRIVACY
titulo: Política de Privacidade
versao: 1.1.0
vigencia: 2026-10-23
---

> **Minuta técnica — não é aconselhamento jurídico.** Trechos com `[A PREENCHER]` e "validar com
> jurídico" precisam ser revisados antes da publicação. Esta política descreve o que a plataforma
> faz hoje, com base no inventário técnico de dados.

## Resumo em linguagem simples

- Coletamos o necessário para você usar a Psiconet: dados de cadastro, perfil, agenda, cobranças, mensagens de suporte e, no caso do atendimento, o **prontuário mantido pelo seu psicólogo**.
- Alguns dados são **sensíveis (saúde)**: o simples fato de você estar em acompanhamento psicológico, sua agenda de sessões e o prontuário. Eles recebem proteção reforçada; o prontuário é **cifrado** e só o psicólogo titular o lê.
- **Não vendemos dados**, não usamos publicidade e **não há ferramentas de rastreamento** (analytics ou pixels).
- As sessões on-line na **Sala Psiconet** acontecem por um provedor de vídeo contratado e **não são gravadas** pela Psiconet.
- Você pode pedir acesso, correção, portabilidade, eliminação e mais. O canal é o chamado **"Privacidade / LGPD"** ou o e-mail do encarregado.

## 1. Controlador e encarregado

- **Controlador:** [A PREENCHER: razão social], CNPJ [A PREENCHER: CNPJ], [A PREENCHER: endereço completo].
- **Encarregado (DPO):** [A PREENCHER: nome do encarregado/DPO] — [A PREENCHER: e-mail do encarregado/DPO].

**Prontuário:** quanto ao conteúdo do prontuário e das anotações de sessão, o **psicólogo** decide o que registrar, no exercício da profissão; a Psiconet fornece a ferramenta, guarda os dados cifrados e controla o acesso. A divisão de papéis (controlador/operador) para esses dados deve ser **validada com jurídico**.

## 2. Dados que tratamos

| Categoria | Exemplos | Origem |
|---|---|---|
| Cadastro | Nome, e-mail, CPF, data de nascimento, senha (guardada só como *hash*) | Você |
| Perfil | Telefone, endereço/cidade, foto; para psicólogos: CRP, especialidades, bio, experiência, endereço do consultório, chave Pix | Você |
| Relação terapêutica | Pedidos de conexão, vínculo de tratamento, consultas, recorrências, links de reunião, endereços de atendimento | Você e o outro usuário |
| Sessão por vídeo (Sala Psiconet) | Registro de presença na sala (quem entrou e saiu, e quando) e de emissão de acesso. **Áudio e vídeo não são gravados nem armazenados** pela Psiconet | Gerados pelo uso |
| Financeiro | Valores, status de pagamento, **comprovantes enviados** (arquivo), contestações | Você e o outro usuário |
| Clínico | Prontuário, anotações de sessão e adendos (cifrados), pedidos de acesso ao prontuário | Psicólogo / paciente |
| Suporte | Chamados e mensagens | Você |
| Técnicos e de segurança | Endereço IP, navegador e sistema (resumidos), datas de login, sessões ativas, registros de auditoria, registros de aceite destes documentos | Gerados pelo uso |
| Comunicação | Notificações na plataforma e e-mails transacionais | Gerados pelo uso |

**Não coletamos** dados de geolocalização do aparelho, contatos, biometria ou dados de redes sociais.

## 3. Para que usamos e com qual base legal

*(Bases legais sugeridas — validar com jurídico.)*

| Finalidade | Base legal (LGPD) |
|---|---|
| Criar e manter a conta, login, recuperação de senha | Execução de contrato (art. 7º, V) |
| Conectar paciente e psicólogo, agenda, cobranças, notificações | Execução de contrato (art. 7º, V) |
| Realizar a sessão por vídeo na Sala Psiconet (transmissão em tempo real) e registrar a presença | Execução de contrato (art. 7º, V) e, por revelar acompanhamento, o consentimento específico do paciente (art. 11, I) *(validar com jurídico)* |
| Tratar o fato de você estar em acompanhamento (vínculo, agenda) — **dado sensível** | **Consentimento específico e destacado** (art. 11, I), colhido no cadastro do paciente |
| Prontuário e anotações | Tutela da saúde, em procedimento realizado por profissional de saúde (art. 11, II, "f"), e cumprimento de obrigação regulatória do psicólogo (art. 11, II, "a") |
| Segurança, prevenção a fraudes e abuso, auditoria | Legítimo interesse (art. 7º, IX) e garantia da prevenção à fraude (art. 11, II, "g") |
| Guardar registros de acesso e prova de aceite | Cumprimento de obrigação legal (art. 7º, II) e exercício regular de direitos (art. 7º, VI) |
| Responder pedidos de suporte e de titulares | Execução de contrato / cumprimento de obrigação legal |

Não usamos seus dados para publicidade, perfilamento comercial ou decisões automatizadas com efeitos jurídicos.

## 4. Dados sensíveis de saúde

- O **prontuário** e as anotações são cifrados campo a campo (AES-256-GCM, chave própria por prontuário, chave-mestra guardada fora do banco).
- Só o **psicólogo titular do vínculo** lê e edita o prontuário. O paciente pode **solicitar acesso**; o psicólogo libera (somente leitura, com prazo e PDF opcionais) ou nega com justificativa.
- **Administradores nunca têm acesso ao conteúdo clínico.**
- Registros finalizados não podem ser alterados nem apagados; correções são feitas por adendo. Todo acesso fica em auditoria encadeada e imutável.
- E-mails e notificações **nunca** contêm conteúdo clínico.
- **Sessões por vídeo:** a Psiconet **não grava** áudio nem vídeo, não transcreve e não armazena a mídia. A transmissão vai cifrada do seu navegador ao provedor de vídeo e dele para a outra pessoa. A sala é exclusiva da consulta (no máximo 2 participantes) e só abre perto do horário marcado. O provedor recebe apenas um identificador da sala e um identificador interno do usuário — **nunca nome, e-mail ou conteúdo clínico**.
- **Gravar a sessão sem o consentimento da outra pessoa é proibido** (ver Termos de Uso).

## 5. Compartilhamento

| Com quem | O quê | Por quê |
|---|---|---|
| Psicólogo ↔ paciente conectados | Nome, foto, contato e dados da agenda/cobrança do vínculo; prontuário apenas conforme a seção 4 | Prestação do atendimento |
| Público em geral | Perfil público do psicólogo (nome, foto, CRP, especialidades, bio, cidade) e **fotos de perfil** (servidas por endereço público) | Busca de profissionais |
| Provedor de infraestrutura | Todos os dados armazenados | Hospedagem: [A PREENCHER: provedor e região] |
| Provedor de e-mail | E-mail, nome e o aviso enviado | Envio de e-mails transacionais: [A PREENCHER: provedor SMTP] |
| Provedor de vídeo (operador) | Áudio e vídeo **em trânsito** durante a sessão, endereço IP e dados técnicos de conexão, identificadores opacos da sala e do usuário | Transmitir a sessão na Sala Psiconet: [A PREENCHER: LiveKit Cloud (LiveKit, Inc.) ou servidor próprio, e região] |
| Autoridades | O que for exigido | Ordem judicial ou obrigação legal |

Não vendemos nem alugamos dados pessoais.

## 6. Transferência internacional

Se o provedor de hospedagem, de e-mail ou de **vídeo** mantiver servidores fora do Brasil (por exemplo, Google Workspace ou a nuvem do provedor de vídeo), haverá transferência internacional, feita com as garantias previstas na LGPD (arts. 33 a 36) — **[A PREENCHER: provedores, países e mecanismo adotado; validar com jurídico]**.

## 7. Por quanto tempo guardamos

*(Prazos sugeridos — validar com jurídico.)*

| Dado | Prazo |
|---|---|
| Conta e perfil | Enquanto a conta existir; após o encerramento, até 5 anos para defesa de direitos, depois eliminação ou anonimização |
| **Prontuário, anotações, adendos e auditoria clínica** | No mínimo o prazo exigido pelas normas do Conselho Federal de Psicologia (referência: 5 anos — validar); **não são apagados antes disso, nem a pedido** |
| Registros de acesso (IP, data e hora) | 6 meses no mínimo (Marco Civil da Internet, art. 15 — validar) |
| Prova de aceite dos documentos | Enquanto a conta existir + 5 anos |
| Pagamentos e comprovantes | 5 anos |
| Chamados | 5 anos após o encerramento |
| Sessões de login | Até a expiração; depois apenas como registro de acesso |
| Notificações | Enquanto a conta existir *(rotina de limpeza: pendente)* |
| Registro de presença nas sessões por vídeo | Mesmo prazo do prontuário (é registro de atendimento — validar) |
| Áudio e vídeo das sessões | **Não são gravados nem guardados** |

## 8. Segurança

Senhas com *hash* BCrypt; conexões cifradas (HTTPS) em produção; cookies de sessão `HttpOnly`; sessão encerrada imediatamente ao sair, trocar a senha ou ter a conta inativada; renovação de sessão com detecção de reutilização de credencial; limites de tentativas (*rate limit*); controle de acesso por perfil e por dono do recurso; dados clínicos cifrados; auditoria imutável; registros de erro sem senhas, tokens, CPF completo ou conteúdo clínico.

Nenhum sistema é 100% seguro. Em caso de incidente de segurança que possa trazer risco ou dano relevante, comunicaremos a ANPD e os titulares afetados nos prazos da regulamentação vigente (LGPD, art. 48 — validar prazos).

## 9. Seus direitos (LGPD, art. 18)

Você pode, a qualquer momento e de forma gratuita:

1. **Confirmar** se tratamos seus dados e **acessá-los**;
2. **Corrigir** dados incompletos, inexatos ou desatualizados (boa parte direto em "Configurações");
3. Pedir **anonimização, bloqueio ou eliminação** de dados desnecessários, excessivos ou tratados em desconformidade;
4. Pedir a **portabilidade** dos dados;
5. Pedir a **eliminação** dos dados tratados com base no consentimento (respeitados os prazos legais de guarda, como o do prontuário);
6. Saber com **quem compartilhamos** seus dados;
7. Saber que pode **não consentir** e quais as consequências;
8. **Revogar o consentimento** (isso pode impedir o uso de funções que dependem dele, como o vínculo de tratamento);
9. Pedir **revisão** de decisões automatizadas (não usamos decisões automatizadas hoje);
10. Peticionar à **ANPD**.

**Como exercer:** abra um chamado na categoria **"Privacidade / LGPD"** (menu Suporte) ou escreva para **[A PREENCHER: e-mail do encarregado/DPO]**. Podemos pedir confirmação de identidade. Responderemos no prazo legal *(validar: 15 dias para a declaração completa de acesso, art. 19, II)*. O **acesso ao prontuário** é pedido diretamente na área "Prontuário" e decidido pelo psicólogo.

Em "Configurações → Privacidade e consentimentos" você vê o histórico dos documentos que aceitou e pode alterar suas preferências de cookies.

## 10. Crianças e adolescentes

O cadastro é permitido apenas a maiores de 18 anos. Se identificarmos conta de menor de idade, ela será inativada e os dados tratados conforme a LGPD (art. 14). *(Validar com jurídico.)*

## 11. Cookies

Usamos **apenas cookies estritamente necessários** (sessão e registro da sua escolha de cookies). Detalhes, prazos e como gerenciar estão na [Política de Cookies](/cookies).

## 12. Alterações

Novas versões terão número de versão e data de vigência, serão comunicadas por notificação e e-mail e exigirão novo aceite no próximo acesso.

## Histórico de versões

| Versão | Vigência | Alterações |
|---|---|---|
| 1.0.0 | 2026-10-02 | Primeira versão. |
| 1.1.0 | 2026-10-23 | Sala Psiconet (vídeo integrado): provedor de vídeo como operador, ausência de gravação, registro de presença e possível transferência internacional. |
