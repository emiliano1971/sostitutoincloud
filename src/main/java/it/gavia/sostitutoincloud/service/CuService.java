package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.CuRecordDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dto.cu.CuDetailDTO;
import it.gavia.sostitutoincloud.dto.cu.CuListDTO;
import it.gavia.sostitutoincloud.model.CuRecord;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
        Optional<CuRecord> opt = cuRecordDAO.findById(cuId);
        if (opt.isEmpty() || !tenantId.equals(opt.get().getFkTenantId())) {
            throw new RuntimeException("CU non trovata: id=" + cuId);
        }
        if (!STATI_VALIDI.contains(nuovoStato)) {
            throw new IllegalArgumentException("Stato non valido: " + nuovoStato
                    + ". Valori ammessi: " + STATI_VALIDI);
        }
        log.warn("CuService.updateStatus() - operazione non implementata, tenantId={}, cuId={}", tenantId, cuId);
        throw new UnsupportedOperationException("updateStatus non ancora implementato");
    }
}
