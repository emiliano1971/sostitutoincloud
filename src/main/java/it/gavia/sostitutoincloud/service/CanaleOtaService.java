package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.CanaleOtaDAO;
import it.gavia.sostitutoincloud.dto.ota.CanaleOtaCreateDTO;
import it.gavia.sostitutoincloud.dto.ota.CanaleOtaDTO;
import it.gavia.sostitutoincloud.dto.ota.CanaleOtaUpdateDTO;
import it.gavia.sostitutoincloud.model.CanaleOta;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@Log4j2
public class CanaleOtaService {

    private final CanaleOtaDAO canaleOtaDAO;
    private final AuditService auditService;

    public CanaleOtaService(CanaleOtaDAO canaleOtaDAO, AuditService auditService) {
        this.canaleOtaDAO = canaleOtaDAO;
        this.auditService = auditService;
    }

    public List<CanaleOtaDTO> findAll() {
        log.debug("CanaleOtaService.findAll()");
        return canaleOtaDAO.findAll().stream().map(this::toDTO).toList();
    }

    public CanaleOtaDTO findById(Integer id) {
        return canaleOtaDAO.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Canale OTA non trovato: id=" + id));
    }

    public CanaleOtaDTO create(CanaleOtaCreateDTO dto) {
        log.info("CanaleOtaService.create() - codice={}", dto.getCodice());
        canaleOtaDAO.findByCodice(dto.getCodice()).ifPresent(c -> {
            throw new IllegalArgumentException("Canale OTA con codice '" + dto.getCodice() + "' già esistente");
        });
        CanaleOta canale = CanaleOta.builder()
                .codice(dto.getCodice())
                .nome(dto.getNome())
                .commissioneDefaultPct(dto.getCommissioneDefaultPct() != null
                        ? dto.getCommissioneDefaultPct() : BigDecimal.ZERO)
                .tassaSoggiornoInclusa(Boolean.TRUE.equals(dto.getTouristTaxIncluded()))
                .touristTaxCollection(dto.getTouristTaxCollection() != null
                        ? dto.getTouristTaxCollection() : "contanti")
                .attivo(true)
                .build();
        CanaleOta saved = canaleOtaDAO.insert(canale);
        auditService.log("ota.create", "CanaleOta", saved.getId(),
                "Creato canale OTA " + saved.getNome());
        return toDTO(saved);
    }

    public CanaleOtaDTO update(Integer id, CanaleOtaUpdateDTO dto) {
        log.info("CanaleOtaService.update() - id={}", id);
        CanaleOta existing = canaleOtaDAO.findById(id)
                .orElseThrow(() -> new RuntimeException("Canale OTA non trovato: id=" + id));
        CanaleOta toUpdate = CanaleOta.builder()
                .id(existing.getId())
                .codice(existing.getCodice())
                .nome(dto.getNome() != null ? dto.getNome() : existing.getNome())
                .commissioneDefaultPct(dto.getCommissioneDefaultPct() != null
                        ? dto.getCommissioneDefaultPct() : existing.getCommissioneDefaultPct())
                .tassaSoggiornoInclusa(dto.getTouristTaxIncluded() != null
                        ? dto.getTouristTaxIncluded() : existing.getTassaSoggiornoInclusa())
                .touristTaxCollection(dto.getTouristTaxCollection() != null
                        ? dto.getTouristTaxCollection() : existing.getTouristTaxCollection())
                .attivo(dto.getAttivo() != null ? dto.getAttivo() : existing.getAttivo())
                .build();
        CanaleOta updated = canaleOtaDAO.update(toUpdate);
        auditService.log("ota.update", "CanaleOta", updated.getId(),
                "Aggiornato canale OTA " + updated.getNome());
        return toDTO(updated);
    }

    public CanaleOtaDTO updateStatus(Integer id, Boolean attivo) {
        log.info("CanaleOtaService.updateStatus() - id={} attivo={}", id, attivo);
        canaleOtaDAO.findById(id)
                .orElseThrow(() -> new RuntimeException("Canale OTA non trovato: id=" + id));
        return toDTO(canaleOtaDAO.updateStatus(id, attivo));
    }

    private CanaleOtaDTO toDTO(CanaleOta c) {
        return CanaleOtaDTO.builder()
                .id(c.getId())
                .codice(c.getCodice())
                .nome(c.getNome())
                .commissioneDefaultPct(c.getCommissioneDefaultPct())
                .touristTaxIncluded(c.getTassaSoggiornoInclusa())
                .touristTaxCollection(c.getTouristTaxCollection())
                .attivo(c.getAttivo())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
