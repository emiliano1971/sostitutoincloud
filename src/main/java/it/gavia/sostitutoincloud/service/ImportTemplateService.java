package it.gavia.sostitutoincloud.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gavia.sostitutoincloud.dao.ImportTemplateDAO;
import it.gavia.sostitutoincloud.dto.importing.ImportTemplateDTO;
import it.gavia.sostitutoincloud.dto.importing.ImportTemplateSaveDTO;
import it.gavia.sostitutoincloud.model.ImportTemplate;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@Log4j2
public class ImportTemplateService {

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final ImportTemplateDAO importTemplateDAO;
    private final ObjectMapper objectMapper;

    public ImportTemplateService(ImportTemplateDAO importTemplateDAO, ObjectMapper objectMapper) {
        this.importTemplateDAO = importTemplateDAO;
        this.objectMapper = objectMapper;
    }

    public List<ImportTemplateDTO> findByTenant(Integer tenantId) {
        log.debug("ImportTemplateService.findByTenant() - tenantId={}", tenantId);
        return importTemplateDAO.findByTenantId(tenantId).stream()
                .map(this::toDTO)
                .toList();
    }

    public ImportTemplateDTO save(Integer tenantId, ImportTemplateSaveDTO dto) {
        if (dto.getNome() == null || dto.getNome().isBlank()) {
            throw new IllegalArgumentException("Nome template obbligatorio");
        }
        log.info("ImportTemplateService.save() - tenantId={} nome={}", tenantId, dto.getNome());

        ImportTemplate entity = ImportTemplate.builder()
                .id(dto.getId())
                .fkTenantId(tenantId)
                .nome(dto.getNome().trim())
                .descrizione(dto.getDescrizione())
                .headerRow(dto.getHeaderRow() != null ? dto.getHeaderRow() : 0)
                .bookingMapping(writeJson(dto.getBookingMapping()))
                .guestMapping(writeJson(dto.getGuestMapping()))
                .build();

        if (dto.getId() != null) {
            // update: verifica appartenenza al tenant
            ImportTemplate existing = importTemplateDAO.findById(dto.getId())
                    .filter(t -> tenantId.equals(t.getFkTenantId()))
                    .orElseThrow(() -> new NoSuchElementException("Template non trovato: id=" + dto.getId()));
            // se il nome cambia, verifica che non collida con un altro template
            boolean nameConflict = importTemplateDAO.findByTenantId(tenantId).stream()
                    .anyMatch(t -> !t.getId().equals(existing.getId())
                            && t.getNome().equalsIgnoreCase(entity.getNome()));
            if (nameConflict) {
                throw new IllegalArgumentException("Nome template già esistente: " + entity.getNome());
            }
            return toDTO(importTemplateDAO.update(entity));
        }

        // insert: verifica unicità nome per tenant
        boolean exists = importTemplateDAO.findByTenantId(tenantId).stream()
                .anyMatch(t -> t.getNome().equalsIgnoreCase(entity.getNome()));
        if (exists) {
            throw new IllegalArgumentException("Nome template già esistente: " + entity.getNome());
        }
        return toDTO(importTemplateDAO.insert(entity));
    }

    public void delete(Integer tenantId, Integer id) {
        ImportTemplate existing = importTemplateDAO.findById(id)
                .filter(t -> tenantId.equals(t.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Template non trovato: id=" + id));
        importTemplateDAO.delete(existing.getId(), tenantId);
        log.info("ImportTemplateService.delete() - tenantId={} id={}", tenantId, id);
    }

    // ── mapping / (de)serializzazione JSONB ──────────────────────────────────

    private ImportTemplateDTO toDTO(ImportTemplate t) {
        return ImportTemplateDTO.builder()
                .id(t.getId())
                .nome(t.getNome())
                .descrizione(t.getDescrizione())
                .headerRow(t.getHeaderRow())
                .bookingMapping(readJson(t.getBookingMapping()))
                .guestMapping(readJson(t.getGuestMapping()))
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }

    private Map<String, String> readJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("ImportTemplateService.readJson() - JSONB non deserializzabile: {}", e.getMessage());
            return Map.of();
        }
    }

    private String writeJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map != null ? map : Map.of());
        } catch (Exception e) {
            throw new IllegalArgumentException("Mapping non serializzabile: " + e.getMessage());
        }
    }
}
