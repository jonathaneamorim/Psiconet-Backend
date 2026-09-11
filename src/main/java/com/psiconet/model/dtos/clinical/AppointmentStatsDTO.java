package com.psiconet.model.dtos.clinical;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AppointmentStatsDTO {
    private long accepted;
    private long completed;
    private long cancelled;
    private long noShow;
}
