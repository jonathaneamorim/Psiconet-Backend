package com.psiconet.services.implement.video;

import com.psiconet.infra.video.VideoAccessException;
import com.psiconet.infra.video.VideoProperties;
import com.psiconet.infra.video.VideoProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Garante no servidor a regra "quem fica sozinho na sala é desconectado" (ADR 016). O front já
 * desconecta ao fim de {@code psiconet.video.alone-timeout}; este job remove pelo provedor quem
 * continuar sozinho depois disso + {@link #GRACE} (aba congelada, cliente alterado...).
 *
 * <p>A cada execução consulta as salas abertas no provedor; uma sala com um único participante passa a
 * ser acompanhada desde a primeira vez em que é vista assim. Estado em memória (uma instância, como os
 * contadores de rate limit): após reiniciar, a contagem recomeça — só a favor do usuário.</p>
 */
@Slf4j
@Service
public class VideoAloneParticipantWatchdog {

    /** Folga sobre o tempo do front, para o cliente desconectar primeiro e com aviso. */
    static final Duration GRACE = Duration.ofMinutes(1);

    private final VideoProvider videoProvider;
    private final VideoProperties properties;
    private final Clock clock;

    /** Sala → (participante sozinho, desde quando). */
    private final Map<String, Alone> aloneByRoom = new ConcurrentHashMap<>();

    @Autowired
    public VideoAloneParticipantWatchdog(VideoProvider videoProvider, VideoProperties properties) {
        this(videoProvider, properties, Clock.systemUTC());
    }

    VideoAloneParticipantWatchdog(VideoProvider videoProvider, VideoProperties properties, Clock clock) {
        this.videoProvider = videoProvider;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedRateString = "${psiconet.video.alone-check-fixed-rate:60000}")
    public void disconnectLongAloneParticipants() {
        if (!properties.isAvailable()) return;
        try {
            check();
        } catch (VideoAccessException ex) {
            // Provedor indisponível: tenta de novo na próxima execução.
            log.warn("Verificação de participantes sozinhos não executada: provedor de vídeo indisponível.");
        }
    }

    void check() {
        Instant now = clock.instant();
        Duration limit = properties.getAloneTimeout().plus(GRACE);
        Set<String> aloneNow = new HashSet<>();

        for (VideoProvider.ActiveRoom room : videoProvider.listRooms()) {
            if (!room.name().startsWith(VideoRoomIds.ROOM_NAME_PREFIX) || room.participants() != 1) continue;

            List<String> identities = videoProvider.listParticipants(room.name());
            if (identities.size() != 1) continue;
            String identity = identities.get(0);
            aloneNow.add(room.name());

            Alone alone = aloneByRoom.compute(room.name(), (name, previous) ->
                    previous != null && previous.identity().equals(identity) ? previous : new Alone(identity, now));

            if (!now.isBefore(alone.since().plus(limit))) {
                videoProvider.removeParticipant(room.name(), identity);
                aloneByRoom.remove(room.name());
                aloneNow.remove(room.name());
                // Sem token, nome ou dado clínico: só a sala opaca e o id do usuário.
                log.info("Participante desconectado por ficar sozinho na sala: sala={} usuario={}", room.name(), identity);
            }
        }
        // Sala vazia, fechada ou com as duas pessoas: para de acompanhar.
        aloneByRoom.keySet().retainAll(aloneNow);
    }

    private record Alone(String identity, Instant since) {}
}
