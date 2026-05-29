package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dao.UtenteDAO;
import it.gavia.sostitutoincloud.dto.auth.UserMeDTO;
import it.gavia.sostitutoincloud.model.Utente;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Log4j2
public class AuthController {

    private final UtenteDAO utenteDAO;

    public AuthController(UtenteDAO utenteDAO) {
        this.utenteDAO = utenteDAO;
    }

    @GetMapping("/me")
    public ResponseEntity<UserMeDTO> me() {
        Integer utenteId = SecurityUtils.getCurrentUtenteId();
        Utente utente = utenteDAO.findById(utenteId)
                .orElseThrow(() -> new RuntimeException("Utente non trovato: id=" + utenteId));

        UserMeDTO dto = UserMeDTO.builder()
                .id(utente.getId())
                .email(utente.getEmail())
                .ruolo(utente.getRuolo())
                .fkTenantId(utente.getFkTenantId())
                .firstName(utente.getFirstName())
                .lastName(utente.getLastName())
                .attivo(utente.getAttivo())
                .build();

        log.info("AuthController.me() - user={}", utente.getEmail());
        return ResponseEntity.ok(dto);
    }
}
