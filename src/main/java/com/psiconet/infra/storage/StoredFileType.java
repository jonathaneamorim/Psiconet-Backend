package com.psiconet.infra.storage;

import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Tipos de arquivo aceitos no upload. A extensão gravada em disco e o Content-Type servido saem
 * DAQUI (do tipo validado pelos magic bytes), nunca do nome do arquivo enviado pelo cliente —
 * senão um PNG chamado "x.html" seria gravado como .html e servido como text/html (SEC-07).
 */
public enum StoredFileType {
    PNG("image/png", ".png", true, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}),
    JPEG("image/jpeg", ".jpg", true, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PDF("application/pdf", ".pdf", false, new byte[]{0x25, 0x50, 0x44, 0x46});

    public static final Set<StoredFileType> IMAGES = Set.of(PNG, JPEG);
    public static final Set<StoredFileType> ANY = Set.of(PNG, JPEG, PDF);

    private final String contentType;
    private final String extension;
    private final boolean image;
    private final byte[] magicBytes;

    StoredFileType(String contentType, String extension, boolean image, byte[] magicBytes) {
        this.contentType = contentType;
        this.extension = extension;
        this.image = image;
        this.magicBytes = magicBytes;
    }

    public String contentType() { return contentType; }
    public String extension() { return extension; }
    public boolean isImage() { return image; }
    byte[] magicBytes() { return magicBytes.clone(); }

    public static Optional<StoredFileType> fromContentType(String contentType) {
        String normalized = FileStorageService.normalizeContentType(contentType);
        return Arrays.stream(values()).filter(t -> t.contentType.equals(normalized)).findFirst();
    }

    /**
     * Content-Type a servir para um arquivo já gravado. Só as extensões que o próprio backend grava
     * (mais ".jpeg" de arquivos antigos); qualquer outra (ex.: ".html" de upload anterior à correção)
     * não tem tipo e nunca é servida como conteúdo renderizável.
     */
    public static Optional<MediaType> mediaTypeOf(String fileName) {
        if (fileName == null) return Optional.empty();
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return Optional.of(MediaType.IMAGE_PNG);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return Optional.of(MediaType.IMAGE_JPEG);
        if (lower.endsWith(".pdf")) return Optional.of(MediaType.APPLICATION_PDF);
        return Optional.empty();
    }
}
