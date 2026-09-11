package com.psiconet.infra.storage;

import com.psiconet.infra.exceptions.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LocalFileStorageServiceImpl implements FileStorageService {

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/png", "image/jpeg", "application/pdf");

    // Assinatura ("magic bytes") real de cada tipo permitido — o cabeçalho Content-Type enviado
    // pelo cliente é só metadado da requisição multipart e pode ser forjado livremente, então não é
    // suficiente para decidir o que vamos gravar em disco e depois servir de volta.
    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
            "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},
            "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46}
    );

    @Value("${psiconet.storage.upload-dir}")
    private String uploadDir;

    @Override
    public String store(MultipartFile file, String subdirectory) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("file", "Nenhum arquivo enviado.");
        }

        String contentType = FileStorageService.normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException("file", "Tipo de arquivo não permitido. Envie PNG, JPG ou PDF.");
        }

        validateMagicBytes(file, contentType);

        try {
            Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path targetDir = baseDir.resolve(subdirectory).normalize();
            Files.createDirectories(targetDir);

            String fileName = UUID.randomUUID() + sanitizedExtension(file.getOriginalFilename());

            Path targetPath = targetDir.resolve(fileName).normalize();

            // O nome do arquivo é sempre um UUID gerado pelo servidor, mas a extensão vem do nome
            // original enviado pelo cliente — essa checagem garante que nada (extensão maliciosa,
            // etc.) faça o caminho final escapar do diretório de destino.
            if (!targetPath.startsWith(targetDir)) {
                throw new BusinessException("file", "Nome de arquivo inválido.");
            }

            file.transferTo(targetPath);

            return subdirectory + "/" + fileName;
        } catch (IOException e) {
            throw new BusinessException("file", "Falha ao salvar o arquivo.");
        }
    }

    @Override
    public Resource loadAsResource(String relativePath) {
        try {
            Path filePath = resolveWithinBaseDir(relativePath);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessException("file", "Arquivo não encontrado.");
            }

            return resource;
        } catch (MalformedURLException e) {
            throw new BusinessException("file", "Caminho de arquivo inválido.");
        }
    }

    @Override
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }

        try {
            Path filePath = resolveWithinBaseDir(relativePath);
            Files.deleteIfExists(filePath);
        } catch (IOException | BusinessException e) {
            // Best-effort: uma falha ao remover o arquivo antigo não pode quebrar o fluxo principal
            // (ex.: troca de foto de perfil) — o arquivo órfão fica só como lixo em disco, não como erro.
        }
    }

    private Path resolveWithinBaseDir(String relativePath) {
        Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path filePath = baseDir.resolve(relativePath).normalize();

        // Impede path traversal (ex.: "../../etc/passwd") escapar do diretório de upload — relevante
        // sobretudo para o endpoint público de fotos, onde o nome do arquivo vem direto da URL.
        if (!filePath.startsWith(baseDir)) {
            throw new BusinessException("file", "Caminho de arquivo inválido.");
        }

        return filePath;
    }

    private void validateMagicBytes(MultipartFile file, String contentType) {
        byte[] expected = MAGIC_BYTES.get(contentType);
        if (expected == null) {
            return;
        }

        byte[] header = new byte[expected.length];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(header, 0, header.length);
            if (read < expected.length || !Arrays.equals(header, expected)) {
                throw new BusinessException(
                        "file",
                        "O conteúdo do arquivo não corresponde ao tipo declarado (" + contentType + ")."
                );
            }
        } catch (IOException e) {
            throw new BusinessException("file", "Não foi possível ler o arquivo enviado.");
        }
    }

    private String sanitizedExtension(String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);

        // Só aceita extensões curtas e alfanuméricas — descarta qualquer coisa com "/", "\", ".."
        // ou caracteres fora do comum, que poderiam ser usados para tentar escapar do diretório de
        // destino via um nome de arquivo original malicioso.
        if (extension == null || !extension.matches("[a-zA-Z0-9]{1,10}")) {
            return "";
        }

        return "." + extension;
    }
}
