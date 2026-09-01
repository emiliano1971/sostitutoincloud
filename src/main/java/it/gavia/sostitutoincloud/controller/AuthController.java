package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.config.DatabaseUserDetailsService;
import it.gavia.sostitutoincloud.config.DatabaseUserDetailsService.CustomUserDetails;
import it.gavia.sostitutoincloud.config.JwtUtils;
import it.gavia.sostitutoincloud.dao.UtenteDAO;
import it.gavia.sostitutoincloud.dto.auth.ChangePasswordDTO;
import it.gavia.sostitutoincloud.dto.auth.LoginRequestDTO;
import it.gavia.sostitutoincloud.dto.auth.LoginResponseDTO;
import it.gavia.sostitutoincloud.dto.auth.PasswordResetConfirmDTO;
import it.gavia.sostitutoincloud.dto.auth.PasswordResetRequestDTO;
import it.gavia.sostitutoincloud.dto.auth.UserMeDTO;
import it.gavia.sostitutoincloud.model.Utente;
import it.gavia.sostitutoincloud.service.PasswordResetService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Log4j2
public class AuthController {

    private final UtenteDAO utenteDAO;
    private final DatabaseUserDetailsService userDetailsService;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetService passwordResetService;

    public AuthController(UtenteDAO utenteDAO,
                          DatabaseUserDetailsService userDetailsService,
                          JwtUtils jwtUtils,
                          PasswordEncoder passwordEncoder,
                          PasswordResetService passwordResetService) {
        this.utenteDAO = utenteDAO;
        this.userDetailsService = userDetailsService;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/api/public/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO request) {
        try {
            CustomUserDetails userDetails = (CustomUserDetails)
                    userDetailsService.loadUserByUsername(request.getEmail());
            if (!passwordEncoder.matches(request.getPassword(), userDetails.getPassword())) {
                log.warn("Login fallito per: {}", request.getEmail());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("{\"message\":\"Credenziali non valide\"}");
            }
            String token = jwtUtils.generateToken(userDetails);
            Utente utente = utenteDAO.findById(userDetails.getUtenteId())
                    .orElseThrow(() -> new RuntimeException("Utente non trovato"));
            UserMeDTO userDto = buildUserMeDTO(utente);
            log.info("Login riuscito per: {}", request.getEmail());
            return ResponseEntity.ok(LoginResponseDTO.builder().token(token).user(userDto).build());
        } catch (DisabledException e) {
            log.warn("Login bloccato - tenant sospeso: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            log.warn("Login fallito per: {} — {}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Credenziali non valide"));
        }
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<UserMeDTO> me() {
        Integer utenteId = SecurityUtils.getCurrentUtenteId();
        Utente utente = utenteDAO.findById(utenteId)
                .orElseThrow(() -> new RuntimeException("Utente non trovato: id=" + utenteId));
        log.info("AuthController.me() - user={}", utente.getEmail());
        return ResponseEntity.ok(buildUserMeDTO(utente));
    }

    // ── Recupero / cambio password ─────────────────────────────────────────────

    /**
     * Risponde SEMPRE 200 con lo stesso messaggio, anche se l'email non esiste
     * o se l'invio fallisce: non deve essere possibile dedurre quali email sono
     * registrate a partire dalla risposta.
     */
    @PostMapping("/api/public/password-reset/request")
    public ResponseEntity<?> passwordResetRequest(@RequestBody PasswordResetRequestDTO request) {
        log.info("AuthController.passwordResetRequest() - email={}", request.getEmail());
        try {
            passwordResetService.requestReset(request.getEmail());
        } catch (Exception e) {
            log.error("AuthController.passwordResetRequest() - errore interno: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok(Map.of(
                "message", "Se l'email esiste riceverai le istruzioni per il reset"));
    }

    @PostMapping("/api/public/password-reset/confirm")
    public ResponseEntity<?> passwordResetConfirm(@RequestBody PasswordResetConfirmDTO request) {
        log.info("AuthController.passwordResetConfirm() - richiesta ricevuta");
        try {
            passwordResetService.confirmReset(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password aggiornata"));
        } catch (IllegalArgumentException e) {
            log.warn("AuthController.passwordResetConfirm() - rifiutata: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/api/auth/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordDTO request) {
        Integer utenteId = SecurityUtils.getCurrentUtenteId();
        log.info("AuthController.changePassword() - utenteId={}", utenteId);
        try {
            passwordResetService.changePassword(utenteId, request.getCurrentPassword(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password aggiornata"));
        } catch (IllegalArgumentException e) {
            log.warn("AuthController.changePassword() - rifiutata per utenteId={}: {}", utenteId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private UserMeDTO buildUserMeDTO(Utente utente) {
        return UserMeDTO.builder()
                .id(utente.getId())
                .email(utente.getEmail())
                .ruolo(utente.getRuolo())
                .fkTenantId(utente.getFkTenantId())
                .fkOwnerId(utente.getFkOwnerId())
                .firstName(utente.getFirstName())
                .lastName(utente.getLastName())
                .attivo(utente.getAttivo())
                .build();
    }
}
