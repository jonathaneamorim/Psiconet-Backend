package com.psiconet.model.dtos.clinical.record;

/** PDF gerado do prontuário + hash SHA-256 do arquivo (registrado na auditoria e enviado em X-Document-SHA256). */
public record ExportedPdfDTO(byte[] content, String sha256, String fileName) {}
