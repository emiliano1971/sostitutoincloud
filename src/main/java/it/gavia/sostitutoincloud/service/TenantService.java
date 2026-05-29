package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.TenantDAO;
import it.gavia.sostitutoincloud.dto.tenant.TenantDetailDTO;
import it.gavia.sostitutoincloud.dto.tenant.TenantListDTO;
import it.gavia.sostitutoincloud.model.Tenant;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

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

    public TenantService(TenantDAO tenantDAO,
                         PropertyDAO propertyDAO,
                         OwnerProfileDAO ownerProfileDAO,
                         BookingDAO bookingDAO) {
        this.tenantDAO = tenantDAO;
        this.propertyDAO = propertyDAO;
        this.ownerProfileDAO = ownerProfileDAO;
        this.bookingDAO = bookingDAO;
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

    public TenantDetailDTO updateStatus(Integer id, String nuovoStato) {
        log.warn("TenantService.updateStatus() - id={} stato={}", id, nuovoStato);
        if (!tenantDAO.existsById(id)) {
            throw new RuntimeException("Tenant non trovato: id=" + id);
        }
        if (!STATI_VALIDI.contains(nuovoStato)) {
            throw new IllegalArgumentException(
                    "Stato non valido: '" + nuovoStato + "'. Valori ammessi: " + STATI_VALIDI);
        }
        throw new UnsupportedOperationException(
                "Update stato non ancora implementato - richiede insert/update nel DAO");
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
