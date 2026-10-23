package com.psiconet.services.implement.video;

import com.psiconet.infra.video.VideoAccessException;
import com.psiconet.infra.video.VideoProperties;
import com.psiconet.infra.video.VideoProvider;
import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.repositories.clinical.AppointmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Garante no servidor as regras de ciclo de vida das salas abertas (ADR 016). O front já age no tempo
 * certo, com aviso; este job confirma {@link #GRACE} depois (aba congelada, cliente alterado...):
 * <ul>
 *   <li><b>sozinho</b> por {@code psiconet.video.alone-timeout} ⇒ o participante é removido;</li>
 *   <li><b>limite de horário</b> ({@link VideoJoinWindow#hardStopAt}) ⇒ a sala é encerrada para todos
 *   (evita sessões indefinidas consumindo o provedor). Sala {@code psn_*} sem consulta também é encerrada.</li>
 * </ul>
 * Uma sala com um único participante é acompanhada desde a primeira vez em que é vista assim. Estado em
 * memória (uma instância, como os contadores de rate limit): reiniciar a API só recomeça a contagem.
 * Cada execução tem um orçamento de tempo, para não segurar os outros jobs agendados se o provedor ficar lento.
 */
@Slf4j
@Service
public class VideoRoomWatchdog {

    /** Folga sobre o tempo do front, para o cliente agir primeiro e com aviso. */
    static final Duration GRACE = Duration.ofMinutes(1);

    /** Tempo máximo de uma execução; o que sobrar fica para a próxima. */
    static final Duration RUN_BUDGET = Duration.ofSeconds(20);

    private final VideoProvider videoProvider;
    private final VideoProperties properties;
    private final VideoJoinWindow joinWindow;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    /** Sala → (participante sozinho, desde quando). */
    private final Map<String, Alone> aloneByRoom = new ConcurrentHashMap<>();

    @Autowired
    public VideoRoomWatchdog(VideoProvider videoProvider, VideoProperties properties, VideoJoinWindow joinWindow,
                             AppointmentRepository appointmentRepository) {
        this(videoProvider, properties, joinWindow, appointmentRepository, Clock.systemDefaultZone());
    }

    VideoRoomWatchdog(VideoProvider videoProvider, VideoProperties properties, VideoJoinWindow joinWindow,
                      AppointmentRepository appointmentRepository, Clock clock) {
        this.videoProvider = videoProvider;
        this.properties = properties;
        this.joinWindow = joinWindow;
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    @Scheduled(fixedRateString = "${psiconet.video.room-check-fixed-rate:60000}")
    public void enforceRoomRules() {
        if (!properties.isAvailable()) return;
        try {
            check();
        } catch (VideoAccessException ex) {
            // Provedor indisponível: tenta de novo na próxima execução.
            log.warn("Verificação das salas de vídeo não executada: provedor de vídeo indisponível.");
        }
    }

    void check() {
        Instant deadline = clock.instant().plus(RUN_BUDGET);
        List<VideoProvider.ActiveRoom> rooms = videoProvider.listRooms().stream()
                .filter(room -> room.name().startsWith(VideoRoomIds.ROOM_NAME_PREFIX) && room.participants() > 0)
                .toList();
        Map<String, Appointment> appointmentByRoomId = rooms.isEmpty() ? Map.of()
                : appointmentRepository.findByRoomIdIn(rooms.stream()
                        .map(room -> VideoRoomIds.roomIdFromName(room.name()).orElseThrow())
                        .toList())
                .stream().collect(Collectors.toMap(Appointment::getRoomId, Function.identity()));

        Set<String> aloneNow = new HashSet<>();
        for (VideoProvider.ActiveRoom room : rooms) {
            if (clock.instant().isAfter(deadline)) {
                log.warn("Verificação das salas de vídeo interrompida pelo limite de tempo; continua na próxima execução.");
                aloneNow.addAll(aloneByRoom.keySet());
                break;
            }
            Appointment appointment = VideoRoomIds.roomIdFromName(room.name()).map(appointmentByRoomId::get).orElse(null);
            if (pastHardStop(appointment)) {
                videoProvider.closeRoom(room.name());
                // Sem token, nome ou dado clínico: só a sala opaca.
                log.info("Sala de vídeo encerrada pelo limite de horário: sala={}", room.name());
                continue;
            }
            if (room.participants() == 1 && checkAlone(room.name())) {
                aloneNow.add(room.name());
            }
        }
        // Sala vazia, fechada ou com as duas pessoas: para de acompanhar.
        aloneByRoom.keySet().retainAll(aloneNow);
    }

    private boolean pastHardStop(Appointment appointment) {
        if (appointment == null) return true;
        LocalDateTime now = LocalDateTime.now(clock);
        return !now.isBefore(joinWindow.hardStopAt(appointment).plus(GRACE));
    }

    /** @return true se a sala continua com alguém sozinho (segue acompanhada). */
    private boolean checkAlone(String roomName) {
        List<String> identities = videoProvider.listParticipants(roomName);
        if (identities.size() != 1) return false;
        String identity = identities.get(0);
        Instant now = clock.instant();

        Alone alone = aloneByRoom.compute(roomName, (name, previous) ->
                previous != null && previous.identity().equals(identity) ? previous : new Alone(identity, now));

        if (now.isBefore(alone.since().plus(properties.getAloneTimeout()).plus(GRACE))) return true;

        videoProvider.removeParticipant(roomName, identity);
        // Sem token, nome ou dado clínico: só a sala opaca e o id do usuário.
        log.info("Participante desconectado por ficar sozinho na sala: sala={} usuario={}", roomName, identity);
        return false;
    }

    private record Alone(String identity, Instant since) {}
}
