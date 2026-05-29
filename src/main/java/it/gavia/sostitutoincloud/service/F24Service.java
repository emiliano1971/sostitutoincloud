package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.CodiceTributoDAO;
import it.gavia.sostitutoincloud.dao.F24RecordDAO;
import it.gavia.sostitutoincloud.dao.TenantDAO;
import it.gavia.sostitutoincloud.dto.f24.F24DetailDTO;
import it.gavia.sostitutoincloud.dto.f24.F24ListDTO;
import it.gavia.sostitutoincloud.model.CodiceTributo;
import it.gavia.sostitutoincloud.model.F24Record;
import it.gavia.sostitutoincloud.model.Tenant;
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
public class F24Service {

    private static final Set<String> STATI_VALIDI = Set.of("draft", "ready", "sent", "paid", "error");

    private final F24RecordDAO f24RecordDAO;
    private final CodiceTributoDAO codiceTributoDAO;
    private final TenantDAO tenantDAO;

    public F24Service(F24RecordDAO f24RecordDAO,
                       CodiceTributoDAO codiceTributoDAO,
                       TenantDAO tenantDAO) {
        this.f24RecordDAO = f24RecordDAO;
        this.codiceTributoDAO = codiceTributoDAO;
        this.tenantDAO = tenantDAO;
    }

    public List<F24ListDTO> findByTenantId(Integer tenantId) {
        Map<Integer, String> codiciById = codiceTributoDAO.findAll().stream()
                .collect(Collectors.toMap(CodiceTributo::getId, CodiceTributo::getCodice));

        List<F24ListDTO> result = f24RecordDAO.findByTenantId(tenantId).stream()
                .sorted(Comparator.comparing(F24Record::getPeriod).reversed())
                .map(f -> F24ListDTO.builder()
                        .id(f.getId())
                        .period(f.getPeriod())
                        .codiceTributo(codiciById.get(f.getFkCodiceTributoId()))
                        .totalAmount(f.getTotalAmount())
                        .withholdingsCount(f.getWithholdingsCount())
                        .stato(f.getStato())
                        .deadlineDate(f.getDeadlineDate())
                        .paymentDate(f.getPaymentDate())
                        .createdAt(f.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        log.info("F24Service.findByTenantId() - tenantId={}, risultati={}", tenantId, result.size());
        return result;
    }

    public Optional<F24DetailDTO> findById(Integer tenantId, Integer f24Id) {
        Optional<F24Record> opt = f24RecordDAO.findById(f24Id);
        if (opt.isEmpty() || !tenantId.equals(opt.get().getFkTenantId())) {
            return Optional.empty();
        }
        F24Record f = opt.get();

        Map<Integer, String> codiciById = codiceTributoDAO.findAll().stream()
                .collect(Collectors.toMap(CodiceTributo::getId, CodiceTributo::getCodice));

        Tenant tenant = tenantDAO.findById(tenantId).orElse(null);

        F24DetailDTO detail = F24DetailDTO.builder()
                .id(f.getId())
                .fkTenantId(f.getFkTenantId())
                .tenantLegalName(tenant != null ? tenant.getLegalName() : null)
                .tenantTaxCode(tenant != null ? tenant.getTaxCode() : null)
                .tenantAddress(tenant != null ? tenant.getLegalAddress() : null)
                .period(f.getPeriod())
                .codiceTributo(codiciById.get(f.getFkCodiceTributoId()))
                .totalAmount(f.getTotalAmount())
                .withholdingsCount(f.getWithholdingsCount())
                .stato(f.getStato())
                .deadlineDate(f.getDeadlineDate())
                .paymentDate(f.getPaymentDate())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();

        log.info("F24Service.findById() - tenantId={}, f24Id={}", tenantId, f24Id);
        return Optional.of(detail);
    }

    public F24ListDTO updateStatus(Integer tenantId, Integer f24Id, String nuovoStato) {
        Optional<F24Record> opt = f24RecordDAO.findById(f24Id);
        if (opt.isEmpty() || !tenantId.equals(opt.get().getFkTenantId())) {
            throw new RuntimeException("F24 non trovato: id=" + f24Id);
        }
        if (!STATI_VALIDI.contains(nuovoStato)) {
            throw new IllegalArgumentException("Stato non valido: " + nuovoStato
                    + ". Valori ammessi: " + STATI_VALIDI);
        }
        log.warn("F24Service.updateStatus() - operazione non implementata, tenantId={}, f24Id={}", tenantId, f24Id);
        throw new UnsupportedOperationException("updateStatus non ancora implementato");
    }
}
