package com.psiconet.infra.video.livekit;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psiconet.infra.video.InvalidWebhookSignatureException;
import com.psiconet.infra.video.VideoAccessException;
import com.psiconet.infra.video.VideoProperties;
import com.psiconet.infra.video.VideoProvider;
import com.psiconet.infra.video.VideoWebhookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptador LiveKit (Cloud ou auto-hospedado) sem o SDK Java oficial: o SDK traz Kotlin/OkHttp 5/
 * Jackson/Protobuf em versões que conflitam com o BOM do Spring Boot 3.3 (ADR 016). O protocolo
 * usado é pequeno e documentado:
 * <ul>
 *   <li>token de acesso = JWT HS256 assinado com o segredo da API ({@code iss} = chave, {@code sub} = identidade,
 *       grant {@code video} e {@code roomConfig});</li>
 *   <li>API de salas = Twirp/JSON em {@code /twirp/livekit.RoomService/*} com um JWT de servidor;</li>
 *   <li>webhook = cabeçalho {@code Authorization} com JWT cujo claim {@code sha256} é o SHA-256 (base64) do corpo.</li>
 * </ul>
 * Nunca loga tokens nem o segredo.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "psiconet.video.provider", havingValue = "livekit", matchIfMissing = true)
public class LiveKitVideoProvider implements VideoProvider {

    private static final String ROOM_SERVICE = "/twirp/livekit.RoomService/";
    private static final Duration SERVER_TOKEN_TTL = Duration.ofMinutes(1);
    // Tolerância de relógio entre o provedor e a API na validação do webhook.
    private static final long WEBHOOK_LEEWAY_SECONDS = 60;

    private final VideoProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public LiveKitVideoProvider(VideoProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String name() {
        return "livekit";
    }

    @Override
    public void ensureRoom(String roomName, int maxParticipants) {
        // CreateRoom é idempotente no LiveKit: sala existente é devolvida sem alteração.
        callRoomService("CreateRoom", Map.of(
                "name", roomName,
                "max_participants", maxParticipants,
                "empty_timeout", properties.getEmptyTimeout().toSeconds(),
                "departure_timeout", properties.getDepartureTimeout().toSeconds()
        ));
    }

    @Override
    public IssuedToken issueToken(String roomName, String identity, int maxParticipants) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getTokenTtl());

        Map<String, Object> video = Map.of(
                "roomJoin", true,
                "room", roomName,
                "canPublish", true,
                "canSubscribe", true,
                // Sem canal de dados (não há chat) nem compartilhamento de tela nesta entrega (ADR 016).
                "canPublishData", false,
                "canPublishSources", List.of("camera", "microphone"),
                "canUpdateOwnMetadata", false
        );
        // Aplicada só se a sala ainda não existir — reforço do limite caso ensureRoom não tenha rodado.
        Map<String, Object> roomConfig = Map.of(
                "name", roomName,
                "maxParticipants", maxParticipants,
                "emptyTimeout", properties.getEmptyTimeout().toSeconds(),
                "departureTimeout", properties.getDepartureTimeout().toSeconds()
        );

        // Sem nome, e-mail ou metadados: o provedor recebe só um id opaco de usuário e o nome opaco da sala.
        String token = JWT.create()
                .withIssuer(properties.getApiKey())
                .withSubject(identity)
                .withNotBefore(Date.from(now))
                .withExpiresAt(Date.from(expiresAt))
                .withClaim("video", video)
                .withClaim("roomConfig", roomConfig)
                .sign(algorithm());

        return new IssuedToken(token, expiresAt);
    }

    @Override
    public void closeRoom(String roomName) {
        try {
            callRoomService("DeleteRoom", Map.of("room", roomName));
        } catch (VideoAccessException ex) {
            // Sala já descartada pelo provedor (ninguém conectado): nada a encerrar.
            log.info("Encerramento de sala ignorado: sala inexistente ou indisponível no provedor.");
        }
    }

    @Override
    public Optional<VideoWebhookEvent> parseWebhook(byte[] rawBody, String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new InvalidWebhookSignatureException("Webhook sem assinatura.");
        }
        String token = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring("Bearer ".length()).trim()
                : authorizationHeader.trim();

        DecodedJWT jwt;
        try {
            jwt = JWT.require(algorithm())
                    .withIssuer(properties.getApiKey())
                    .acceptLeeway(WEBHOOK_LEEWAY_SECONDS)
                    .build()
                    .verify(token);
        } catch (JWTVerificationException ex) {
            throw new InvalidWebhookSignatureException("Assinatura do webhook inválida.");
        }

        String expectedHash = jwt.getClaim("sha256").asString();
        String actualHash = Base64.getEncoder().encodeToString(sha256(rawBody));
        if (expectedHash == null || !MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII), actualHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new InvalidWebhookSignatureException("Corpo do webhook não confere com a assinatura.");
        }

        JsonNode body;
        try {
            body = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new InvalidWebhookSignatureException("Corpo do webhook ilegível.");
        }

        VideoWebhookEvent.Type type = switch (body.path("event").asText("")) {
            case "participant_joined" -> VideoWebhookEvent.Type.PARTICIPANT_JOINED;
            // Conexão abortada também é uma saída, do ponto de vista de presença.
            case "participant_left", "participant_connection_aborted" -> VideoWebhookEvent.Type.PARTICIPANT_LEFT;
            case "room_finished" -> VideoWebhookEvent.Type.ROOM_FINISHED;
            default -> null;
        };
        if (type == null) {
            return Optional.empty();
        }

        String roomName = body.path("room").path("name").asText(null);
        String identity = body.path("participant").path("identity").asText(null);
        String eventId = body.path("id").asText(null);
        // createdAt é int64 em segundos; o JSON do protobuf pode trazê-lo como string.
        long createdAt = body.path("createdAt").asLong(0);
        Instant occurredAt = createdAt > 0 ? Instant.ofEpochSecond(createdAt) : Instant.now();

        return Optional.of(new VideoWebhookEvent(eventId, type, roomName, identity, occurredAt));
    }

    private void callRoomService(String method, Map<String, Object> body) {
        String apiUrl = properties.resolvedApiUrl();
        if (apiUrl == null) {
            throw VideoAccessException.unavailable();
        }
        try {
            restClient.post()
                    .uri(apiUrl + ROOM_SERVICE + method)
                    .header("Authorization", "Bearer " + serverToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            log.warn("Provedor de vídeo recusou {}: HTTP {}", method, ex.getStatusCode().value());
            throw VideoAccessException.unavailable();
        } catch (RestClientException ex) {
            log.warn("Provedor de vídeo indisponível em {}: {}", method, ex.getClass().getSimpleName());
            throw VideoAccessException.unavailable();
        }
    }

    /** JWT de servidor, de vida curtíssima, só com permissão de criar/administrar salas. */
    private String serverToken() {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(properties.getApiKey())
                .withNotBefore(Date.from(now))
                .withExpiresAt(Date.from(now.plus(SERVER_TOKEN_TTL)))
                .withClaim("video", Map.of("roomCreate", true, "roomAdmin", true, "roomList", true))
                .sign(algorithm());
    }

    private Algorithm algorithm() {
        if (properties.getApiSecret() == null || properties.getApiSecret().isBlank()) {
            throw VideoAccessException.unavailable();
        }
        return Algorithm.HMAC256(properties.getApiSecret());
    }

    private static byte[] sha256(byte[] body) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(body);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
