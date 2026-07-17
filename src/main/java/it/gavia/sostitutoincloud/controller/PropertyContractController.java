package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dto.property.PropertyContractRuleCreateDTO;
import it.gavia.sostitutoincloud.dto.property.PropertyContractRuleDTO;
import it.gavia.sostitutoincloud.service.PropertyContractService;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/properties/{propertyId}/contracts")
@Log4j2
public class PropertyContractController {

    private final PropertyContractService propertyContractService;

    public PropertyContractController(PropertyContractService propertyContractService) {
        this.propertyContractService = propertyContractService;
    }

    @GetMapping
    public ResponseEntity<List<PropertyContractRuleDTO>> findAll(@PathVariable Integer propertyId) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(propertyContractService.findByPropertyId(tenantId, propertyId));
    }

    @PostMapping
    public ResponseEntity<PropertyContractRuleDTO> create(
            @PathVariable Integer propertyId,
            @RequestBody PropertyContractRuleCreateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        dto.setFkPropertyId(propertyId);
        PropertyContractRuleDTO created = propertyContractService.create(tenantId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PropertyContractRuleDTO> update(
            @PathVariable Integer propertyId,
            @PathVariable Integer id,
            @RequestBody PropertyContractRuleCreateDTO dto) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        dto.setFkPropertyId(propertyId);
        PropertyContractRuleDTO updated = propertyContractService.update(tenantId, id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Integer propertyId,
            @PathVariable Integer id) {
        Integer tenantId = SecurityUtils.getCurrentTenantId();
        propertyContractService.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }
}
