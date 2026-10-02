package com.psiconet.model.dtos;

/** Resposta padrão de sucesso com mensagem (sempre JSON — o frontend faz parse de JSON). */
public record MessageResponseDTO(String message) {}
