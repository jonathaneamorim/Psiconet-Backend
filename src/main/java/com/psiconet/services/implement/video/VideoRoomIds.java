package com.psiconet.services.implement.video;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Identificadores de sala: 128 bits de {@link SecureRandom} em base64url — opacos, imprevisíveis e
 * nunca derivados de nomes ou ids de usuário/consulta. O nome enviado ao provedor leva um prefixo fixo.
 */
public final class VideoRoomIds {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ROOM_NAME_PREFIX = "psn_";

    private VideoRoomIds() {}

    public static String newRoomId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String roomName(String roomId) {
        return ROOM_NAME_PREFIX + roomId;
    }

    public static Optional<String> roomIdFromName(String roomName) {
        if (roomName == null || !roomName.startsWith(ROOM_NAME_PREFIX)) return Optional.empty();
        return Optional.of(roomName.substring(ROOM_NAME_PREFIX.length()));
    }
}
