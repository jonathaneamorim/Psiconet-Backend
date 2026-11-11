package com.psiconet.model.dtos.legal;

import java.time.LocalDate;

/** Versões vigentes — contrato único consumido pelo frontend (cadastro, banner de cookies, reaceite). */
public record LegalVersionsDTO(VersionInfo terms, VersionInfo privacy, VersionInfo cookies) {
    public record VersionInfo(String version, LocalDate effectiveDate) {}
}
