package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.property.PropertyCreateDTO;
import it.gavia.sostitutoincloud.dto.property.PropertyDetailDTO;
import it.gavia.sostitutoincloud.dto.property.PropertyListDTO;
import it.gavia.sostitutoincloud.dto.property.PropertyPrimoImmobileUpdateDTO;
import it.gavia.sostitutoincloud.dto.property.PropertyStatusUpdateDTO;
import it.gavia.sostitutoincloud.dto.property.PropertyUpdateOwnerDTO;
import it.gavia.sostitutoincloud.service.PropertyService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/properties")
@Log4j2
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping
    public ResponseEntity<List<PropertyListDTO>> findAll(
            @RequestParam(required = false) Boolean attivo,
            @RequestParam(required = false) Integer ownerId) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        if (ownerId != null) {
            return ResponseEntity.ok(propertyService.findByOwner(tenantId, ownerId, attivo));
        }
        if (attivo != null) {
            return ResponseEntity.ok(propertyService.findByTenantIdAndAttivo(tenantId, attivo));
        }
        return ResponseEntity.ok(propertyService.findByTenantId(tenantId));
    }

    /**
     * Segnala se il proprietario ha già un altro immobile attivo censito come primo immobile.
     * excludePropertyId esclude l'immobile in corso di modifica. Avviso informativo per il
     * frontend: non blocca la creazione né il salvataggio.
     */
    @GetMapping("/check-primo-immobile")
    public ResponseEntity<Map<String, Object>> checkPrimoImmobile(
            @RequestParam Integer ownerId,
            @RequestParam(required = false) Integer excludePropertyId) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        log.debug("PropertyController.checkPrimoImmobile() - tenantId={} ownerId={} excludePropertyId={}",
                tenantId, ownerId, excludePropertyId);
        return propertyService.checkPrimoImmobile(tenantId, ownerId, excludePropertyId)
                .map(p -> ResponseEntity.ok(Map.<String, Object>of(
                        "exists", true,
                        "propertyName", p.getDisplayName(),
                        "propertyId", p.getId())))
                .orElseGet(() -> ResponseEntity.ok(Map.of("exists", false)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PropertyDetailDTO> findById(@PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return propertyService.findById(tenantId, id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Property non trovata: id=" + id));
    }

    @PostMapping
    public ResponseEntity<PropertyDetailDTO> create(@RequestBody PropertyCreateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        PropertyDetailDTO created = propertyService.create(tenantId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody PropertyCreateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        try {
            return ResponseEntity.ok(propertyService.update(tenantId, id, dto));
        } catch (java.util.NoSuchElementException e) {
            log.warn("PropertyController.update() - not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("PropertyController.update() - bad request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PropertyDetailDTO> updateStatus(
            @PathVariable Integer id,
            @RequestBody PropertyStatusUpdateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        PropertyDetailDTO updated = propertyService.updateStatus(tenantId, id, dto.getAttivo());
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PropertyDetailDTO> updatePrimoImmobile(
            @PathVariable Integer id,
            @RequestBody PropertyPrimoImmobileUpdateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        PropertyDetailDTO updated = propertyService.updatePrimoImmobile(tenantId, id, dto.getPrimoImmobile());
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/owner")
    public ResponseEntity<PropertyDetailDTO> updateOwner(
            @PathVariable Integer id,
            @RequestBody PropertyUpdateOwnerDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        PropertyDetailDTO updated = propertyService.updateOwner(tenantId, id, dto.getFkOwnerId());
        return ResponseEntity.ok(updated);
    }
}
