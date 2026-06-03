package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.property.PropertyCreateDTO;
import it.gavia.sostitutoincloud.dto.property.PropertyDetailDTO;
import it.gavia.sostitutoincloud.dto.property.PropertyListDTO;
import it.gavia.sostitutoincloud.dto.property.PropertyStatusUpdateDTO;
import it.gavia.sostitutoincloud.dto.property.PropertyUpdateOwnerDTO;
import it.gavia.sostitutoincloud.service.PropertyService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @RequestParam(required = false) Boolean attivo) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        if (attivo != null) {
            return ResponseEntity.ok(propertyService.findByTenantIdAndAttivo(tenantId, attivo));
        }
        return ResponseEntity.ok(propertyService.findByTenantId(tenantId));
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

    @PatchMapping("/{id}/status")
    public ResponseEntity<PropertyDetailDTO> updateStatus(
            @PathVariable Integer id,
            @RequestBody PropertyStatusUpdateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        PropertyDetailDTO updated = propertyService.updateStatus(tenantId, id, dto.getAttivo());
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
