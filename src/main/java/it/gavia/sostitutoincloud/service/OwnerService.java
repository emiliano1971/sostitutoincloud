package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.CuRecordDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyContractRuleDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.RegimeFiscaleDAO;
import it.gavia.sostitutoincloud.dao.SettlementDAO;
import it.gavia.sostitutoincloud.dao.UtenteDAO;
import it.gavia.sostitutoincloud.dao.WithholdingLedgerDAO;
import it.gavia.sostitutoincloud.dto.owner.OwnerCreateDTO;
import it.gavia.sostitutoincloud.dto.owner.OwnerDetailDTO;
import it.gavia.sostitutoincloud.dto.owner.OwnerListDTO;
import it.gavia.sostitutoincloud.dto.owner.OwnerUpdateDTO;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.RegimeFiscale;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Log4j2
public class OwnerService {

    private final OwnerProfileDAO ownerProfileDAO;
    private final PropertyDAO propertyDAO;
    private final BookingDAO bookingDAO;
    private final SettlementDAO settlementDAO;
    private final RegimeFiscaleDAO regimeFiscaleDAO;
    private final AuditService auditService;
    private final PropertyContractRuleDAO propertyContractRuleDAO;
    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final CuRecordDAO cuRecordDAO;
    private final WithholdingLedgerDAO withholdingLedgerDAO;
    private final UtenteDAO utenteDAO;

    public OwnerService(OwnerProfileDAO ownerProfileDAO,
                        PropertyDAO propertyDAO,
                        BookingDAO bookingDAO,
                        SettlementDAO settlementDAO,
                        RegimeFiscaleDAO regimeFiscaleDAO,
                        AuditService auditService,
                        PropertyContractRuleDAO propertyContractRuleDAO,
                        FiscalDocumentDAO fiscalDocumentDAO,
                        CuRecordDAO cuRecordDAO,
                        WithholdingLedgerDAO withholdingLedgerDAO,
                        UtenteDAO utenteDAO) {
        this.propertyContractRuleDAO = propertyContractRuleDAO;
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.cuRecordDAO = cuRecordDAO;
        this.withholdingLedgerDAO = withholdingLedgerDAO;
        this.utenteDAO = utenteDAO;
        this.ownerProfileDAO = ownerProfileDAO;
        this.propertyDAO = propertyDAO;
        this.bookingDAO = bookingDAO;
        this.settlementDAO = settlementDAO;
        this.regimeFiscaleDAO = regimeFiscaleDAO;
        this.auditService = auditService;
    }

    public List<OwnerListDTO> findByTenantId(Integer tenantId) {
        List<OwnerProfile> owners = ownerProfileDAO.findByTenantId(tenantId);
        log.info("OwnerService.findByTenantId() - tenantId={}, {} owner trovati", tenantId, owners.size());
        Map<Integer, String> regimeMap = buildRegimeMap();
        return owners.stream()
                .map(o -> toListDTO(o, regimeMap))
                .toList();
    }

    public List<OwnerListDTO> findByTenantIdAndAttivo(Integer tenantId, Boolean attivo) {
        List<OwnerProfile> owners = ownerProfileDAO.findByTenantIdAndAttivo(tenantId, attivo);
        log.info("OwnerService.findByTenantIdAndAttivo() - tenantId={}, attivo={}, {} owner trovati",
                tenantId, attivo, owners.size());
        Map<Integer, String> regimeMap = buildRegimeMap();
        return owners.stream()
                .map(o -> toListDTO(o, regimeMap))
                .toList();
    }

    public Optional<OwnerDetailDTO> findById(Integer tenantId, Integer ownerId) {
        log.info("OwnerService.findById() - tenantId={}, ownerId={}", tenantId, ownerId);
        return ownerProfileDAO.findById(ownerId)
                .filter(o -> tenantId.equals(o.getFkTenantId()))
                .map(o -> {
                    Map<Integer, String> regimeMap = buildRegimeMap();
                    List<Booking> bookings = bookingDAO.findByOwnerId(o.getId());
                    BigDecimal totalGross = bookings.stream()
                            .map(Booking::getGrossAmount)
                            .filter(v -> v != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal totalOwnerNet = bookings.stream()
                            .map(Booking::getOwnerNetAmount)
                            .filter(v -> v != null)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return toDetailDTO(o, regimeMap,
                            propertyDAO.findByOwnerId(o.getId()).size(),
                            bookings.size(),
                            totalGross,
                            totalOwnerNet,
                            settlementDAO.findByOwnerId(o.getId()).size());
                });
    }

    public OwnerDetailDTO create(Integer tenantId, OwnerCreateDTO dto) {
        log.info("OwnerService.create() - tenantId={} taxCode={}", tenantId, dto.getTaxCode());
        ownerProfileDAO.findByTaxCode(dto.getTaxCode())
                .filter(o -> tenantId.equals(o.getFkTenantId()))
                .ifPresent(o -> { throw new IllegalArgumentException("Proprietario con questo CF già esistente"); });
        Integer regimeId = dto.getFkRegimeFiscaleId();
        if (regimeId == null) {
            regimeId = regimeFiscaleDAO.findByCodice("cedolare_secca")
                    .orElseThrow(() -> new RuntimeException("Regime cedolare_secca non trovato"))
                    .getId();
        }
        OwnerProfile owner = OwnerProfile.builder()
                .fkTenantId(tenantId)
                .ownerType(dto.getOwnerType())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .legalName(dto.getLegalName())
                .taxCode(dto.getTaxCode())
                .vatNumber(dto.getVatNumber())
                .fkRegimeFiscaleId(regimeId)
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .iban(dto.getIban())
                .attivo(true)
                .build();
        OwnerProfile saved = ownerProfileDAO.insert(owner);
        auditService.log("owner.create", "OwnerProfile", saved.getId(),
                "Creato proprietario " + saved.getFirstName() + " " + saved.getLastName());
        Map<Integer, String> regimeMap = buildRegimeMap();
        return toDetailDTO(saved, regimeMap, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }

    public OwnerDetailDTO update(Integer tenantId, Integer ownerId, OwnerUpdateDTO dto) {
        log.info("OwnerService.update() - id={}", ownerId);
        OwnerProfile existing = ownerProfileDAO.findById(ownerId)
                .filter(o -> tenantId.equals(o.getFkTenantId()))
                .orElseThrow(() -> new RuntimeException("Owner non trovato: id=" + ownerId));
        if (dto.getTaxCode() != null && !dto.getTaxCode().equals(existing.getTaxCode())) {
            ownerProfileDAO.findByTaxCode(dto.getTaxCode())
                    .filter(o -> tenantId.equals(o.getFkTenantId()) && !o.getId().equals(ownerId))
                    .ifPresent(o -> { throw new IllegalArgumentException("Proprietario con questo CF già esistente"); });
        }
        OwnerProfile toUpdate = OwnerProfile.builder()
                .id(existing.getId())
                .fkTenantId(existing.getFkTenantId())
                .ownerType(dto.getOwnerType() != null ? dto.getOwnerType() : existing.getOwnerType())
                .firstName(dto.getFirstName() != null ? dto.getFirstName() : existing.getFirstName())
                .lastName(dto.getLastName() != null ? dto.getLastName() : existing.getLastName())
                .legalName(dto.getLegalName() != null ? dto.getLegalName() : existing.getLegalName())
                .taxCode(dto.getTaxCode() != null ? dto.getTaxCode() : existing.getTaxCode())
                .vatNumber(dto.getVatNumber() != null ? dto.getVatNumber() : existing.getVatNumber())
                .fkRegimeFiscaleId(dto.getFkRegimeFiscaleId() != null ? dto.getFkRegimeFiscaleId() : existing.getFkRegimeFiscaleId())
                .email(dto.getEmail() != null ? dto.getEmail() : existing.getEmail())
                .phone(dto.getPhone() != null ? dto.getPhone() : existing.getPhone())
                .iban(dto.getIban() != null ? dto.getIban() : existing.getIban())
                .build();
        OwnerProfile updated = ownerProfileDAO.updateAnagrafica(toUpdate);
        auditService.log("owner.update", "OwnerProfile", updated.getId(),
                "Aggiornato proprietario " + updated.getFirstName() + " " + updated.getLastName());
        Map<Integer, String> regimeMap = buildRegimeMap();
        List<Booking> bookings = bookingDAO.findByOwnerId(updated.getId());
        BigDecimal totalGross = bookings.stream()
                .map(Booking::getGrossAmount).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOwnerNet = bookings.stream()
                .map(Booking::getOwnerNetAmount).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return toDetailDTO(updated, regimeMap,
                propertyDAO.findByOwnerId(updated.getId()).size(),
                bookings.size(), totalGross, totalOwnerNet,
                settlementDAO.findByOwnerId(updated.getId()).size());
    }

    public OwnerDetailDTO updateStatus(Integer tenantId, Integer ownerId, Boolean attivo) {
        log.info("OwnerService.updateStatus() - tenantId={}, ownerId={}, attivo={}", tenantId, ownerId, attivo);
        ownerProfileDAO.findById(ownerId)
                .filter(o -> tenantId.equals(o.getFkTenantId()))
                .orElseThrow(() -> new RuntimeException("Owner non trovato: id=" + ownerId));
        OwnerProfile updated = ownerProfileDAO.updateStatus(ownerId, attivo);
        if (Boolean.TRUE.equals(attivo)) {
            auditService.log("owner.activate", "OwnerProfile", updated.getId(),
                    "Proprietario " + updated.getFirstName() + " " + updated.getLastName() + " riattivato");
        } else {
            auditService.log("owner.suspend", "OwnerProfile", updated.getId(),
                    "Proprietario " + updated.getFirstName() + " " + updated.getLastName() + " disattivato");
        }
        Map<Integer, String> regimeMap = buildRegimeMap();
        List<Booking> bookings = bookingDAO.findByOwnerId(updated.getId());
        BigDecimal totalGross = bookings.stream()
                .map(Booking::getGrossAmount).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOwnerNet = bookings.stream()
                .map(Booking::getOwnerNetAmount).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return toDetailDTO(updated, regimeMap,
                propertyDAO.findByOwnerId(updated.getId()).size(),
                bookings.size(), totalGross, totalOwnerNet,
                settlementDAO.findByOwnerId(updated.getId()).size());
    }

    /**
     * Elimina il proprietario con i suoi immobili e le regole di contratto.
     * Bloccato (IllegalStateException → 400) se esistono prenotazioni sugli immobili, dati
     * fiscali collegati (documenti, liquidazioni, CU, ritenute: FK RESTRICT) o un utente di
     * accesso al portale collegato.
     *
     * @throws NoSuchElementException proprietario inesistente o di altro tenant (404)
     */
    @Transactional
    public void eliminaProprietario(Integer tenantId, Integer ownerId) {
        OwnerProfile owner = ownerProfileDAO.findById(ownerId)
                .filter(o -> tenantId.equals(o.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Proprietario non trovato: id=" + ownerId));

        int prenotazioni = bookingDAO.countByOwnerProperties(ownerId, tenantId);
        if (prenotazioni > 0) {
            throw new IllegalStateException("Impossibile eliminare: esistono " + prenotazioni
                    + " prenotazioni associate agli immobili di questo proprietario");
        }
        List<String> vincoli = new ArrayList<>();
        int documenti = fiscalDocumentDAO.findByOwnerAndTenant(tenantId, ownerId).size();
        if (documenti > 0) vincoli.add(documenti + " documenti fiscali");
        int liquidazioni = settlementDAO.findByTenantIdAndOwnerId(tenantId, ownerId).size();
        if (liquidazioni > 0) vincoli.add(liquidazioni + " liquidazioni");
        int cu = cuRecordDAO.findByTenantIdAndOwnerId(tenantId, ownerId).size();
        if (cu > 0) vincoli.add(cu + " CU");
        int ritenute = withholdingLedgerDAO.countByOwner(ownerId, tenantId);
        if (ritenute > 0) vincoli.add(ritenute + " ritenute registrate");
        if (!vincoli.isEmpty()) {
            throw new IllegalStateException("Impossibile eliminare: esistono " + String.join(", ", vincoli)
                    + " associati a questo proprietario");
        }
        if (utenteDAO.countByOwnerId(ownerId) > 0) {
            throw new IllegalStateException("Impossibile eliminare: esiste un utente di accesso al portale "
                    + "collegato a questo proprietario, eliminarlo prima da Utenti");
        }

        List<Property> immobili = propertyDAO.findByOwnerAndTenant(tenantId, ownerId);
        for (Property p : immobili) {
            propertyContractRuleDAO.deleteByPropertyId(p.getId());
        }
        propertyDAO.deleteByOwnerId(ownerId, tenantId);
        ownerProfileDAO.deleteById(ownerId, tenantId);

        auditService.log("owner.delete", "OwnerProfile", ownerId,
                "Eliminato proprietario " + owner.getFirstName() + " " + owner.getLastName()
                        + " (" + owner.getTaxCode() + ") con " + immobili.size() + " immobili");
        log.info("OwnerService.eliminaProprietario() - tenant={} owner={} immobili={}", tenantId, ownerId, immobili.size());
    }

    private Map<Integer, String> buildRegimeMap() {
        return regimeFiscaleDAO.findAll().stream()
                .collect(Collectors.toMap(RegimeFiscale::getId, RegimeFiscale::getCodice));
    }

    private OwnerListDTO toListDTO(OwnerProfile o, Map<Integer, String> regimeMap) {
        return OwnerListDTO.builder()
                .id(o.getId())
                .ownerType(o.getOwnerType())
                .firstName(o.getFirstName())
                .lastName(o.getLastName())
                .legalName(o.getLegalName())
                .taxCode(o.getTaxCode())
                .vatNumber(o.getVatNumber())
                .fiscalRegime(regimeMap.getOrDefault(o.getFkRegimeFiscaleId(), null))
                .email(o.getEmail())
                .phone(o.getPhone())
                .iban(o.getIban())
                .attivo(o.getAttivo())
                .propertiesCount(propertyDAO.findByOwnerId(o.getId()).size())
                .createdAt(o.getCreatedAt())
                .build();
    }

    private OwnerDetailDTO toDetailDTO(OwnerProfile o, Map<Integer, String> regimeMap,
                                       int propertiesCount, int bookingsCount,
                                       BigDecimal totalGrossAmount, BigDecimal totalOwnerNet,
                                       int settlementsCount) {
        return OwnerDetailDTO.builder()
                .id(o.getId())
                .fkTenantId(o.getFkTenantId())
                .ownerType(o.getOwnerType())
                .firstName(o.getFirstName())
                .lastName(o.getLastName())
                .legalName(o.getLegalName())
                .taxCode(o.getTaxCode())
                .vatNumber(o.getVatNumber())
                .fiscalRegime(regimeMap.getOrDefault(o.getFkRegimeFiscaleId(), null))
                .email(o.getEmail())
                .phone(o.getPhone())
                .iban(o.getIban())
                .attivo(o.getAttivo())
                .propertiesCount(propertiesCount)
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .bookingsCount(bookingsCount)
                .totalGrossAmount(totalGrossAmount)
                .totalOwnerNet(totalOwnerNet)
                .settlementsCount(settlementsCount)
                .build();
    }
}
