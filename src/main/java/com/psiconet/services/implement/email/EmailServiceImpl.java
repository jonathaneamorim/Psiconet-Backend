package com.psiconet.services.implement.email;

import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.services.interfaces.email.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {
    @Autowired
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String sender;

    @Async
    public void sendMailAppointment(Appointment appointment) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(sender);
            mailMessage.setTo(appointment.getTreatmentLink().getPatient().getUser().getEmail());
            mailMessage.setSubject(appointment.getTitle());
            mailMessage.setText(appointment.getDescription());
            javaMailSender.send(mailMessage);
        } catch (Exception e) {
            log.error("Erro ao enviar e-mail de encontro!", e);
        }
    }

    @Async
    public void sendMailConfirmationAppointment(Appointment appointment) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(sender);
            mailMessage.setTo(appointment.getTreatmentLink().getPsychologist().getUser().getEmail());
            mailMessage.setSubject(appointment.getTitle());

            mailMessage.setText("O usuário "
                    + appointment.getTreatmentLink().getPatient().getUser().getFullName()
                    + " aceitou o convite para o encontro "
                    + appointment.getStartDateTime().getDayOfMonth()
                    + " de "
                    + appointment.getStartDateTime().getMonthValue()
                    + " de "
                    + appointment.getStartDateTime().getYear());
            javaMailSender.send(mailMessage);
        } catch (Exception e) {
            log.error("Erro ao enviar e-mail de encontro!", e);
        }
    }
}