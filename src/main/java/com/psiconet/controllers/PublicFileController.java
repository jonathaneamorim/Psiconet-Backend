package com.psiconet.controllers;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.infra.storage.FileStorageService;
import com.psiconet.infra.storage.StoredFileType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

        // Só imagens: qualquer outro arquivo em avatars/ (ex.: ".html" gravado antes da correção do
        // SEC-07) não é servido — o tipo vem da extensão gerada pelo backend, nunca de adivinhação.
        MediaType mediaType = StoredFileType.mediaTypeOf(filename)
                .filter(type -> type.getType().equals("image"))
                .orElseThrow(() -> new EntityNotFoundException("Arquivo não encontrado."));
        Resource resource = fileStorageService.loadAsResource("avatars/" + filename);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }
}
