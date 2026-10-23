package com.psiconet.services.implement.clinical;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.mapper.AppointmentMapper;
import com.psiconet.model.dtos.clinical.AppointmentCreateDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.RecurrenceRule;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.entities.profile.Patient;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.repositories.clinical.AppointmentRepository;
import com.psiconet.repositories.clinical.RecurrenceRuleRepository;
import com.psiconet.repositories.clinical.TreatmentLinkRepository;
import com.psiconet.repositories.financial.PaymentRepository;
import com.psiconet.repositories.profile.PatientRepository;
import com.psiconet.repositories.profile.PsychologistRepository;
import com.psiconet.services.implement.financial.PaymentGenerationService;
import com.psiconet.services.interfaces.NotificationService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PatientSchedulingGuardTest {

    private final AppointmentRepository appointmentRepository = mock(AppointmentRepository.class);
    private final TreatmentLinkRepository treatmentLinkRepository = mock(TreatmentLinkRepository.class);
    private final RecurrenceRuleRepository recurrenceRuleRepository = mock(RecurrenceRuleRepository.class);
    private final PatientRepository patientRepository = mock(PatientRepository.class);
    private final PsychologistRepository psychologistRepository = mock(PsychologistRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    // Classe concreta (não mockável no JDK 25+) e nunca alcançada quando o agendamento é barrado.
    private final PaymentGenerationService paymentGenerationService = null;

    private final RecurrenceGenerationService generationService = new RecurrenceGenerationService(
            recurrenceRuleRepository, appointmentRepository, notificationService, paymentGenerationService);

    private final AppointmentServiceImpl appointmentService = new AppointmentServiceImpl(
            appointmentRepository, treatmentLinkRepository, recurrenceRuleRepository, generationService,
            mock(PaymentRepository.class), patientRepository, psychologistRepository, mock(UserRepository.class),
            new com.psiconet.mapper.AppointmentMapperImpl(), notificationService, paymentGenerationService,
            new com.psiconet.infra.video.VideoProperties());

    private TreatmentLink linkWithPatientStatus(UserStatusEnum status) {
        User patientUser = new User();
        patientUser.setId(UUID.randomUUID());
        patientUser.setStatus(status);
        Patient patient = new Patient();
        patient.setUser(patientUser);

        User psychologistUser = new User();
        psychologistUser.setId(UUID.randomUUID());
        psychologistUser.setRole(RoleEnum.PSYCHOLOGIST);
        Psychologist psychologist = new Psychologist();
        psychologist.setUser(psychologistUser);

        TreatmentLink link = new TreatmentLink();
        link.setPatient(patient);
        link.setPsychologist(psychologist);
        link.setIsActive(true);
        link.setDefaultPrice(BigDecimal.TEN);
        return link;
    }

    @ParameterizedTest
    @EnumSource(value = UserStatusEnum.class, names = {"INACTIVE", "BLOCKED", "PENDING_VERIFICATION"})
    void psicologoNaoAgendaConsultaComPacienteSemContaAtiva(UserStatusEnum status) {
        TreatmentLink link = linkWithPatientStatus(status);
        User psychologistUser = link.getPsychologist().getUser();
        when(psychologistRepository.findByUser(psychologistUser)).thenReturn(Optional.of(link.getPsychologist()));
        when(patientRepository.findByUserId(link.getPatient().getUser().getId())).thenReturn(Optional.of(link.getPatient()));
        when(treatmentLinkRepository.findByPatientAndPsychologistAndIsActiveTrue(link.getPatient(), link.getPsychologist()))
                .thenReturn(Optional.of(link));

        AppointmentCreateDTO dto = new AppointmentCreateDTO();
        dto.setPatientId(link.getPatient().getUser().getId());
        dto.setStartDateTime(LocalDateTime.now().plusDays(1));
        dto.setMeetingType(MeetingTypeEnum.VIDEO_CALL);
        dto.setMeetingLink("https://meet.example.com/x");

        assertThrows(BusinessException.class, () -> appointmentService.create(psychologistUser, dto));
        verify(appointmentRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatusEnum.class, names = {"INACTIVE", "BLOCKED", "PENDING_VERIFICATION"})
    void recorrenciaNaoGeraConsultaParaPacienteSemContaAtiva(UserStatusEnum status) {
        RecurrenceRule rule = new RecurrenceRule();
        rule.setTreatmentLink(linkWithPatientStatus(status));
        rule.setIsActive(true);

        generationService.ensureNextInstance(rule);

        verify(appointmentRepository, never()).save(any());
        verify(recurrenceRuleRepository, never()).save(any());
    }
}
