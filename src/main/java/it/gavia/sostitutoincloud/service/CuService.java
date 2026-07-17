package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.CuRecordDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dto.cu.CuDetailDTO;
import it.gavia.sostitutoincloud.dto.cu.CuGeneraBatchResponseDTO;
import it.gavia.sostitutoincloud.dto.cu.CuGeneraRequestDTO;
import it.gavia.sostitutoincloud.dto.cu.CuListDTO;
import it.gavia.sostitutoincloud.model.CuRecord;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Log4j2
public class CuService {

    private static final Set<String> STATI_VALIDI = Set.of("draft", "generated", "sent", "delivered");

    private final CuRecordDAO cuRecordDAO;
    private final OwnerProfileDAO ownerProfileDAO;

    public CuService(CuRecordDAO cuRecordDAO, OwnerProfileDAO ownerProfileDAO) {
        this.cuRecordDAO = cuRecordDAO;
        this.ownerProfileDAO = ownerProfileDAO;
    }

    private String resolveOwnerName(OwnerProfile owner) {
        if (owner == null) return null;
        if (owner.getFirstName() != null && owner.getLastName() != null) {
            return owner.getFirstName() + " " + owner.getLastName();
        }
        return owner.getLegalName();
    }

    public List<CuListDTO> findByTenantId(Integer tenantId, Integer ownerId, Integer taxYear) {
        Map<Integer, OwnerProfile> ownersById = ownerProfileDAO.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(OwnerProfile::getId, o -> o));

        List<CuListDTO> result = cuRecordDAO.findByTenantId(tenantId).stream()
                .filter(c -> ownerId == null || ownerId.equals(c.getFkOwnerId()))
                .filter(c -> taxYear == null || taxYear.equals(c.getTaxYear()))
                .sorted(Comparator.comparing(CuRecord::getTaxYear).reversed())
                .map(c -> {
                    OwnerProfile owner = ownersById.get(c.getFkOwnerId());
                    return CuListDTO.builder()
                            .id(c.getId())
                            .ownerName(resolveOwnerName(owner))
                            .taxYear(c.getTaxYear())
                            .totalCompensi(c.getTotalCompensi())
                            .totalRitenute(c.getTotalRitenute())
                            .stato(c.getStato())
                            .generatedAt(c.getGeneratedAt() != null ? c.getGeneratedAt().toLocalDate() : null)
                            .createdAt(c.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        log.info("CuService.findByTenantId() - tenantId={}, risultati={}", tenantId, result.size());
        return result;
    }

    public Optional<CuDetailDTO> findById(Integer tenantId, Integer cuId) {
        Optional<CuRecord> opt = cuRecordDAO.findById(cuId);
        if (opt.isEmpty() || !tenantId.equals(opt.get().getFkTenantId())) {
            return Optional.empty();
        }
        CuRecord c = opt.get();

        OwnerProfile owner = ownerProfileDAO.findById(c.getFkOwnerId()).orElse(null);

        CuDetailDTO detail = CuDetailDTO.builder()
                .id(c.getId())
                .fkTenantId(c.getFkTenantId())
                .fkOwnerId(c.getFkOwnerId())
                .ownerName(resolveOwnerName(owner))
                .ownerTaxCode(owner != null ? owner.getTaxCode() : null)
                .ownerIban(owner != null ? owner.getIban() : null)
                .taxYear(c.getTaxYear())
                .totalCompensi(c.getTotalCompensi())
                .totalRitenute(c.getTotalRitenute())
                .stato(c.getStato())
                .generatedAt(c.getGeneratedAt() != null ? c.getGeneratedAt().toLocalDate() : null)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();

        log.info("CuService.findById() - tenantId={}, cuId={}", tenantId, cuId);
        return Optional.of(detail);
    }

    public CuListDTO updateStatus(Integer tenantId, Integer cuId, String nuovoStato) {
        if (!STATI_VALIDI.contains(nuovoStato)) {
            throw new IllegalArgumentException("Stato non valido: " + nuovoStato
                    + ". Valori ammessi: " + STATI_VALIDI);
        }
        CuRecord cu = cuRecordDAO.findById(cuId)
                .filter(c -> tenantId.equals(c.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("CU non trovata: id=" + cuId));
        CuRecord updated = cuRecordDAO.updateStato(cu.getId(), nuovoStato);
        log.info("CuService.updateStatus() - id={} stato={}", cuId, nuovoStato);
        return toListDTO(updated, ownerProfileDAO.findById(updated.getFkOwnerId()).orElse(null));
    }

    // ── generazione CU ──────────────────────────────────────────────────────

    /** Genera (o rigenera) la CU di un singolo proprietario per l'anno fiscale. */
    private CuListDTO generaPerOwner(Integer tenantId, Integer ownerId, Integer taxYear) {
        Map<String, Object> agg = cuRecordDAO.aggregateByOwnerYear(tenantId, ownerId, taxYear);
        BigDecimal totalCompensi = toBigDecimal(agg.get("total_compensi"));
        BigDecimal totalImponibile = toBigDecimal(agg.get("total_imponibile"));
        BigDecimal totalRitenute = toBigDecimal(agg.get("total_ritenute"));

        Optional<CuRecord> esistente = cuRecordDAO.findByTenantIdAndOwnerId(tenantId, ownerId).stream()
                .filter(c -> taxYear.equals(c.getTaxYear()))
                .findFirst();

        CuRecord cu;
        if (esistente.isPresent()) {
            String stato = esistente.get().getStato();
            if ("delivered".equals(stato) || "sent".equals(stato)) {
                throw new IllegalStateException("CU già " + stato);
            }
            // draft o generated → rigenera
            cu = cuRecordDAO.updateTotaliAndStato(esistente.get().getId(),
                    totalCompensi, totalImponibile, totalRitenute, "generated");
        } else {
            cu = cuRecordDAO.insert(CuRecord.builder()
                    .fkTenantId(tenantId)
                    .fkOwnerId(ownerId)
                    .taxYear(taxYear)
                    .totalCompensi(totalCompensi)
                    .totalImponibile(totalImponibile)
                    .totalRitenute(totalRitenute)
                    .stato("generated")
                    .build());
        }

        OwnerProfile owner = ownerProfileDAO.findById(ownerId).orElse(null);
        log.info("CuService.generaPerOwner() - tenantId={} ownerId={} year={}", tenantId, ownerId, taxYear);
        return toListDTO(cu, owner);
    }

    public CuListDTO genera(Integer tenantId, CuGeneraRequestDTO req) {
        if (req.getTaxYear() == null) {
            throw new IllegalArgumentException("taxYear obbligatorio");
        }
        log.info("CuService.genera() - tenantId={} ownerId={} year={}", tenantId, req.getOwnerId(), req.getTaxYear());
        return generaPerOwner(tenantId, req.getOwnerId(), req.getTaxYear());
    }

    public CuGeneraBatchResponseDTO generaBatch(Integer tenantId, Integer taxYear) {
        if (taxYear == null) {
            throw new IllegalArgumentException("taxYear obbligatorio");
        }
        List<Integer> ownerIds = cuRecordDAO.findOwnerIdsWithWithholding(tenantId, taxYear);
        List<CuListDTO> records = new ArrayList<>();
        int generated = 0, skipped = 0;
        for (Integer ownerId : ownerIds) {
            try {
                records.add(generaPerOwner(tenantId, ownerId, taxYear));
                generated++;
            } catch (IllegalStateException e) {
                skipped++;
                log.warn("CuService.generaBatch() - owner {} saltato: {}", ownerId, e.getMessage());
            }
        }
        log.info("CuService.generaBatch() - tenantId={} year={} generated={} skipped={}",
                tenantId, taxYear, generated, skipped);
        return CuGeneraBatchResponseDTO.builder()
                .generated(generated)
                .skipped(skipped)
                .records(records)
                .build();
    }

    private CuListDTO toListDTO(CuRecord c, OwnerProfile owner) {
        return CuListDTO.builder()
                .id(c.getId())
                .ownerName(resolveOwnerName(owner))
                .taxYear(c.getTaxYear())
                .totalCompensi(c.getTotalCompensi())
                .totalRitenute(c.getTotalRitenute())
                .stato(c.getStato())
                .generatedAt(c.getGeneratedAt() != null ? c.getGeneratedAt().toLocalDate() : null)
                .createdAt(c.getCreatedAt())
                .build();
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }
}
