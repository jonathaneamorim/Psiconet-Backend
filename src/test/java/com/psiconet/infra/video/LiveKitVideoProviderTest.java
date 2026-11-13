package com.psiconet.infra.video;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import tools.jackson.databind.ObjectMapper;
import com.psiconet.infra.video.livekit.LiveKitVideoProvider;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Contrato com o LiveKit: claims do token e verificação de assinatura do webhook. */
class LiveKitVideoProviderTest {

    private static final String KEY = "APIchave";
    private static final String SECRET = "segredo-livekit-de-teste-com-32-caracteres!";

    private final LiveKitVideoProvider provider = new LiveKitVideoProvider(properties(), new ObjectMapper());

    private static VideoProperties properties() {
        VideoProperties p = new VideoProperties();
        p.setEnabled(true);
        p.setUrl("wss://video.example.com");
        p.setApiKey(KEY);
        p.setApiSecret(SECRET);
        p.setTokenTtl(Duration.ofMinutes(10));
        return p;
    }

    @Test
    void tokenTemPermissoesMinimasSalaDeDoisEValidadeCurta() {
        VideoProvider.IssuedToken issued = provider.issueToken("psn_abc", "user-1", 2);
        DecodedJWT jwt = JWT.require(Algorithm.HMAC256(SECRET)).withIssuer(KEY).build().verify(issued.token());

        assertEquals("user-1", jwt.getSubject());
        Map<String, Object> video = jwt.getClaim("video").asMap();
        assertEquals(true, video.get("roomJoin"));
        assertEquals("psn_abc", video.get("room"));
        assertEquals(true, video.get("canPublish"));
        assertEquals(true, video.get("canSubscribe"));
        assertEquals(false, video.get("canPublishData"));
        assertEquals(List.of("camera", "microphone"), video.get("canPublishSources"));
        for (String forbidden : List.of("roomRecord", "roomAdmin", "roomCreate", "roomList", "ingressAdmin", "hidden")) {
            assertFalse(video.containsKey(forbidden), forbidden);
        }
        assertEquals(2, ((Number) jwt.getClaim("roomConfig").asMap().get("maxParticipants")).intValue());
        // Nada de nome, e-mail ou metadados enviados ao provedor
        assertTrue(jwt.getClaim("name").isMissing());
        assertTrue(jwt.getClaim("metadata").isMissing());

        long ttl = jwt.getExpiresAt().toInstant().getEpochSecond() - jwt.getNotBefore().toInstant().getEpochSecond();
        assertEquals(600, ttl);
    }

    private static String signedWebhook(byte[] body, String secret, Instant expiresAt) throws Exception {
        String hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(body));
        return JWT.create().withIssuer(KEY).withExpiresAt(Date.from(expiresAt))
                .withClaim("sha256", hash).sign(Algorithm.HMAC256(secret));
    }

    private static byte[] body(String event) {
        return ("{\"event\":\"" + event + "\",\"id\":\"EV_1\",\"createdAt\":\"1760000000\","
                + "\"room\":{\"name\":\"psn_abc\"},\"participant\":{\"identity\":\"user-1\"}}").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void webhookAssinadoEInterpretado() throws Exception {
        byte[] body = body("participant_joined");
        VideoWebhookEvent event = provider.parseWebhook(body, signedWebhook(body, SECRET, Instant.now().plusSeconds(300)))
                .orElseThrow();
        assertEquals(VideoWebhookEvent.Type.PARTICIPANT_JOINED, event.type());
        assertEquals("psn_abc", event.roomName());
        assertEquals("user-1", event.participantIdentity());
        assertEquals("EV_1", event.eventId());
        assertEquals(Instant.ofEpochSecond(1760000000L), event.occurredAt());
    }

    @Test
    void eventoSemInteresseEIgnorado() throws Exception {
        byte[] body = body("track_published");
        assertTrue(provider.parseWebhook(body, signedWebhook(body, SECRET, Instant.now().plusSeconds(300))).isEmpty());
    }

    @Test
    void webhookComCorpoAlteradoSegredoErradoVencidoOuSemAssinaturaERecusado() throws Exception {
        byte[] body = body("participant_joined");
        String valid = signedWebhook(body, SECRET, Instant.now().plusSeconds(300));

        assertThrows(InvalidWebhookSignatureException.class,
                () -> provider.parseWebhook(body("participant_left"), valid));
        assertThrows(InvalidWebhookSignatureException.class,
                () -> provider.parseWebhook(body, signedWebhook(body, "outro-segredo-com-mais-de-32-caracteres!!", Instant.now().plusSeconds(300))));
        assertThrows(InvalidWebhookSignatureException.class,
                () -> provider.parseWebhook(body, signedWebhook(body, SECRET, Instant.now().minusSeconds(3600))));
        assertThrows(InvalidWebhookSignatureException.class, () -> provider.parseWebhook(body, null));
        assertThrows(InvalidWebhookSignatureException.class, () -> provider.parseWebhook(body, "lixo"));
    }
}
