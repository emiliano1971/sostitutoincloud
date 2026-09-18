package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.config.DatabaseUserDetailsService;
import it.gavia.sostitutoincloud.config.DatabaseUserDetailsService.CustomUserDetails;
import it.gavia.sostitutoincloud.config.JwtUtils;
import it.gavia.sostitutoincloud.dao.UtenteDAO;
import it.gavia.sostitutoincloud.dto.auth.ChangePasswordDTO;
import it.gavia.sostitutoincloud.dto.auth.ForceChangePasswordDTO;
import it.gavia.sostitutoincloud.dto.auth.LoginRequestDTO;
import it.gavia.sostitutoincloud.dto.auth.LoginResponseDTO;
import it.gavia.sostitutoincloud.dto.auth.PasswordResetConfirmDTO;
import it.gavia.sostitutoincloud.dto.auth.PasswordResetRequestDTO;
import it.gavia.sostitutoincloud.dto.auth.UserMeDTO;
import it.gavia.sostitutoincloud.model.Utente;
import it.gavia.sostitutoincloud.service.AuditService;
import it.gavia.sostitutoincloud.service.PasswordResetService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
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
    private final AuditService auditService;

    public AuthController(UtenteDAO utenteDAO,
                          DatabaseUserDetailsService userDetailsService,
                          JwtUtils jwtUtils,
                          PasswordEncoder passwordEncoder,
                          PasswordResetService passwordResetService,
                          AuditService auditService) {
        this.utenteDAO = utenteDAO;
        this.userDetailsService = userDetailsService;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetService = passwordResetService;
        this.auditService = auditService;
    }

    /**
     * IP del chiamante. Dietro il proxy di test/prod l'indirizzo reale è nel primo
     * elemento di X-Forwarded-For; remoteAddr sarebbe sempre quello del proxy.
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip.split(",")[0].trim() : "unknown";
    }

    @PostMapping("/api/public/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO request, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String email = request.getEmail();
        try {
            CustomUserDetails userDetails = (CustomUserDetails)
                    userDetailsService.loadUserByUsername(email);
            if (!passwordEncoder.matches(request.getPassword(), userDetails.getPassword())) {
                log.warn("Login fallito per: {}", email);
                // L'utente esiste: tenant e id sono noti e rendono il tentativo
                // attribuibile in fase di analisi.
                auditService.log("auth.login_failed", "Utente", null,
                        "Login fallito per email: " + email + " — credenziali non valide",
                        userDetails.getTenantId(), userDetails.getUtenteId(), ip, email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("{\"message\":\"Credenziali non valide\"}");
            }
            String token = jwtUtils.generateToken(userDetails);
            Utente utente = utenteDAO.findById(userDetails.getUtenteId())
                    .orElseThrow(() -> new RuntimeException("Utente non trovato"));
            UserMeDTO userDto = buildUserMeDTO(utente);
            boolean mustChange = Boolean.TRUE.equals(utente.getMustChangePassword());
            log.info("Login riuscito per: {} (mustChangePassword={})", email, mustChange);
            auditService.log("auth.login", "Utente", utente.getId(),
                    "Login riuscito per " + email + " — ruolo: " + utente.getRuolo()
                            + " — tenant: " + utente.getFkTenantId(),
                    utente.getFkTenantId(), utente.getId(), ip, email);
            return ResponseEntity.ok(LoginResponseDTO.builder()
                    .token(token)
                    .user(userDto)
                    .mustChangePassword(mustChange)
                    .build());
        } catch (DisabledException e) {
            log.warn("Login bloccato - tenant sospeso: {}", email);
            // L'utente esiste ma loadUserByUsername non lo restituisce: il tenant si
            // ricava dall'anagrafica, così il blocco resta attribuibile.
            Utente utente = utenteDAO.findByEmail(email).orElse(null);
            auditService.log("auth.login_blocked", "Utente", null,
                    "Login bloccato per email: " + email + " — " + e.getMessage(),
                    utente != null ? utente.getFkTenantId() : null,
                    utente != null ? utente.getId() : null, ip, email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            log.warn("Login fallito per: {} — {}", email, e.getMessage());
            // Email sconosciuta: nessun tenant a cui attribuire il tentativo.
            auditService.log("auth.login_failed", "Utente", null,
                    "Login fallito per email: " + email + " — credenziali non valide",
                    null, null, ip, email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Credenziali non valide"));
        }
    }

    /**
     * Il JWT è stateless: non c'è nulla da invalidare lato server. L'endpoint esiste per
     * registrare l'uscita nell'audit; è il frontend a eliminare il token.
     */
    @PostMapping("/api/auth/logout")
    public ResponseEntity<?> logout(HttpServletRequest httpRequest) {
        Integer utenteId = SecurityUtils.getCurrentUtenteId();
        String email = SecurityUtils.getCurrentUserEmail();
        log.info("AuthController.logout() - utenteId={} email={}", utenteId, email);
        auditService.log("auth.logout", "Utente", utenteId,
                "Logout per " + email,
                tenantIdCorrenteOrNull(), utenteId, getClientIp(httpRequest), email);
        return ResponseEntity.ok(Map.of("message", "Logout effettuato"));
    }

    /** Il super_admin non ha tenant: getCurrentTenantId() lì non deve far fallire l'audit. */
    private Integer tenantIdCorrenteOrNull() {
        try {
            return SecurityUtils.getCurrentTenantId();
        } catch (Exception e) {
            return null;
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
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordDTO request,
                                            HttpServletRequest httpRequest) {
        Integer utenteId = SecurityUtils.getCurrentUtenteId();
        log.info("AuthController.changePassword() - utenteId={}", utenteId);
        try {
            passwordResetService.changePassword(utenteId, request.getCurrentPassword(), request.getNewPassword());
            String email = SecurityUtils.getCurrentUserEmail();
            auditService.log("auth.password_changed", "Utente", utenteId,
                    "Password cambiata per " + email,
                    tenantIdCorrenteOrNull(), utenteId, getClientIp(httpRequest), email);
            return ResponseEntity.ok(Map.of("message", "Password aggiornata"));
        } catch (IllegalArgumentException e) {
            log.warn("AuthController.changePassword() - rifiutata per utenteId={}: {}", utenteId, e.getMessage());
            // Il motivo del rifiuto (password corrente errata, requisiti non rispettati,
            // uguale alla precedente) arriva dal messaggio del service.
            String email = SecurityUtils.getCurrentUserEmail();
            auditService.log("auth.password_change_failed", "Utente", utenteId,
                    "Tentativo cambio password fallito per " + email + " — " + e.getMessage(),
                    tenantIdCorrenteOrNull(), utenteId, getClientIp(httpRequest), email);
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Cambio password al primo accesso. Non richiede la password corrente — l'utente
     * non conosce quella temporanea — ed è per questo consentito SOLO a chi ha
     * must_change_password = true: la verifica è nel service, non qui.
     */
    @PostMapping("/api/auth/force-change-password")
    public ResponseEntity<?> forceChangePassword(@RequestBody ForceChangePasswordDTO request,
                                                 HttpServletRequest httpRequest) {
        Integer utenteId = SecurityUtils.getCurrentUtenteId();
        log.info("AuthController.forceChangePassword() - utenteId={}", utenteId);
        try {
            passwordResetService.forceChangePassword(utenteId, request.getNewPassword());
            String email = SecurityUtils.getCurrentUserEmail();
            auditService.log("auth.password_forced_change", "Utente", utenteId,
                    "Cambio password obbligatorio completato per " + email,
                    tenantIdCorrenteOrNull(), utenteId, getClientIp(httpRequest), email);
            return ResponseEntity.ok(Map.of("message", "Password aggiornata"));
        } catch (IllegalArgumentException e) {
            log.warn("AuthController.forceChangePassword() - rifiutata per utenteId={}: {}", utenteId, e.getMessage());
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
                .mustChangePassword(Boolean.TRUE.equals(utente.getMustChangePassword()))
                .build();
    }
}
