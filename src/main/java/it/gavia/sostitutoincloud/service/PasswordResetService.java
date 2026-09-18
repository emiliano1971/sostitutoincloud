package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.UtenteDAO;
import it.gavia.sostitutoincloud.model.Utente;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
    private final Environment environment;

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
                                PasswordEncoder passwordEncoder,
                                Environment environment) {
        this.utenteDAO = utenteDAO;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
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
        // L'hash va letto a parte: SELECT_COLS di UtenteDAO non espone password_hash,
        // quindi utente.getPasswordHash() qui sarebbe sempre null.
        assertDiversaDallaCorrente(utenteDAO.findPasswordHashById(utente.getId()).orElse(null), newPassword);
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
        assertDiversaDallaCorrente(currentHash, newPassword);
        utenteDAO.updatePassword(utenteId, passwordEncoder.encode(newPassword));
        log.info("PasswordResetService.changePassword() - utenteId={}", utenteId);
    }

    /**
     * Cambio password al primo accesso: NON verifica la password corrente perché
     * l'utente non conosce quella temporanea assegnata dall'amministratore.
     * Proprio per questo è vincolato al flag must_change_password: senza il controllo
     * sarebbe un modo per cambiare la password di chiunque partendo da un token rubato.
     */
    public void forceChangePassword(Integer utenteId, String newPassword) {
        Utente utente = utenteDAO.findById(utenteId)
                .orElseThrow(() -> new IllegalArgumentException("Utente non trovato"));

        if (!Boolean.TRUE.equals(utente.getMustChangePassword())) {
            log.warn("PasswordResetService.forceChangePassword() - rifiutata: utenteId={} non ha "
                    + "must_change_password attivo", utenteId);
            throw new IllegalArgumentException(
                    "Cambio forzato non consentito: usa il cambio password ordinario");
        }

        validateNewPassword(newPassword);
        // L'hash va letto a parte: SELECT_COLS di UtenteDAO non espone password_hash,
        // quindi utente.getPasswordHash() qui sarebbe sempre null.
        assertDiversaDallaCorrente(utenteDAO.findPasswordHashById(utenteId).orElse(null), newPassword);
        // updatePassword riporta must_change_password a false.
        utenteDAO.updatePassword(utenteId, passwordEncoder.encode(newPassword));
        log.info("PasswordResetService.forceChangePassword() - utenteId={} password impostata al primo accesso",
                utenteId);
    }

    /**
     * Regole valide per TUTTI i flussi (reset via email, cambio ordinario, cambio forzato).
     * Fuori dal profilo local si richiede anche un minimo di complessità: in locale i dati
     * sono fittizi e la regola intralcerebbe solo lo sviluppo, come già per la validazione IBAN.
     */
    private void validateNewPassword(String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "La password deve essere di almeno " + MIN_PASSWORD_LENGTH + " caratteri");
        }
        if (environment.acceptsProfiles(Profiles.of("local"))) {
            return;
        }
        boolean maiuscola = newPassword.chars().anyMatch(Character::isUpperCase);
        boolean minuscola = newPassword.chars().anyMatch(Character::isLowerCase);
        boolean cifra = newPassword.chars().anyMatch(Character::isDigit);
        if (!maiuscola || !minuscola || !cifra) {
            throw new IllegalArgumentException(
                    "La password deve contenere almeno una maiuscola, una minuscola e una cifra");
        }
    }

    /**
     * Impedisce di reimpostare la password già in uso. Vale per tutti i flussi (reset via
     * email, cambio ordinario, cambio forzato): il confronto è sull'hash a DB, quindi
     * richiede la password in chiaro appena ricevuta, prima della codifica.
     */
    private void assertDiversaDallaCorrente(String currentHash, String newPassword) {
        if (currentHash != null && passwordEncoder.matches(newPassword, currentHash)) {
            throw new IllegalArgumentException("La nuova password non può essere uguale a quella attuale");
        }
    }

    /** 64 caratteri esadecimali da due UUID: sta nel VARCHAR(64) della colonna. */
    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }
}
