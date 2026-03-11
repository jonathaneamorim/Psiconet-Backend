package com.psiconet.repositories.financial;

import com.psiconet.model.entities.financial.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {}