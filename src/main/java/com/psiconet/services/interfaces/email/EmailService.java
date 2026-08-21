package com.psiconet.services.interfaces.email;

import com.psiconet.model.entities.clinical.Appointment;

public interface EmailService {
    void sendMailAppointment(Appointment appointment);
    void sendMailConfirmationAppointment(Appointment appointment);
}
