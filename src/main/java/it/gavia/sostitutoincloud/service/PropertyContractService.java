package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.CanaleOtaDAO;
import it.gavia.sostitutoincloud.dao.PropertyContractRuleDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dto.property.PropertyContractRuleCreateDTO;
import it.gavia.sostitutoincloud.dto.property.PropertyContractRuleDTO;
import it.gavia.sostitutoincloud.model.CanaleOta;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.PropertyContractRule;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Log4j2
public class PropertyContractService {

    private static final Set<String> TIPI_VALIDI = Set.of(
            "pulizie", "commissione_ota", "cambio_biancheria",
            "commissione_pm", "provvigione_proprietario");

    private static final Set<String> CALC_MODE_VALIDI = Set.of(
            "fisso", "percentuale", "fisso_per_notte",
            "fisso_per_persona", "percentuale_lordo", "rimanenza");

    private static final Set<String> TIPI_RIMANENZA_AMMESSA = Set.of(
            "commissione_pm", "provvigione_proprietario");

    private static final Map<String, String> TIPO_LABELS = Map.of(
            "pulizie", "Pulizie Abitazione",
            "commissione_ota", "Commissione OTA",
            "cambio_biancheria", "Cambio Biancheria",
            "commissione_pm", "Commissione PM",
            "provvigione_proprietario", "Provvigione Proprietario");

    private static final Map<String, String> CALC_MODE_LABELS = Map.of(
            "fisso", "Importo Fisso (€)",
            "percentuale", "Percentuale (%)",
            "fisso_per_notte", "Fisso per Notte (€)",
            "fisso_per_persona", "Fisso per Persona (€)",
            "percentuale_lordo", "Percentuale sul Lordo (%)",
            "rimanenza", "Rimanenza automatica");

    private final PropertyContractRuleDAO contractRuleDAO;
    private final CanaleOtaDAO canaleOtaDAO;
    private final PropertyDAO propertyDAO;
    private final AuditService auditService;

    public PropertyContractService(PropertyContractRuleDAO contractRuleDAO,
                                   CanaleOtaDAO canaleOtaDAO,
                                   PropertyDAO propertyDAO,
                                   AuditService auditService) {
        this.contractRuleDAO = contractRuleDAO;
        this.canaleOtaDAO = canaleOtaDAO;
        this.propertyDAO = propertyDAO;
        this.auditService = auditService;
    }

    public List<PropertyContractRuleDTO> findByPropertyId(Integer tenantId, Integer propertyId) {
        log.info("PropertyContractService.findByPropertyId() - tenantId={}, propertyId={}", tenantId, propertyId);
        verifyPropertyBelongsToTenant(tenantId, propertyId);
        return contractRuleDAO.findByPropertyId(propertyId).stream()
                .map(this::toDTO)
                .toList();
    }

    public PropertyContractRuleDTO create(Integer tenantId, PropertyContractRuleCreateDTO dto) {
        log.info("PropertyContractService.create() - tenantId={}, propertyId={}, tipo={}",
                tenantId, dto.getFkPropertyId(), dto.getTipo());
        verifyPropertyBelongsToTenant(tenantId, dto.getFkPropertyId());
        validate(dto, null);

        PropertyContractRule rule = PropertyContractRule.builder()
                .fkPropertyId(dto.getFkPropertyId())
                .fkTenantId(tenantId)
                .fkCanaleOtaId(dto.getFkCanaleOtaId())
                .tipo(dto.getTipo())
                .calcMode(dto.getCalcMode())
                .valore(dto.getValore() != null ? dto.getValore() : BigDecimal.ZERO)
                .isRemainder(Boolean.TRUE.equals(dto.getIsRemainder()))
                .ordine(dto.getOrdine() != null ? dto.getOrdine() : 0)
                .attivo(true)
                .build();
        PropertyContractRule saved = contractRuleDAO.insert(rule);

        auditService.log("contract.create", "PropertyContractRule", saved.getId(),
                "Aggiunta regola " + saved.getTipo() + " a immobile " + saved.getFkPropertyId());
        return toDTO(saved);
    }

    public PropertyContractRuleDTO update(Integer tenantId, Integer ruleId, PropertyContractRuleCreateDTO dto) {
        log.info("PropertyContractService.update() - tenantId={}, ruleId={}", tenantId, ruleId);
        PropertyContractRule existing = contractRuleDAO.findById(ruleId);
        if (!tenantId.equals(existing.getFkTenantId())) {
            throw new RuntimeException("Regola contratto non appartiene al tenant: id=" + ruleId);
        }
        verifyPropertyBelongsToTenant(tenantId, existing.getFkPropertyId());
        validate(dto, ruleId);

        existing.setTipo(dto.getTipo());
        existing.setCalcMode(dto.getCalcMode());
        existing.setValore(dto.getValore() != null ? dto.getValore() : BigDecimal.ZERO);
        existing.setIsRemainder(Boolean.TRUE.equals(dto.getIsRemainder()));
        existing.setFkCanaleOtaId(dto.getFkCanaleOtaId());
        existing.setOrdine(dto.getOrdine() != null ? dto.getOrdine() : 0);
        PropertyContractRule saved = contractRuleDAO.update(existing);

        auditService.log("contract.update", "PropertyContractRule", saved.getId(),
                "Modificata regola " + saved.getTipo() + " immobile " + saved.getFkPropertyId());
        return toDTO(saved);
    }

    public void delete(Integer tenantId, Integer ruleId) {
        log.info("PropertyContractService.delete() - tenantId={}, ruleId={}", tenantId, ruleId);
        PropertyContractRule existing = contractRuleDAO.findById(ruleId);
        if (!tenantId.equals(existing.getFkTenantId())) {
            throw new RuntimeException("Regola contratto non appartiene al tenant: id=" + ruleId);
        }
        contractRuleDAO.delete(ruleId);
        auditService.log("contract.delete", "PropertyContractRule", ruleId,
                "Eliminata regola id=" + ruleId + " immobile " + existing.getFkPropertyId());
    }

    // ── validazione e helper ──────────────────────────────────────────────

    private void validate(PropertyContractRuleCreateDTO dto, Integer currentRuleId) {
        if (dto.getTipo() == null || !TIPI_VALIDI.contains(dto.getTipo())) {
            throw new IllegalArgumentException("Tipo regola non valido: " + dto.getTipo());
        }
        if (dto.getCalcMode() == null || !CALC_MODE_VALIDI.contains(dto.getCalcMode())) {
            throw new IllegalArgumentException("Modalità di calcolo non valida: " + dto.getCalcMode());
        }
        boolean isRemainder = Boolean.TRUE.equals(dto.getIsRemainder()) || "rimanenza".equals(dto.getCalcMode());

        // commissione_ota richiede un canale OTA
        if ("commissione_ota".equals(dto.getTipo()) && dto.getFkCanaleOtaId() == null) {
            throw new IllegalArgumentException("La commissione OTA richiede un canale OTA");
        }

        // rimanenza solo per commissione_pm o provvigione_proprietario
        if (isRemainder && !TIPI_RIMANENZA_AMMESSA.contains(dto.getTipo())) {
            throw new IllegalArgumentException(
                    "La rimanenza è ammessa solo per Commissione PM o Provvigione Proprietario");
        }

        // una sola regola rimanenza per immobile
        if (isRemainder) {
            boolean esisteAltra = contractRuleDAO.findByPropertyId(dto.getFkPropertyId()).stream()
                    .filter(r -> currentRuleId == null || !currentRuleId.equals(r.getId()))
                    .anyMatch(r -> Boolean.TRUE.equals(r.getIsRemainder()));
            if (esisteAltra) {
                throw new IllegalArgumentException("Esiste già una voce rimanenza");
            }
        }
    }

    private void verifyPropertyBelongsToTenant(Integer tenantId, Integer propertyId) {
        Property property = propertyDAO.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property non trovata: id=" + propertyId));
        if (!tenantId.equals(property.getFkTenantId())) {
            throw new RuntimeException("Property non appartiene al tenant: id=" + propertyId);
        }
    }

    private PropertyContractRuleDTO toDTO(PropertyContractRule rule) {
        String canaleName = null;
        if (rule.getFkCanaleOtaId() != null) {
            canaleName = canaleOtaDAO.findById(rule.getFkCanaleOtaId())
                    .map(CanaleOta::getNome)
                    .orElse(null);
        }
        return PropertyContractRuleDTO.builder()
                .id(rule.getId())
                .fkPropertyId(rule.getFkPropertyId())
                .fkCanaleOtaId(rule.getFkCanaleOtaId())
                .canaleName(canaleName)
                .tipo(rule.getTipo())
                .tipoLabel(TIPO_LABELS.getOrDefault(rule.getTipo(), rule.getTipo()))
                .calcMode(rule.getCalcMode())
                .calcModeLabel(CALC_MODE_LABELS.getOrDefault(rule.getCalcMode(), rule.getCalcMode()))
                .valore(rule.getValore())
                .isRemainder(rule.getIsRemainder())
                .ordine(rule.getOrdine())
                .build();
    }
}
