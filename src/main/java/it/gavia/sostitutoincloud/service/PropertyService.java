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
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
    private final AuditService auditService;

    public PropertyService(PropertyDAO propertyDAO,
                           PropertyOtaCodeDAO propertyOtaCodeDAO,
                           OwnerProfileDAO ownerProfileDAO,
                           BookingDAO bookingDAO,
                           CanaleOtaDAO canaleOtaDAO,
                           TipoImmobileDAO tipoImmobileDAO,
                           AuditService auditService) {
        this.propertyDAO = propertyDAO;
        this.propertyOtaCodeDAO = propertyOtaCodeDAO;
        this.ownerProfileDAO = ownerProfileDAO;
        this.bookingDAO = bookingDAO;
        this.canaleOtaDAO = canaleOtaDAO;
        this.tipoImmobileDAO = tipoImmobileDAO;
        this.auditService = auditService;
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
        log.info("PropertyService.create() - tenantId={}, internalCode={}", tenantId, dto.getInternalCode());
        boolean codeExists = propertyDAO.findByTenantId(tenantId).stream()
                .anyMatch(p -> dto.getInternalCode().equals(p.getInternalCode()));
        if (codeExists) {
            throw new IllegalArgumentException("Codice immobile già esistente");
        }
        Integer tipoId = dto.getFkTipoImmobileId();
        if (tipoId == null && dto.getPropertyType() != null) {
            tipoId = tipoImmobileDAO.findByCodice(dto.getPropertyType())
                    .map(TipoImmobile::getId)
                    .orElse(null);
        }
        if (tipoId == null) {
            tipoId = tipoImmobileDAO.findAll().stream()
                    .findFirst()
                    .map(TipoImmobile::getId)
                    .orElse(null);
        }
        final Integer resolvedTipoId = tipoId;
        // primo_immobile: TRUE se è il primo immobile attivo dell'owner (ritenuta primaria 21%),
        // FALSE dal secondo in poi (ritenuta secondaria 26%). Il PM può poi modificarlo manualmente.
        boolean primoImmobile = dto.getFkOwnerId() != null
                && propertyDAO.countActiveByOwner(dto.getFkOwnerId(), tenantId) == 0;
        Property property = Property.builder()
                .fkTenantId(tenantId)
                .fkOwnerId(dto.getFkOwnerId())
                .fkPmUserId(SecurityUtils.getCurrentUtenteId())
                .fkTipoImmobileId(resolvedTipoId)
                .internalCode(dto.getInternalCode())
                .displayName(dto.getDisplayName())
                .address(dto.getAddress() != null ? dto.getAddress() : "")
                .city(dto.getCity())
                .region(dto.getRegion() != null ? dto.getRegion() : "")
                .cinCode(dto.getCinCode())
                .attivo(true)
                .primoImmobile(primoImmobile)
                .build();
        Property saved = propertyDAO.insert(property);
        if (dto.getOtaCodes() != null && !dto.getOtaCodes().isEmpty()) {
            for (OtaCodeDTO ota : dto.getOtaCodes()) {
                canaleOtaDAO.findByCodice(ota.getCanaleCodiceName()).ifPresent(canale -> {
                    PropertyOtaCode otaCode = PropertyOtaCode.builder()
                            .fkPropertyId(saved.getId())
                            .fkCanaleOtaId(canale.getId())
                            .externalId(ota.getExternalId())
                            .build();
                    propertyOtaCodeDAO.insert(otaCode);
                });
            }
        }
        auditService.log("property.create", "Property", saved.getId(),
                "Creato immobile " + saved.getDisplayName() + " (" + saved.getInternalCode() + ")");
        LookupMaps maps = buildLookupMaps(tenantId);
        return toDetailDTO(saved, maps);
    }

    public PropertyDetailDTO update(Integer tenantId, Integer propertyId, PropertyCreateDTO dto) {
        log.info("PropertyService.update() - id={}", propertyId);
        Property existing = propertyDAO.findById(propertyId)
                .filter(p -> tenantId.equals(p.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Property non trovata: id=" + propertyId));

        // codice interno univoco nel tenant (escludendo l'immobile corrente)
        boolean codeExists = propertyDAO.findByTenantId(tenantId).stream()
                .anyMatch(p -> !p.getId().equals(propertyId) && dto.getInternalCode().equals(p.getInternalCode()));
        if (codeExists) {
            throw new IllegalArgumentException("Codice immobile già esistente");
        }

        // risoluzione tipo immobile (come nel create): id esplicito, poi codice, poi fallback al primo
        Integer tipoId = dto.getFkTipoImmobileId();
        if (tipoId == null && dto.getPropertyType() != null) {
            tipoId = tipoImmobileDAO.findByCodice(dto.getPropertyType())
                    .map(TipoImmobile::getId)
                    .orElse(null);
        }
        if (tipoId == null) {
            tipoId = existing.getFkTipoImmobileId();
        }

        Property toUpdate = Property.builder()
                .id(existing.getId())
                .fkTenantId(tenantId)
                .displayName(dto.getDisplayName())
                .internalCode(dto.getInternalCode())
                .address(dto.getAddress())
                .city(dto.getCity())
                .region(dto.getRegion())
                .cinCode(dto.getCinCode())
                .fkTipoImmobileId(tipoId)
                .build();
        Property updated = propertyDAO.update(toUpdate);

        // codici OTA: cancella e reinserisci quelli non vuoti (stessa logica del create)
        propertyOtaCodeDAO.deleteByPropertyId(propertyId);
        if (dto.getOtaCodes() != null && !dto.getOtaCodes().isEmpty()) {
            for (OtaCodeDTO ota : dto.getOtaCodes()) {
                if (ota.getExternalId() == null || ota.getExternalId().isBlank()) continue;
                canaleOtaDAO.findByCodice(ota.getCanaleCodiceName()).ifPresent(canale -> {
                    PropertyOtaCode otaCode = PropertyOtaCode.builder()
                            .fkPropertyId(propertyId)
                            .fkCanaleOtaId(canale.getId())
                            .externalId(ota.getExternalId())
                            .build();
                    propertyOtaCodeDAO.insert(otaCode);
                });
            }
        }

        auditService.log("property.update", "Property", updated.getId(),
                "Aggiornato immobile " + updated.getDisplayName() + " (" + updated.getInternalCode() + ")");
        LookupMaps maps = buildLookupMaps(tenantId);
        return toDetailDTO(updated, maps);
    }

    public PropertyDetailDTO updateStatus(Integer tenantId, Integer propertyId, Boolean attivo) {
        log.info("PropertyService.updateStatus() - tenantId={}, propertyId={}, attivo={}", tenantId, propertyId, attivo);
        propertyDAO.findById(propertyId)
                .filter(p -> tenantId.equals(p.getFkTenantId()))
                .orElseThrow(() -> new RuntimeException("Property non trovata: id=" + propertyId));
        Property updated = propertyDAO.updateStatus(propertyId, attivo);
        if (Boolean.TRUE.equals(attivo)) {
            auditService.log("property.activate", "Property", updated.getId(),
                    "Immobile " + updated.getDisplayName() + " riattivato");
        } else {
            auditService.log("property.suspend", "Property", updated.getId(),
                    "Immobile " + updated.getDisplayName() + " disattivato");
        }
        LookupMaps maps = buildLookupMaps(tenantId);
        return toDetailDTO(updated, maps);
    }

    public PropertyDetailDTO updateOwner(Integer tenantId, Integer propertyId, Integer fkOwnerId) {
        log.info("PropertyService.updateOwner() - tenantId={}, propertyId={}, fkOwnerId={}", tenantId, propertyId, fkOwnerId);
        propertyDAO.findById(propertyId)
                .filter(p -> tenantId.equals(p.getFkTenantId()))
                .orElseThrow(() -> new RuntimeException("Property non trovata: id=" + propertyId));
        ownerProfileDAO.findById(fkOwnerId)
                .filter(o -> tenantId.equals(o.getFkTenantId()))
                .orElseThrow(() -> new RuntimeException("Owner non trovato: id=" + fkOwnerId));
        Property updated = propertyDAO.updateOwner(propertyId, fkOwnerId);
        auditService.log("property.assign_owner", "Property", updated.getId(),
                "Immobile " + updated.getDisplayName() + " assegnato a owner id=" + fkOwnerId);
        LookupMaps maps = buildLookupMaps(tenantId);
        return toDetailDTO(updated, maps);
    }

    public PropertyDetailDTO updatePrimoImmobile(Integer tenantId, Integer propertyId, Boolean primoImmobile) {
        log.info("PropertyService.updatePrimoImmobile() - tenantId={}, propertyId={}, primoImmobile={}",
                tenantId, propertyId, primoImmobile);
        propertyDAO.findById(propertyId)
                .filter(p -> tenantId.equals(p.getFkTenantId()))
                .orElseThrow(() -> new RuntimeException("Property non trovata: id=" + propertyId));
        Property updated = propertyDAO.updatePrimoImmobile(propertyId, primoImmobile);
        auditService.log("property.primo_immobile", "Property", updated.getId(),
                "Immobile " + updated.getDisplayName()
                        + (Boolean.TRUE.equals(primoImmobile) ? " marcato come primo immobile (ritenuta primaria)"
                                                              : " marcato come secondo+ immobile (ritenuta secondaria)"));
        LookupMaps maps = buildLookupMaps(tenantId);
        return toDetailDTO(updated, maps);
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
                .primoImmobile(p.getPrimoImmobile())
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
