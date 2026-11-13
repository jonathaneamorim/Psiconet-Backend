package com.psiconet.infra.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

public interface FileStorageService {
    /**
     * Grava o arquivo com nome gerado (UUID) e a extensão do tipo validado. Imagens são decodificadas
     * e regravadas (remove EXIF/GPS e qualquer carga escondida). Devolve "subdiretorio/arquivo".
     */
    String store(MultipartFile file, String subdirectory, Set<StoredFileType> allowedTypes);
    Resource loadAsResource(String relativePath);

    // Remove um arquivo previamente armazenado (best-effort). Usado para não deixar arquivos órfãos
    // se acumulando indefinidamente no disco a cada novo upload que substitui um anterior (ex.: troca
    // de foto de perfil) — sem isso, o armazenamento cresce sem limite a cada re-upload.
    void delete(String relativePath);

    // "image/jpg" não é um MIME type oficial, mas é o que navegadores/SOs e libs de upload enviam com
    // frequência para arquivos .jpg — sem essa normalização, um JPEG genuíno é rejeitado só por causa
    // da string declarada no Content-Type multipart, que o cliente é livre para escrever como quiser.
    static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }

        String normalized = contentType.trim().toLowerCase();
        if ("image/jpg".equals(normalized) || "image/pjpeg".equals(normalized)) {
            return "image/jpeg";
        }

        return normalized;
    }
}
