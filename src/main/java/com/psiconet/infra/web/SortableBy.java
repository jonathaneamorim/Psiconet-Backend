package com.psiconet.infra.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Campos aceitos em {@code ?sort=} para o {@code Pageable} anotado (SEC-08). Sem a anotação, o
 * endpoint não aceita ordenação do cliente (vale só a de {@code @PageableDefault}). Ordenar por campo
 * livre permitia erro 500 e inferir dados de relacionamentos (ex.: ordenar por CPF do paciente).
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SortableBy {
    String[] value();
}
