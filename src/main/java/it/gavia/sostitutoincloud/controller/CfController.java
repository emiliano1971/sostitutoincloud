package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.cf.CfCalcolaRequestDTO;
import it.gavia.sostitutoincloud.service.CodiceFiscaleService;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Calcolo del codice fiscale ospite (autenticato).
 */
@RestController
@RequestMapping("/api/cf")
@Log4j2
public class CfController {

    private final CodiceFiscaleService codiceFiscaleService;

    public CfController(CodiceFiscaleService codiceFiscaleService) {
        this.codiceFiscaleService = codiceFiscaleService;
    }

    @PostMapping("/calcola")
    public ResponseEntity<?> calcola(@RequestBody CfCalcolaRequestDTO req) {
        try {
            String cf = codiceFiscaleService.calcola(
                    req.getCognome(), req.getNome(), req.getDataNascita(),
                    req.getSesso(), req.getComuneNascita());
            return ResponseEntity.ok(Map.of("codiceFiscale", cf));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }
}
