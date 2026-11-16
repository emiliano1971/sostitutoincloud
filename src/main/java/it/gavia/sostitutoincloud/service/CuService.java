package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.CuRecordDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dto.cu.CuDetailDTO;
import it.gavia.sostitutoincloud.dto.cu.CuEliminataDTO;
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
    /** Stati in cui una CU orfana (senza ritenute nel registro) si può eliminare. */
    private static final Set<String> STATI_ELIMINABILI = Set.of("draft", "generated");

    private final CuRecordDAO cuRecordDAO;
    private final OwnerProfileDAO ownerProfileDAO;
    private final AuditService auditService;

    public CuService(CuRecordDAO cuRecordDAO, OwnerProfileDAO ownerProfileDAO, AuditService auditService) {
        this.cuRecordDAO = cuRecordDAO;
        this.ownerProfileDAO = ownerProfileDAO;
        this.auditService = auditService;
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
                            .totalImponibile(c.getTotalImponibile())
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
                .totalImponibile(c.getTotalImponibile())
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

    /**
     * Generazione singola su una CU orfana: se per owner e anno non restano ritenute valide e la
     * CU è draft/generated, la elimina (audit cu.delete) invece di fallire sull'aggregazione.
     * Vuoto se ci sono ritenute, se la CU non esiste o se è sent/delivered: in quei casi si
     * prosegue con {@link #genera}, che risponde come prima (rigenerazione o errore).
     */
    public Optional<CuEliminataDTO> eliminaSeOrfana(Integer tenantId, Integer ownerId, Integer taxYear) {
        if (taxYear == null) {
            throw new IllegalArgumentException("taxYear obbligatorio");
        }
        if (cuRecordDAO.countRitenuteValide(tenantId, ownerId, taxYear) > 0) {
            return Optional.empty();
        }
        return cuRecordDAO.findByTenantOwnerYear(tenantId, ownerId, taxYear)
                .filter(cu -> STATI_ELIMINABILI.contains(cu.getStato()))
                .map(cu -> {
                    String ownerName = eliminaOrfana(cu);
                    return CuEliminataDTO.builder()
                            .eliminata(true)
                            .cuId(cu.getId())
                            .ownerName(ownerName)
                            .taxYear(taxYear)
                            .message("CU " + taxYear + " di " + ownerName
                                    + " eliminata: non restano ritenute nell'anno")
                            .build();
                });
    }

    /** Elimina una CU orfana draft/generated con audit cu.delete; restituisce il nome del proprietario. */
    private String eliminaOrfana(CuRecord cu) {
        OwnerProfile owner = ownerProfileDAO.findById(cu.getFkOwnerId()).orElse(null);
        String ownerName = owner != null ? resolveOwnerName(owner) : "owner id=" + cu.getFkOwnerId();
        cuRecordDAO.deleteById(cu.getId());
        auditService.log("cu.delete", "CuRecord", cu.getId(),
                "CU " + cu.getTaxYear() + " di " + ownerName + " (owner id=" + cu.getFkOwnerId()
                        + ", stato " + cu.getStato() + ") eliminata: nessuna ritenuta rimasta nel registro. "
                        + "Importi che aveva: compensi € " + cu.getTotalCompensi()
                        + ", imponibile € " + cu.getTotalImponibile()
                        + ", ritenute € " + cu.getTotalRitenute() + ".");
        log.info("CuService - CU orfana eliminata id={} owner={} anno={}", cu.getId(), cu.getFkOwnerId(), cu.getTaxYear());
        return ownerName;
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
        // CU orfane: proprietari senza più ritenute valide nell'anno (es. ricevuta eliminata).
        // Non compaiono in findOwnerIdsWithWithholding, quindi il giro sopra non le tocca.
        int eliminated = 0;
        for (CuRecord orfana : cuRecordDAO.findOrfane(tenantId, taxYear)) {
            if (STATI_ELIMINABILI.contains(orfana.getStato())) {
                eliminaOrfana(orfana);
                eliminated++;
            } else {
                skipped++;
                log.warn("CuService.generaBatch() - CU {} (owner {}) senza ritenute ma già {}: non eliminata",
                        orfana.getId(), orfana.getFkOwnerId(), orfana.getStato());
            }
        }
        log.info("CuService.generaBatch() - tenantId={} year={} generated={} skipped={} eliminated={}",
                tenantId, taxYear, generated, skipped, eliminated);
        return CuGeneraBatchResponseDTO.builder()
                .generated(generated)
                .skipped(skipped)
                .eliminated(eliminated)
                .records(records)
                .build();
    }

    private CuListDTO toListDTO(CuRecord c, OwnerProfile owner) {
        return CuListDTO.builder()
                .id(c.getId())
                .ownerName(resolveOwnerName(owner))
                .taxYear(c.getTaxYear())
                .totalCompensi(c.getTotalCompensi())
                .totalImponibile(c.getTotalImponibile())
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
