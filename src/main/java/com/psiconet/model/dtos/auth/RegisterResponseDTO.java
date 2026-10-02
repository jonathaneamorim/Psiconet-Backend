package com.psiconet.model.dtos.auth;

/** Resposta do cadastro público. {@code nextStep} orienta o frontend (hoje sempre "LOGIN"). */
public record RegisterResponseDTO(String message, String nextStep) {}
