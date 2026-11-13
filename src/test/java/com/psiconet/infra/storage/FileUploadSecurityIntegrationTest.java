package com.psiconet.infra.storage;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** SEC-07: o tipo do arquivo gravado e servido vem do conteúdo validado, nunca do nome enviado. */
class FileUploadSecurityIntegrationTest extends IntegrationTestBase {

    @Autowired private FileStorageService fileStorageService;

    @Value("${psiconet.storage.upload-dir}")
    private String uploadDir;

    @Test
    void fotoComNomeHtmlEGravadaEServidaComoImagem() throws Exception {
        User patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        MockMultipartFile file = new MockMultipartFile("file", "ataque.html", "image/png", png());

        MvcResult upload = mvc.perform(multipart("/patients/me/photo").file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(patient))))
                .andReturn();
        assertEquals(200, upload.getResponse().getStatus());

        String photoUrl = userRepository.findById(patient.getId()).orElseThrow().getPhotoUrl();
        String fileName = photoUrl.substring(photoUrl.lastIndexOf('/') + 1);
        assertTrue(fileName.endsWith(".png"), "extensão deve vir do tipo validado: " + fileName);

        MvcResult served = mvc.perform(get("/public/photos/" + fileName)).andReturn();
        assertEquals("image/png", served.getResponse().getContentType());
        assertEquals("nosniff", served.getResponse().getHeader("X-Content-Type-Options"));
    }

    @Test
    void arquivoLegadoComExtensaoPerigosaNaoESalvoComoHtml() throws Exception {
        Path avatars = Path.of(uploadDir).toAbsolutePath().resolve("avatars");
        Files.createDirectories(avatars);
        Path legacy = avatars.resolve("legado-" + System.nanoTime() + ".html");
        Files.writeString(legacy, "<script>alert(1)</script>", StandardCharsets.UTF_8);

        MvcResult served = mvc.perform(get("/public/photos/" + legacy.getFileName())).andReturn();
        assertEquals(404, served.getResponse().getStatus());
        assertFalse(String.valueOf(served.getResponse().getContentType()).contains("html"));
    }

    @Test
    void comprovanteGravaExtensaoDoTipoDetectado() {
        MockMultipartFile file = new MockMultipartFile("file", "comprovante.html", "application/pdf",
                "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));

        String path = fileStorageService.store(file, "receipts", StoredFileType.ANY);
        assertTrue(path.endsWith(".pdf"), path);

        Resource resource = fileStorageService.loadAsResource(path);
        assertEquals("application/pdf", StoredFileType.mediaTypeOf(resource.getFilename()).orElseThrow().toString());
    }

    @Test
    void tipoDeclaradoDiferenteDoConteudoERecusado() {
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png",
                "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        assertThrows(com.psiconet.infra.exceptions.BusinessException.class,
                () -> fileStorageService.store(file, "avatars", StoredFileType.IMAGES));
    }

    @Test
    void pdfNaoEAceitoOndeSoImagemEPermitida() {
        MockMultipartFile file = new MockMultipartFile("file", "foto.pdf", "application/pdf",
                "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        assertThrows(com.psiconet.infra.exceptions.BusinessException.class,
                () -> fileStorageService.store(file, "avatars", StoredFileType.IMAGES));
    }

    @Test
    void imagemComCabecalhoValidoMasConteudoCorrompidoERecusada() {
        byte[] fake = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", fake);
        assertThrows(com.psiconet.infra.exceptions.BusinessException.class,
                () -> fileStorageService.store(file, "avatars", StoredFileType.IMAGES));
    }

    @Test
    void jpegEReencodadoSemMetadadosExif() throws Exception {
        byte[] withExif = injectExif(jpeg());
        assertTrue(new String(withExif, StandardCharsets.ISO_8859_1).contains("Exif"));

        MockMultipartFile file = new MockMultipartFile("file", "foto.jpg", "image/jpeg", withExif);
        String path = fileStorageService.store(file, "avatars", Set.of(StoredFileType.JPEG));

        byte[] stored = Files.readAllBytes(Path.of(uploadDir).toAbsolutePath().resolve(path));
        assertFalse(new String(stored, StandardCharsets.ISO_8859_1).contains("Exif"), "EXIF/GPS deve ser removido");
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(stored)));
    }

    private static byte[] png() throws Exception {
        return encode("png");
    }

    private static byte[] jpeg() throws Exception {
        return encode("jpg");
    }

    private static byte[] encode(String format) throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    /** Insere um segmento APP1 "Exif" logo após o SOI (FFD8), como fazem câmeras e celulares. */
    private static byte[] injectExif(byte[] jpeg) {
        byte[] payload = "Exif\0\0GPS-LATITUDE-FALSA".getBytes(StandardCharsets.ISO_8859_1);
        int length = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.write(payload, 0, payload.length);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }
}
