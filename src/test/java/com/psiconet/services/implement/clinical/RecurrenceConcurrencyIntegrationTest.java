package com.psiconet.services.implement.clinical;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.RecurrenceRule;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import com.psiconet.model.enums.clinical.RecurrenceFrequencyEnum;
import com.psiconet.repositories.clinical.AppointmentRepository;
import com.psiconet.repositories.clinical.RecurrenceRuleRepository;
import com.psiconet.repositories.clinical.TreatmentLinkRepository;
import com.psiconet.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-07f / SEC-20: o job de recorrência e uma ação do usuário podem materializar a próxima ocorrência
 * ao mesmo tempo. A regra é travada no banco durante a geração — nunca nascem duas ocorrências ativas.
 */
class RecurrenceConcurrencyIntegrationTest extends IntegrationTestBase {

    @Autowired RecurrenceGenerationService generationService;
    @Autowired RecurrenceRuleRepository ruleRepository;
    @Autowired TreatmentLinkRepository linkRepository;
    @Autowired AppointmentRepository appointmentRepository;

    @Test
    void geracaoConcorrenteNaoDuplicaOcorrencia() throws Exception {
        List<RecurrenceRule> rules = new ArrayList<>();
        for (int i = 0; i < 4; i++) rules.add(newRule());

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (RecurrenceRule rule : rules) {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> calls = new ArrayList<>();
                for (int t = 0; t < 4; t++) {
                    // Cada chamada com a sua própria instância da regra, como job e requisição em paralelo.
                    RecurrenceRule copy = ruleRepository.findById(rule.getId()).orElseThrow();
                    calls.add(pool.submit(() -> {
                        start.await();
                        generationService.ensureNextInstance(copy);
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> call : calls) {
                    try {
                        call.get();
                    } catch (Exception ignored) {
                        // Conflito de versão em uma das chamadas é aceitável; duplicar não é.
                    }
                }
            }
        } finally {
            pool.shutdown();
        }

        for (RecurrenceRule rule : rules) {
            long active = appointmentRepository.findAll().stream()
                    .filter(a -> a.getRecurrenceRule() != null && a.getRecurrenceRule().getId().equals(rule.getId()))
                    .count();
            assertThat(active).as("ocorrências da regra %s", rule.getId()).isEqualTo(1);
        }
    }

    private RecurrenceRule newRule() {
        User psychologist = createUser(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE);
        User patient = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);

        TreatmentLink link = new TreatmentLink();
        link.setPsychologist(psychologistRepository.findByUserId(psychologist.getId()).orElseThrow());
        link.setPatient(patientRepository.findByUserId(patient.getId()).orElseThrow());
        link.setIsActive(true);
        link.setStartDate(LocalDate.now());
        link.setDefaultPrice(new BigDecimal("150.00"));
        link = linkRepository.save(link);

        RecurrenceRule rule = new RecurrenceRule();
        rule.setTreatmentLink(link);
        rule.setFrequency(RecurrenceFrequencyEnum.EVERY_N_DAYS);
        rule.setIntervalDays(1);
        rule.setStartTime(LocalTime.of(10, 0));
        rule.setEndTime(LocalTime.of(10, 50));
        rule.setStartDate(LocalDate.now().plusDays(1));
        rule.setMeetingType(MeetingTypeEnum.VIDEO_CALL);
        rule.setMeetingProvider(MeetingProviderEnum.EXTERNAL_LINK);
        rule.setMeetingLink("https://meet.example.com/x");
        rule.setIsActive(true);
        return ruleRepository.save(rule);
    }
}
