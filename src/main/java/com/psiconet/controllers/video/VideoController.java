package com.psiconet.controllers.video;

import com.psiconet.infra.ratelimit.RateLimited;
import com.psiconet.model.dtos.video.VideoConfigDTO;
import com.psiconet.services.interfaces.video.VideoSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class VideoController {

    private final VideoSessionService videoSessionService;

    // Se a "Sala Psiconet" está disponível (flag + provedor configurado). Autenticado.
    @GetMapping("/video/config")
    public ResponseEntity<VideoConfigDTO> getConfig() {
        return ResponseEntity.ok(videoSessionService.getConfig());
    }

    // Webhook do provedor de vídeo: público, aceito SOMENTE com assinatura válida (401 caso contrário).
    @PostMapping("/webhooks/video")
    @RateLimited("video-webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody byte[] body,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        videoSessionService.handleWebhook(body, authorization);
        return ResponseEntity.ok().build();
    }
}
