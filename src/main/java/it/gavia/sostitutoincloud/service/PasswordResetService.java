package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.UtenteDAO;
import it.gavia.sostitutoincloud.model.Utente;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Recupero password via email e cambio password per utente autenticato.
 * Invio tramite JavaMailSender → Postfix locale (localhost:25) → relay Office 365.
 */
@Service
@Log4j2
public class PasswordResetService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UtenteDAO utenteDAO;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.mail.reset-token-expiry-minutes}")
    private int tokenExpiryMinutes;

    /**
     * Base URL del FRONTEND, non del backend: in locale React gira sul dev server
     * Vite (5173) mentre app.base-url punta a Tomcat (8081).
     */
    @Value("${app.mail.reset-link-base-url}")
    private String resetLinkBaseUrl;

    public PasswordResetService(UtenteDAO utenteDAO,
                                JavaMailSender mailSender,
                                PasswordEncoder passwordEncoder) {
        this.utenteDAO = utenteDAO;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Genera un token di reset e invia l'email con il link.
     * Se l'email non esiste non solleva errori e non invia nulla: il chiamante
     * risponde sempre allo stesso modo, per non rivelare quali email sono registrate.
     */
    public void requestReset(String email) {
        if (email == null || email.isBlank()) {
            log.warn("PasswordResetService.requestReset() - email assente, richiesta ignorata");
            return;
        }

        Optional<Utente> utenteOpt = utenteDAO.findByEmail(email.trim());
        if (utenteOpt.isEmpty()) {
            log.warn("PasswordResetService.requestReset() - email non registrata: {}", email);
            return;
        }

        Utente utente = utenteOpt.get();
        String token = generateToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(tokenExpiryMinutes);
        utenteDAO.saveResetToken(utente.getId(), token, expiresAt);

        String link = resetLinkBaseUrl + "/reset-password?token=" + token;
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(mailFrom);
        msg.setTo(utente.getEmail());
        msg.setSubject("Sostituto in Cloud — Reset password");
        msg.setText("""
                Hai richiesto il reset della password.

                Clicca il link seguente per impostare una nuova password (valido %d minuti):

                %s

                Se non hai richiesto il reset, ignora questa email.

                Il Team di Sostituto in Cloud
                """.formatted(tokenExpiryMinutes, link));

        try {
            mailSender.send(msg);
            log.info("PasswordResetService.requestReset() - email={} token generato e email inviata", email);
        } catch (Exception e) {
            // Il token resta valido: l'utente può richiedere di nuovo il reset.
            log.error("PasswordResetService.requestReset() - invio email fallito per {}: {}", email, e.getMessage(), e);
        }
    }

    /** Imposta la nuova password a partire dal token ricevuto per email. */
    public void confirmReset(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token non valido o scaduto");
        }

        Utente utente = utenteDAO.findByResetToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token non valido o scaduto"));

        if (utente.getResetTokenExpiresAt() == null
                || utente.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("PasswordResetService.confirmReset() - token scaduto per utenteId={}", utente.getId());
            utenteDAO.clearResetToken(utente.getId());
            throw new IllegalArgumentException("Token scaduto — richiedere un nuovo reset");
        }

        validateNewPassword(newPassword);
        utenteDAO.updatePassword(utente.getId(), passwordEncoder.encode(newPassword));
        log.info("PasswordResetService.confirmReset() - utenteId={}", utente.getId());
    }

    /** Cambio password per utente autenticato, con verifica della password corrente. */
    public void changePassword(Integer utenteId, String currentPassword, String newPassword) {
        String currentHash = utenteDAO.findPasswordHashById(utenteId)
                .orElseThrow(() -> new IllegalArgumentException("Utente non trovato"));

        if (currentPassword == null || !passwordEncoder.matches(currentPassword, currentHash)) {
            log.warn("PasswordResetService.changePassword() - password corrente errata per utenteId={}", utenteId);
            throw new IllegalArgumentException("Password corrente non corretta");
        }

        validateNewPassword(newPassword);
        utenteDAO.updatePassword(utenteId, passwordEncoder.encode(newPassword));
        log.info("PasswordResetService.changePassword() - utenteId={}", utenteId);
    }

    private void validateNewPassword(String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "La password deve essere di almeno " + MIN_PASSWORD_LENGTH + " caratteri");
        }
    }

    /** 64 caratteri esadecimali da due UUID: sta nel VARCHAR(64) della colonna. */
    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }
}
