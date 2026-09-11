package com.psiconet.mapper;

import com.psiconet.model.dtos.clinical.RecurrenceRuleDTO;
import com.psiconet.model.entities.clinical.RecurrenceRule;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RecurrenceRuleMapper {
    RecurrenceRuleDTO toDto(RecurrenceRule rule);
}
