package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.CanaleOtaDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.PropertyOtaCodeDAO;
import it.gavia.sostitutoincloud.dao.TipoImmobileDAO;
import it.gavia.sostitutoincloud.dto.property.OtaCodeDTO;
import it.gavia.sostitutoincloud.dto.property.PropertyCreateDTO;
import it.gavia.sostitutoincloud.dto.property.PropertyDetailDTO;
import it.gavia.sostitutoincloud.dto.property.PropertyListDTO;
import it.gavia.sostitutoincloud.model.CanaleOta;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.PropertyOtaCode;
import it.gavia.sostitutoincloud.model.TipoImmobile;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Log4j2
public class PropertyService {

    private final PropertyDAO propertyDAO;
    private final PropertyOtaCodeDAO propertyOtaCodeDAO;
    private final OwnerProfileDAO ownerProfileDAO;
    private final BookingDAO bookingDAO;
    private final CanaleOtaDAO canaleOtaDAO;
    private final TipoImmobileDAO tipoImmobileDAO;

    public PropertyService(PropertyDAO propertyDAO,
                           PropertyOtaCodeDAO propertyOtaCodeDAO,
                           OwnerProfileDAO ownerProfileDAO,
                           BookingDAO bookingDAO,
                           CanaleOtaDAO canaleOtaDAO,
                           TipoImmobileDAO tipoImmobileDAO) {
        this.propertyDAO = propertyDAO;
        this.propertyOtaCodeDAO = propertyOtaCodeDAO;
        this.ownerProfileDAO = ownerProfileDAO;
        this.bookingDAO = bookingDAO;
        this.canaleOtaDAO = canaleOtaDAO;
        this.tipoImmobileDAO = tipoImmobileDAO;
    }

    public List<PropertyListDTO> findByTenantId(Integer tenantId) {
        List<Property> properties = propertyDAO.findByTenantId(tenantId);
        log.info("PropertyService.findByTenantId() - tenantId={}, {} property trovate", tenantId, properties.size());
        LookupMaps maps = buildLookupMaps(tenantId);
        return properties.stream()
                .map(p -> toListDTO(p, maps))
                .toList();
    }

    public List<PropertyListDTO> findByTenantIdAndAttivo(Integer tenantId, Boolean attivo) {
        List<Property> properties = propertyDAO.findByTenantIdAndAttivo(tenantId, attivo);
        log.info("PropertyService.findByTenantIdAndAttivo() - tenantId={}, attivo={}, {} property trovate",
                tenantId, attivo, properties.size());
        LookupMaps maps = buildLookupMaps(tenantId);
        return properties.stream()
                .map(p -> toListDTO(p, maps))
                .toList();
    }

    public Optional<PropertyDetailDTO> findById(Integer tenantId, Integer propertyId) {
        log.info("PropertyService.findById() - tenantId={}, propertyId={}", tenantId, propertyId);
        return propertyDAO.findById(propertyId)
                .filter(p -> tenantId.equals(p.getFkTenantId()))
                .map(p -> {
                    LookupMaps maps = buildLookupMaps(tenantId);
                    return toDetailDTO(p, maps);
                });
    }

    public PropertyDetailDTO create(Integer tenantId, PropertyCreateDTO dto) {
        log.warn("PropertyService.create() - tenantId={}, displayName={}", tenantId, dto.getDisplayName());
        throw new UnsupportedOperationException(
                "Creazione property non ancora implementata - richiede insert nel DAO");
    }

    public PropertyDetailDTO updateStatus(Integer tenantId, Integer propertyId, Boolean attivo) {
        log.warn("PropertyService.updateStatus() - tenantId={}, propertyId={}, attivo={}", tenantId, propertyId, attivo);
        propertyDAO.findById(propertyId)
                .filter(p -> tenantId.equals(p.getFkTenantId()))
                .orElseThrow(() -> new RuntimeException("Property non trovata: id=" + propertyId));
        throw new UnsupportedOperationException(
                "Update stato non ancora implementato - richiede update nel DAO");
    }

    // ── lookup helpers ──────────────────────────────────────────────────────

    private LookupMaps buildLookupMaps(Integer tenantId) {
        Map<Integer, String> tipoMap = tipoImmobileDAO.findAll().stream()
                .collect(Collectors.toMap(TipoImmobile::getId, TipoImmobile::getCodice));
        Map<Integer, String> canaleNomeMap = canaleOtaDAO.findAll().stream()
                .collect(Collectors.toMap(CanaleOta::getId, CanaleOta::getNome));
        Map<Integer, OwnerProfile> ownerMap = ownerProfileDAO.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(OwnerProfile::getId, o -> o));
        return new LookupMaps(tipoMap, canaleNomeMap, ownerMap);
    }

    private List<OtaCodeDTO> resolveOtaCodes(Integer propertyId, Map<Integer, String> canaleNomeMap) {
        return propertyOtaCodeDAO.findByPropertyId(propertyId).stream()
                .map(oc -> OtaCodeDTO.builder()
                        .canaleCodiceName(canaleNomeMap.getOrDefault(oc.getFkCanaleOtaId(), null))
                        .externalId(oc.getExternalId())
                        .build())
                .toList();
    }

    private String resolveOwnerName(Integer fkOwnerId, Map<Integer, OwnerProfile> ownerMap) {
        if (fkOwnerId == null) return null;
        OwnerProfile o = ownerMap.get(fkOwnerId);
        if (o == null) return null;
        return o.getFirstName() + " " + o.getLastName();
    }

    private PropertyListDTO toListDTO(Property p, LookupMaps maps) {
        List<OtaCodeDTO> otaCodes = resolveOtaCodes(p.getId(), maps.canaleNomeMap);
        return PropertyListDTO.builder()
                .id(p.getId())
                .internalCode(p.getInternalCode())
                .displayName(p.getDisplayName())
                .address(p.getAddress())
                .city(p.getCity())
                .region(p.getRegion())
                .propertyType(maps.tipoMap.getOrDefault(p.getFkTipoImmobileId(), null))
                .cinCode(p.getCinCode())
                .attivo(p.getAttivo())
                .ownerName(resolveOwnerName(p.getFkOwnerId(), maps.ownerMap))
                .listingsCount(otaCodes.size())
                .bookingsCount(bookingDAO.findByPropertyId(p.getId()).size())
                .otaCodes(otaCodes)
                .createdAt(p.getCreatedAt())
                .build();
    }

    private PropertyDetailDTO toDetailDTO(Property p, LookupMaps maps) {
        List<OtaCodeDTO> otaCodes = resolveOtaCodes(p.getId(), maps.canaleNomeMap);
        return PropertyDetailDTO.builder()
                .id(p.getId())
                .fkTenantId(p.getFkTenantId())
                .fkOwnerId(p.getFkOwnerId())
                .fkPmUserId(p.getFkPmUserId())
                .internalCode(p.getInternalCode())
                .displayName(p.getDisplayName())
                .address(p.getAddress())
                .city(p.getCity())
                .region(p.getRegion())
                .propertyType(maps.tipoMap.getOrDefault(p.getFkTipoImmobileId(), null))
                .cinCode(p.getCinCode())
                .attivo(p.getAttivo())
                .ownerName(resolveOwnerName(p.getFkOwnerId(), maps.ownerMap))
                .listingsCount(otaCodes.size())
                .bookingsCount(bookingDAO.findByPropertyId(p.getId()).size())
                .otaCodes(otaCodes)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private record LookupMaps(
            Map<Integer, String> tipoMap,
            Map<Integer, String> canaleNomeMap,
            Map<Integer, OwnerProfile> ownerMap) {
    }
}
