package com.psiconet.controllers.psychologist;

import com.psiconet.model.dtos.profile.open.PublicPsychologistDTO;
import com.psiconet.services.implement.profile.PsychologistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/psychologists")
@RequiredArgsConstructor
public class PsychologistController {

    private final PsychologistService service;

    @GetMapping("/search")
    public ResponseEntity<PublicPsychologistDTO> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String crp
    ) {
        if (crp != null) {
            return ResponseEntity.ok(service.searchByCrp(crp));
        }
        if (name != null) {
            return ResponseEntity.ok(service.searchByName(name));
        }

        throw new IllegalArgumentException("Informe ao menos um parâmetro: name ou crp");
    }
}