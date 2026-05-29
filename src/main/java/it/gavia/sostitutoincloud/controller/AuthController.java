package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.config.DatabaseUserDetailsService;
import it.gavia.sostitutoincloud.config.DatabaseUserDetailsService.CustomUserDetails;
import it.gavia.sostitutoincloud.config.JwtUtils;
import it.gavia.sostitutoincloud.dao.UtenteDAO;
import it.gavia.sostitutoincloud.dto.auth.LoginRequestDTO;
import it.gavia.sostitutoincloud.dto.auth.LoginResponseDTO;
import it.gavia.sostitutoincloud.dto.auth.UserMeDTO;
import it.gavia.sostitutoincloud.model.Utente;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@Log4j2
public class AuthController {

    private final UtenteDAO utenteDAO;
    private final DatabaseUserDetailsService userDetailsService;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UtenteDAO utenteDAO,
                          DatabaseUserDetailsService userDetailsService,
                          JwtUtils jwtUtils,
                          PasswordEncoder passwordEncoder) {
        this.utenteDAO = utenteDAO;
        this.userDetailsService = userDetailsService;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
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
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException |
                 org.springframework.security.authentication.DisabledException e) {
            log.warn("Login fallito per: {} — {}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("{\"message\":\"Credenziali non valide\"}");
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
