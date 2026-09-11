package com.psiconet.infra.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${psiconet.mail.from}")
    private String fromAddress;

    @Value("${psiconet.frontend-url}")
    private String frontendUrl;

    // Assíncrono: quem chama (ex.: POST /auth/forgot-password) não espera o SMTP responder,
    // então uma falha de envio aqui não tem como voltar como erro HTTP — só logamos.
    @Override
    @Async("mailTaskExecutor")
    public void sendPasswordResetLink(String toEmail, String fullName, String token, int expirationMinutes) {
        try {
            // Só o token vai na URL: diferente do fluxo antigo, o e-mail do usuário não precisa
            // aparecer no link (evita vazar esse dado em histórico de navegador/logs de acesso do
            // front), já que o próprio token já identifica o pedido de redefinição de forma única.
            String resetLink = frontendUrl + "/reset-password?token="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress, "Psiconet");
            helper.setTo(toEmail);
            helper.setSubject("Redefinição de senha — Psiconet");
            helper.setText(buildHtml(fullName, resetLink, expirationMinutes), true);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
            log.error(
                    "Falha ao enviar e-mail de redefinição de senha para {}. " +
                            "Verifique as credenciais SMTP (MAIL_USERNAME/MAIL_PASSWORD).",
                    toEmail, e
            );
        }
    }

    private String buildHtml(String fullName, String resetLink, int expirationMinutes) {
        return """
            <!doctype html>
            <html>
              <body style="margin:0;padding:0;background-color:#f1f5f9;font-family:Arial,Helvetica,sans-serif;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f1f5f9;padding:32px 0;">
                  <tr>
                    <td align="center">
                      <table width="480" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.1);">
                        <tr>
                          <td style="background-color:#2563eb;padding:24px 32px;">
                            <span style="color:#ffffff;font-size:20px;font-weight:bold;">Psiconet</span>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px;">
                            <p style="font-size:15px;color:#334155;margin:0 0 16px;">Olá, %s!</p>
                            <p style="font-size:15px;color:#334155;margin:0 0 24px;">
                              Recebemos uma solicitação para redefinir a senha da sua conta na Psiconet. Clique no botão abaixo para escolher uma nova senha:
                            </p>
                            <div style="text-align:center;margin:0 0 24px;">
                              <a href="%s" style="display:inline-block;background-color:#2563eb;color:#ffffff;font-size:15px;font-weight:bold;padding:14px 32px;border-radius:10px;text-decoration:none;">Redefinir Senha</a>
                            </div>
                            <p style="font-size:12px;color:#94a3b8;margin:0 0 24px;word-break:break-all;">
                              Se o botão não funcionar, copie e cole este link no navegador:<br>
                              <a href="%s" style="color:#2563eb;">%s</a>
                            </p>
                            <p style="font-size:13px;color:#64748b;margin:0 0 8px;">
                              Este link expira em <strong>%d minutos</strong>.
                            </p>
                            <p style="font-size:13px;color:#64748b;margin:0;">
                              Se você não solicitou essa alteração, pode ignorar este e-mail com segurança — sua senha continuará a mesma.
                            </p>
                          </td>
                        </tr>
                        <tr>
                          <td style="background-color:#f8fafc;padding:16px 32px;border-top:1px solid #e2e8f0;">
                            <p style="font-size:11px;color:#94a3b8;margin:0;">© Psiconet — Cuidando da sua saúde mental.</p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
              </body>
            </html>
            """.formatted(escapeHtml(fullName), resetLink, resetLink, resetLink, expirationMinutes);
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
