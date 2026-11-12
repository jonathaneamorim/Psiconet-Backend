package com.psiconet.infra.storage;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LocalFileStorageServiceImpl implements FileStorageService {

    // Teto de pixels antes de decodificar: um PNG pequeno pode declarar dimensões gigantes
    // ("bomba de descompressão") e esgotar a memória ao ser lido.
    private static final int MAX_IMAGE_DIMENSION = 6000;

    @Value("${psiconet.storage.upload-dir}")
    private String uploadDir;

    @Override
    public String store(MultipartFile file, String subdirectory, Set<StoredFileType> allowedTypes) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("file", "Nenhum arquivo enviado.");
        }

        StoredFileType type = StoredFileType.fromContentType(file.getContentType())
                .filter(allowedTypes::contains)
                .orElseThrow(() -> new BusinessException("file", "Tipo de arquivo não permitido. Envie " + describe(allowedTypes) + "."));

        // O Content-Type do multipart é só metadado e pode ser forjado: o conteúdo precisa confirmar.
        validateMagicBytes(file, type);

        try {
            Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path targetDir = baseDir.resolve(subdirectory).normalize();
            if (!targetDir.startsWith(baseDir)) {
                throw new BusinessException("file", "Destino de arquivo inválido.");
            }
            Files.createDirectories(targetDir);

            // Nome e extensão 100% gerados pelo servidor — o nome original nunca é usado.
            Path targetPath = targetDir.resolve(UUID.randomUUID() + type.extension());

            if (type.isImage()) {
                writeReencodedImage(file, type, targetPath);
            } else {
                file.transferTo(targetPath);
            }

            return subdirectory + "/" + targetPath.getFileName();
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
                throw new EntityNotFoundException("Arquivo não encontrado.");
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

    private void validateMagicBytes(MultipartFile file, StoredFileType type) {
        byte[] expected = type.magicBytes();
        byte[] header = new byte[expected.length];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(header, 0, header.length);
            if (read < expected.length || !Arrays.equals(header, expected)) {
                throw new BusinessException(
                        "file",
                        "O conteúdo do arquivo não corresponde ao tipo declarado (" + type.contentType() + ")."
                );
            }
        } catch (IOException e) {
            throw new BusinessException("file", "Não foi possível ler o arquivo enviado.");
        }
    }

    /**
     * Decodifica e regrava a imagem no mesmo formato: o arquivo final só tem pixels — metadados
     * (EXIF com GPS do celular, comentários) e bytes extras escondidos após a imagem ficam para trás.
     */
    private void writeReencodedImage(MultipartFile file, StoredFileType type, Path targetPath) throws IOException {
        BufferedImage image = readImageWithinLimits(file);
        if (type == StoredFileType.JPEG && image.getColorModel().hasAlpha()) {
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.createGraphics().drawImage(image, 0, 0, java.awt.Color.WHITE, null);
            image = rgb;
        }
        String format = type == StoredFileType.PNG ? "png" : "jpg";
        if (!ImageIO.write(image, format, targetPath.toFile())) {
            throw new BusinessException("file", "Não foi possível processar a imagem enviada.");
        }
    }

    private BufferedImage readImageWithinLimits(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream(); ImageInputStream stream = ImageIO.createImageInputStream(in)) {
            Iterator<ImageReader> readers = stream == null ? null : ImageIO.getImageReaders(stream);
            if (readers == null || !readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                if (reader.getWidth(0) > MAX_IMAGE_DIMENSION || reader.getHeight(0) > MAX_IMAGE_DIMENSION) {
                    throw new BusinessException("file", "Imagem muito grande. Use no máximo " + MAX_IMAGE_DIMENSION + " pixels de largura e altura.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw invalidImage();
                return image;
            } catch (IOException | RuntimeException e) {
                if (e instanceof BusinessException business) throw business;
                throw invalidImage();
            } finally {
                reader.dispose();
            }
        }
    }

    private static BusinessException invalidImage() {
        return new BusinessException("file", "Imagem inválida ou corrompida.");
    }

    private static String describe(Set<StoredFileType> types) {
        return types.stream()
                .sorted()
                .map(t -> t == StoredFileType.JPEG ? "JPG" : t.name())
                .collect(Collectors.joining(", "));
    }
}
