package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.TenantDAO;
import it.gavia.sostitutoincloud.dao.TenantSettingsDAO;
import it.gavia.sostitutoincloud.dto.tenant.TenantCreateDTO;
import it.gavia.sostitutoincloud.dto.tenant.TenantDetailDTO;
import it.gavia.sostitutoincloud.dto.tenant.TenantListDTO;
import it.gavia.sostitutoincloud.dto.tenant.TenantUpdateDTO;
import it.gavia.sostitutoincloud.model.Tenant;
import it.gavia.sostitutoincloud.model.TenantSettings;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Log4j2
public class TenantService {

    private static final Set<String> STATI_VALIDI = Set.of("active", "suspended");

    private final TenantDAO tenantDAO;
    private final PropertyDAO propertyDAO;
    private final OwnerProfileDAO ownerProfileDAO;
    private final BookingDAO bookingDAO;
    private final TenantSettingsDAO tenantSettingsDAO;

    public TenantService(TenantDAO tenantDAO,
                         PropertyDAO propertyDAO,
                         OwnerProfileDAO ownerProfileDAO,
                         BookingDAO bookingDAO,
                         TenantSettingsDAO tenantSettingsDAO) {
        this.tenantDAO = tenantDAO;
        this.propertyDAO = propertyDAO;
        this.ownerProfileDAO = ownerProfileDAO;
        this.bookingDAO = bookingDAO;
        this.tenantSettingsDAO = tenantSettingsDAO;
    }

    public List<TenantListDTO> findAll() {
        List<Tenant> tenants = tenantDAO.findAll();
        log.info("TenantService.findAll() - {} tenant trovati", tenants.size());
        return tenants.stream()
                .map(this::toListDTO)
                .toList();
    }

    public Optional<TenantDetailDTO> findById(Integer id) {
        log.info("TenantService.findById() - id={}", id);
        return tenantDAO.findById(id)
                .map(this::toDetailDTO);
    }

    public TenantDetailDTO create(TenantCreateDTO dto) {
        log.info("TenantService.create() - taxCode={}", dto.getTaxCode());
        tenantDAO.findByTaxCode(dto.getTaxCode()).ifPresent(t -> {
            throw new IllegalArgumentException("Tenant con questo codice fiscale già esistente");
        });
        Tenant tenant = Tenant.builder()
                .legalName(dto.getLegalName())
                .displayName(dto.getDisplayName())
                .taxCode(dto.getTaxCode())
                .vatNumber(dto.getVatNumber())
                .stato("draft")
                .administrativeEmail(dto.getAdministrativeEmail())
                .pec(dto.getPec())
                .phone(dto.getPhone())
                .legalAddress(dto.getLegalAddress())
                .build();
        Tenant saved = tenantDAO.insert(tenant);
        tenantSettingsDAO.save(defaultSettings(saved.getId()));
        return toDetailDTO(saved);
    }

    public TenantDetailDTO update(Integer id, TenantUpdateDTO dto) {
        log.info("TenantService.update() - id={}", id);
        Tenant existing = tenantDAO.findById(id)
                .orElseThrow(() -> new RuntimeException("Tenant non trovato: id=" + id));
        if (dto.getTaxCode() != null && !dto.getTaxCode().equals(existing.getTaxCode())) {
            tenantDAO.findByTaxCode(dto.getTaxCode())
                    .filter(t -> !t.getId().equals(id))
                    .ifPresent(t -> { throw new IllegalArgumentException("Tenant con questo codice fiscale già esistente"); });
        }
        Tenant toUpdate = Tenant.builder()
                .id(existing.getId())
                .legalName(dto.getLegalName() != null ? dto.getLegalName() : existing.getLegalName())
                .displayName(dto.getDisplayName() != null ? dto.getDisplayName() : existing.getDisplayName())
                .taxCode(dto.getTaxCode() != null ? dto.getTaxCode() : existing.getTaxCode())
                .vatNumber(dto.getVatNumber() != null ? dto.getVatNumber() : existing.getVatNumber())
                .administrativeEmail(dto.getAdministrativeEmail() != null ? dto.getAdministrativeEmail() : existing.getAdministrativeEmail())
                .pec(dto.getPec() != null ? dto.getPec() : existing.getPec())
                .phone(dto.getPhone() != null ? dto.getPhone() : existing.getPhone())
                .legalAddress(dto.getLegalAddress() != null ? dto.getLegalAddress() : existing.getLegalAddress())
                .build();
        Tenant updated = tenantDAO.update(toUpdate);
        return toDetailDTO(updated);
    }

    public TenantDetailDTO updateStatus(Integer id, String nuovoStato) {
        log.info("TenantService.updateStatus() - id={} stato={}", id, nuovoStato);
        if (!tenantDAO.existsById(id)) {
            throw new RuntimeException("Tenant non trovato: id=" + id);
        }
        if (!STATI_VALIDI.contains(nuovoStato)) {
            throw new IllegalArgumentException(
                    "Stato non valido: '" + nuovoStato + "'. Valori ammessi: " + STATI_VALIDI);
        }
        Tenant updated = tenantDAO.updateStatus(id, nuovoStato);
        return toDetailDTO(updated);
    }

    private TenantSettings defaultSettings(Integer tenantId) {
        return TenantSettings.builder()
                .fkTenantId(tenantId)
                .withholdingRatePrimary(new BigDecimal("21.00"))
                .withholdingRateSecondary(new BigDecimal("26.00"))
                .codiceTributoF24("1919")
                .documentWindowDays(14)
                .cedolareSeccaEnabled(true)
                .sdiAutoSend(true)
                .derogaRicevutaEnabled(false)
                .numerazioneAutomatica(true)
                .alertScadenzeDocumenti(true)
                .alertScadenzeF24(true)
                .notificheEmail(true)
                .build();
    }

    private TenantListDTO toListDTO(Tenant t) {
        return TenantListDTO.builder()
                .id(t.getId())
                .legalName(t.getLegalName())
                .displayName(t.getDisplayName())
                .taxCode(t.getTaxCode())
                .vatNumber(t.getVatNumber())
                .stato(t.getStato())
                .administrativeEmail(t.getAdministrativeEmail())
                .phone(t.getPhone())
                .legalAddress(t.getLegalAddress())
                .activatedAt(t.getActivatedAt())
                .createdAt(t.getCreatedAt())
                .propertiesCount(propertyDAO.findByTenantId(t.getId()).size())
                .ownersCount(ownerProfileDAO.findByTenantId(t.getId()).size())
                .bookingsCount(bookingDAO.findByTenantId(t.getId()).size())
                .build();
    }

    private TenantDetailDTO toDetailDTO(Tenant t) {
        return TenantDetailDTO.builder()
                .id(t.getId())
                .legalName(t.getLegalName())
                .displayName(t.getDisplayName())
                .taxCode(t.getTaxCode())
                .vatNumber(t.getVatNumber())
                .stato(t.getStato())
                .administrativeEmail(t.getAdministrativeEmail())
                .pec(t.getPec())
                .phone(t.getPhone())
                .legalAddress(t.getLegalAddress())
                .activatedAt(t.getActivatedAt())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .propertiesCount(propertyDAO.findByTenantId(t.getId()).size())
                .ownersCount(ownerProfileDAO.findByTenantId(t.getId()).size())
                .bookingsCount(bookingDAO.findByTenantId(t.getId()).size())
                .build();
    }
}
