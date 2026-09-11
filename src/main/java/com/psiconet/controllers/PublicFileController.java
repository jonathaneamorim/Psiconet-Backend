package com.psiconet.controllers;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Fotos de perfil precisam ser exibidas em <img> por qualquer visitante (inclusive em cartões de
// outros usuários), então esse endpoint fica fora da autenticação (ver SecurityConfig: "/public/**").
// Só serve o subdiretório "avatars" — nunca comprovantes de pagamento ou outros arquivos privados.
@RestController
@RequestMapping("/public/photos")
@RequiredArgsConstructor
public class PublicFileController {

    private final FileStorageService fileStorageService;

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getPhoto(@PathVariable String filename) {
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new BusinessException("file", "Nome de arquivo inválido.");
        }

        Resource resource = fileStorageService.loadAsResource("avatars/" + filename);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }
}
