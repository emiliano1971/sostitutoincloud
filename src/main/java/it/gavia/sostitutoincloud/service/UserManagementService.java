package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.UtenteDAO;
import it.gavia.sostitutoincloud.dto.user.UtenteCreateDTO;
import it.gavia.sostitutoincloud.dto.user.UtenteListDTO;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import it.gavia.sostitutoincloud.model.Utente;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Log4j2
public class UserManagementService {

    private final UtenteDAO utenteDAO;
    private final OwnerProfileDAO ownerProfileDAO;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserManagementService(UtenteDAO utenteDAO,
                                 OwnerProfileDAO ownerProfileDAO,
                                 PasswordEncoder passwordEncoder,
                                 AuditService auditService) {
        this.utenteDAO = utenteDAO;
        this.ownerProfileDAO = ownerProfileDAO;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public List<UtenteListDTO> findByTenantId(Integer tenantId) {
        List<Utente> utenti = utenteDAO.findByTenantId(tenantId);
        log.debug("UserManagementService.findByTenantId() - tenantId={}, {} utenti trovati", tenantId, utenti.size());
        return utenti.stream()
                .map(this::toListDTO)
                .toList();
    }

    public UtenteListDTO create(Integer tenantId, UtenteCreateDTO dto) {
        // Valida ruolo
        if (!"pm_user".equals(dto.getRuolo()) && !"owner_user".equals(dto.getRuolo())) {
            throw new IllegalArgumentException("Ruolo non valido: ammessi solo pm_user o owner_user");
        }
        // Valida email univoca
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email obbligatoria");
        }
        if (utenteDAO.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email già registrata");
        }
        // Valida password
        if (dto.getPassword() == null || dto.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password obbligatoria, minimo 8 caratteri");
        }
        // Valida nome/cognome
        if (dto.getFirstName() == null || dto.getFirstName().isBlank()
                || dto.getLastName() == null || dto.getLastName().isBlank()) {
            throw new IllegalArgumentException("Nome e cognome obbligatori");
        }

        Integer fkOwnerId = null;
        if ("owner_user".equals(dto.getRuolo())) {
            if (dto.getFkOwnerId() == null) {
                throw new IllegalArgumentException("Proprietario obbligatorio per un utente owner_user");
            }
            OwnerProfile owner = ownerProfileDAO.findById(dto.getFkOwnerId())
                    .filter(o -> tenantId.equals(o.getFkTenantId()))
                    .orElseThrow(() -> new IllegalArgumentException("Proprietario non valido per questo tenant"));
            // Verifica che il proprietario non abbia già un utente owner_user
            boolean alreadyAssigned = utenteDAO.findByTenantIdAndRuolo(tenantId, "owner_user").stream()
                    .anyMatch(u -> owner.getId().equals(u.getFkOwnerId()));
            if (alreadyAssigned) {
                throw new IllegalArgumentException("Questo proprietario ha già un utente");
            }
            fkOwnerId = owner.getId();
        }

        Utente utente = Utente.builder()
                .fkTenantId(tenantId)
                .email(dto.getEmail())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .passwordHash(passwordEncoder.encode(dto.getPassword()))
                .ruolo(dto.getRuolo())
                .attivo(true)
                .fkOwnerId(fkOwnerId)
                .build();

        Utente saved = utenteDAO.insert(utente);
        log.info("UserManagementService.create() - email={} ruolo={} tenantId={}",
                saved.getEmail(), saved.getRuolo(), tenantId);
        auditService.log("user.create", "Utente", saved.getId(),
                "Creato utente " + saved.getEmail() + " ruolo " + saved.getRuolo());
        return toListDTO(saved);
    }

    public UtenteListDTO updateStatus(Integer tenantId, Integer utenteId, Boolean attivo) {
        Utente utente = utenteDAO.findById(utenteId)
                .filter(u -> tenantId.equals(u.getFkTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Utente non trovato: id=" + utenteId));
        if ("tenant_admin".equals(utente.getRuolo())) {
            throw new IllegalArgumentException("Non è possibile disattivare l'amministratore del tenant");
        }
        Utente updated = utenteDAO.updateStatus(utenteId, attivo);
        log.info("UserManagementService.updateStatus() - id={} attivo={} tenantId={}", utenteId, attivo, tenantId);
        if (Boolean.TRUE.equals(attivo)) {
            auditService.log("user.activate", "Utente", updated.getId(),
                    "Utente " + updated.getEmail() + " riattivato");
        } else {
            auditService.log("user.suspend", "Utente", updated.getId(),
                    "Utente " + updated.getEmail() + " disattivato");
        }
        return toListDTO(updated);
    }

    public void delete(Integer tenantId, Integer utenteId) {
        Utente utente = utenteDAO.findById(utenteId)
                .filter(u -> tenantId.equals(u.getFkTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Utente non trovato: id=" + utenteId));
        if ("tenant_admin".equals(utente.getRuolo())) {
            throw new IllegalArgumentException("Non è possibile eliminare l'amministratore del tenant");
        }
        utenteDAO.delete(utenteId);
        log.info("UserManagementService.delete() - id={} tenantId={}", utenteId, tenantId);
        auditService.log("user.delete", "Utente", utenteId,
                "Eliminato utente id=" + utenteId);
    }

    private UtenteListDTO toListDTO(Utente u) {
        String ownerName = null;
        if (u.getFkOwnerId() != null) {
            ownerName = ownerProfileDAO.findById(u.getFkOwnerId())
                    .map(this::resolveOwnerName)
                    .orElse(null);
        }
        return UtenteListDTO.builder()
                .id(u.getId())
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .ruolo(u.getRuolo())
                .attivo(u.getAttivo())
                .ownerName(ownerName)
                .createdAt(u.getCreatedAt())
                .lastLogin(u.getLastLogin())
                .build();
    }

    private String resolveOwnerName(OwnerProfile o) {
        if (o.getLegalName() != null && !o.getLegalName().isBlank()) {
            return o.getLegalName();
        }
        return ((o.getFirstName() != null ? o.getFirstName() : "") + " "
                + (o.getLastName() != null ? o.getLastName() : "")).trim();
    }
}
