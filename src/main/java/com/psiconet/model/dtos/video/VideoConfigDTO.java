package com.psiconet.model.dtos.video;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Disponibilidade da videochamada integrada (feature flag + provedor configurado). */
@Getter
@AllArgsConstructor
public class VideoConfigDTO {
    private final boolean enabled;
}
