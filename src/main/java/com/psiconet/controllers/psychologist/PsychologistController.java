package com.psiconet.controllers.psychologist;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.model.dtos.profile.open.PublicPsychologistDTO;
import com.psiconet.services.implement.profile.PsychologistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/psychologists")
@RequiredArgsConstructor
public class PsychologistController {

    private final PsychologistService service;

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String crp
    ) {

        boolean hasName =
                name != null && !name.isBlank();

        boolean hasCrp =
                crp != null && !crp.isBlank();

        if (!hasName && !hasCrp) {

            throw new BusinessException(
                    "search",
                    "Informe ao menos um parâmetro: name ou crp."
            );
        }

        if (hasCrp) {

            PublicPsychologistDTO psychologist =
                    service.searchByCrp(crp);

            return ResponseEntity.ok(
                    psychologist
            );
        }

        List<PublicPsychologistDTO> psychologists =
                service.searchByName(name);

        return ResponseEntity.ok(
                psychologists
        );
    }
}