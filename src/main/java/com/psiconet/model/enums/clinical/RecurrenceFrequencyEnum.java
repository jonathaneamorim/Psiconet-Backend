package com.psiconet.model.enums.clinical;

public enum RecurrenceFrequencyEnum {
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    BIMONTHLY,
    QUARTERLY,
    FIRST_DAY_OF_MONTH,
    LAST_DAY_OF_MONTH,
    // Intervalo livre em dias corridos (usa RecurrenceRule.intervalDays); pode cair em fim de semana.
    EVERY_N_DAYS
}
