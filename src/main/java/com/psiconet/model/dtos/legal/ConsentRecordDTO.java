package com.psiconet.model.dtos.legal;

import java.time.Instant;

/** Item do histórico de aceites exibido ao titular (sem IP/navegador). */
public record ConsentRecordDTO(String type, String version, Instant acceptedAt, String source) {}
