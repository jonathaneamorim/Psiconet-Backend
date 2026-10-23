package com.psiconet.services.implement.video;

import com.psiconet.infra.video.VideoProperties;
import com.psiconet.infra.video.VideoProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regra "sozinho por 15 min ⇒ desconectado", garantida no servidor com 1 min de folga sobre o front. */
class VideoAloneParticipantWatchdogTest {

    private static final String ROOM = "psn_sala";

    private final FakeProvider provider = new FakeProvider();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-23T14:00:00Z"));
    private VideoAloneParticipantWatchdog watchdog;

    @BeforeEach
    void setUp() {
        VideoProperties properties = new VideoProperties();
        properties.setAloneTimeout(Duration.ofMinutes(15));
        watchdog = new VideoAloneParticipantWatchdog(provider, properties, clock);
    }

    @Test
    void sozinhoAlemDoLimiteMaisFolgaEDesconectado() {
        provider.room(ROOM, "paciente");
        watchdog.check();

        clock.advance(Duration.ofMinutes(15).plusSeconds(59));
        watchdog.check();
        assertTrue(provider.removed.isEmpty(), "dentro da folga o front ainda é quem desconecta");

        clock.advance(Duration.ofSeconds(1));
        watchdog.check();
        assertEquals(List.of(ROOM + ":paciente"), provider.removed);
    }

    @Test
    void chegadaDaOutraPessoaZeraAContagem() {
        provider.room(ROOM, "paciente");
        watchdog.check();

        clock.advance(Duration.ofMinutes(10));
        provider.room(ROOM, "paciente", "psicologo");
        watchdog.check();

        clock.advance(Duration.ofMinutes(1));
        provider.room(ROOM, "paciente");
        watchdog.check();

        clock.advance(Duration.ofMinutes(15));
        watchdog.check();
        assertTrue(provider.removed.isEmpty(), "sozinho de novo há só 15 min");

        clock.advance(Duration.ofMinutes(1));
        watchdog.check();
        assertEquals(List.of(ROOM + ":paciente"), provider.removed);
    }

    @Test
    void trocaDeQuemEstaSozinhoReiniciaAContagem() {
        provider.room(ROOM, "paciente");
        watchdog.check();

        clock.advance(Duration.ofMinutes(10));
        provider.room(ROOM, "psicologo");
        watchdog.check();

        clock.advance(Duration.ofMinutes(10));
        watchdog.check();
        assertTrue(provider.removed.isEmpty());
    }

    @Test
    void ignoraSalasQueNaoSaoDaPsiconet() {
        provider.room("outra_sala", "alguem");
        watchdog.check();
        clock.advance(Duration.ofHours(1));
        watchdog.check();
        assertTrue(provider.removed.isEmpty());
    }

    private static final class FakeProvider implements VideoProvider {
        private final Map<String, List<String>> rooms = new HashMap<>();
        private final List<String> removed = new ArrayList<>();

        void room(String name, String... identities) {
            rooms.put(name, List.of(identities));
        }

        @Override public String name() { return "fake"; }
        @Override public void ensureRoom(String roomName, int maxParticipants) { }
        @Override public IssuedToken issueToken(String roomName, String identity, int maxParticipants) { return null; }
        @Override public void closeRoom(String roomName) { rooms.remove(roomName); }

        @Override
        public List<ActiveRoom> listRooms() {
            return rooms.entrySet().stream().map(e -> new ActiveRoom(e.getKey(), e.getValue().size())).toList();
        }

        @Override
        public List<String> listParticipants(String roomName) {
            return rooms.getOrDefault(roomName, List.of());
        }

        @Override
        public void removeParticipant(String roomName, String identity) {
            removed.add(roomName + ":" + identity);
            rooms.computeIfPresent(roomName, (k, v) -> v.stream().filter(i -> !i.equals(identity)).toList());
        }

        @Override
        public Optional<com.psiconet.infra.video.VideoWebhookEvent> parseWebhook(byte[] rawBody, String authorizationHeader) {
            return Optional.empty();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }

        void advance(Duration duration) { now = now.plus(duration); }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
