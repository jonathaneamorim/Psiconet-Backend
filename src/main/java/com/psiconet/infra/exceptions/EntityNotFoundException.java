package com.psiconet.infra.exceptions;

public class EntityNotFoundException extends RuntimeException {

  public EntityNotFoundException(String message) {
    super(message);
  }

  public EntityNotFoundException(Class<?> entity, Object value) {
    super(entity.getSimpleName() + " não encontrado: " + value);
  }
}