package com.psiconet.mapper;

import com.psiconet.model.dtos.financial.PaymentDTO;
import com.psiconet.model.entities.financial.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "appointmentId", expression = "java(payment.getAppointment() != null ? payment.getAppointment().getId() : null)")
    PaymentDTO toDto(Payment payment);
}
