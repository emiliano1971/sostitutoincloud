package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.RegimeFiscaleDAO;
import it.gavia.sostitutoincloud.dao.SettlementDAO;
import it.gavia.sostitutoincloud.dto.owner.OwnerDetailDTO;
import it.gavia.sostitutoincloud.dto.owner.OwnerListDTO;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import it.gavia.sostitutoincloud.model.RegimeFiscale;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
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

    public OwnerService(OwnerProfileDAO ownerProfileDAO,
                        PropertyDAO propertyDAO,
                        BookingDAO bookingDAO,
                        SettlementDAO settlementDAO,
                        RegimeFiscaleDAO regimeFiscaleDAO) {
        this.ownerProfileDAO = ownerProfileDAO;
        this.propertyDAO = propertyDAO;
        this.bookingDAO = bookingDAO;
        this.settlementDAO = settlementDAO;
        this.regimeFiscaleDAO = regimeFiscaleDAO;
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

    public OwnerDetailDTO updateStatus(Integer tenantId, Integer ownerId, Boolean attivo) {
        log.warn("OwnerService.updateStatus() - tenantId={}, ownerId={}, attivo={}", tenantId, ownerId, attivo);
        OwnerProfile owner = ownerProfileDAO.findById(ownerId)
                .filter(o -> tenantId.equals(o.getFkTenantId()))
                .orElseThrow(() -> new RuntimeException("Owner non trovato: id=" + ownerId));
        throw new UnsupportedOperationException(
                "Update stato non ancora implementato - richiede insert/update nel DAO");
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
