package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.RegolaTassaSoggiornoDAO;
import it.gavia.sostitutoincloud.dao.TassaFasciaEtaDAO;
import it.gavia.sostitutoincloud.dao.TassaStagioneDAO;
import it.gavia.sostitutoincloud.dao.TassaZonaDAO;
import it.gavia.sostitutoincloud.dto.touristtax.*;
import it.gavia.sostitutoincloud.model.RegolaTassaSoggiorno;
import it.gavia.sostitutoincloud.model.TassaFasciaEta;
import it.gavia.sostitutoincloud.model.TassaStagione;
import it.gavia.sostitutoincloud.model.TassaZona;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Log4j2
public class TouristTaxService {

    private final RegolaTassaSoggiornoDAO regolaDAO;
    private final TassaFasciaEtaDAO fasciaEtaDAO;
    private final TassaStagioneDAO stagioneDAO;
    private final TassaZonaDAO zonaDAO;
    private final AuditService auditService;

    public TouristTaxService(RegolaTassaSoggiornoDAO regolaDAO,
                             TassaFasciaEtaDAO fasciaEtaDAO,
                             TassaStagioneDAO stagioneDAO,
                             TassaZonaDAO zonaDAO,
                             AuditService auditService) {
        this.regolaDAO = regolaDAO;
        this.fasciaEtaDAO = fasciaEtaDAO;
        this.stagioneDAO = stagioneDAO;
        this.zonaDAO = zonaDAO;
        this.auditService = auditService;
    }

    public List<RegolaTassaSoggiornoListDTO> findByTenantId(Integer tenantId) {
        log.debug("TouristTaxService.findByTenantId() - tenantId={}", tenantId);
        List<RegolaTassaSoggiorno> regole = regolaDAO.findByTenantId(tenantId);
        List<Integer> ids = regole.stream().map(RegolaTassaSoggiorno::getId).toList();

        // Conteggi figli caricati in batch — no N+1
        Map<Integer, Integer> fasceCount = fasciaEtaDAO.countByRegolaIds(ids);
        Map<Integer, Integer> stagioniCount = stagioneDAO.countByRegolaIds(ids);
        Map<Integer, Integer> zoneCount = zonaDAO.countByRegolaIds(ids);

        return regole.stream().map(r -> RegolaTassaSoggiornoListDTO.builder()
                .id(r.getId())
                .comune(r.getComune())
                .provincia(r.getProvincia())
                .region(r.getRegion())
                .importoPerNotte(r.getImportoPerNotte())
                .maxNotti(r.getMaxNotti())
                .maxAmountPerPerson(r.getMaxAmountPerPerson())
                .attivo(r.getAttivo())
                .validaDal(r.getValidaDal())
                .validaAl(r.getValidaAl())
                .fascieEtaCount(fasceCount.getOrDefault(r.getId(), 0))
                .stagioniCount(stagioniCount.getOrDefault(r.getId(), 0))
                .zoneCount(zoneCount.getOrDefault(r.getId(), 0))
                .build()).toList();
    }

    public RegolaTassaSoggiornoDetailDTO findById(Integer tenantId, Integer id) {
        log.debug("TouristTaxService.findById() - tenantId={} id={}", tenantId, id);
        RegolaTassaSoggiorno r = getOwnedRegola(tenantId, id);
        return toDetailDTO(r);
    }

    @Transactional
    public RegolaTassaSoggiornoDetailDTO create(Integer tenantId, RegolaTassaSoggiornoCreateDTO dto) {
        log.info("TouristTaxService.create() - tenantId={} comune={}", tenantId, dto.getComune());
        RegolaTassaSoggiorno regola = RegolaTassaSoggiorno.builder()
                .comune(dto.getComune())
                .provincia(dto.getProvincia())
                .region(dto.getRegion())
                .importoPerNotte(dto.getImportoPerNotte())
                .maxNotti(dto.getMaxNotti() != null ? dto.getMaxNotti() : 7)
                .maxAmountPerPerson(dto.getMaxAmountPerPerson())
                .validaDal(dto.getValidaDal())
                .validaAl(dto.getValidaAl())
                .exemptions(dto.getExemptions())
                .notes(dto.getNotes())
                .attivo(true)
                .fkTenantId(tenantId)
                .build();
        RegolaTassaSoggiorno saved = regolaDAO.insert(regola);
        saveChildren(saved.getId(), dto);
        auditService.log("tourist_tax.create", "RegolaTassaSoggiorno", saved.getId(),
                "Creata regola tassa soggiorno per " + saved.getComune());
        return toDetailDTO(getOwnedRegola(tenantId, saved.getId()));
    }

    @Transactional
    public RegolaTassaSoggiornoDetailDTO update(Integer tenantId, Integer id,
                                                RegolaTassaSoggiornoCreateDTO dto) {
        log.info("TouristTaxService.update() - tenantId={} id={}", tenantId, id);
        RegolaTassaSoggiorno existing = getOwnedRegola(tenantId, id);

        existing.setComune(dto.getComune());
        existing.setProvincia(dto.getProvincia());
        existing.setRegion(dto.getRegion());
        existing.setImportoPerNotte(dto.getImportoPerNotte());
        existing.setMaxNotti(dto.getMaxNotti() != null ? dto.getMaxNotti() : existing.getMaxNotti());
        existing.setMaxAmountPerPerson(dto.getMaxAmountPerPerson());
        existing.setValidaDal(dto.getValidaDal());
        existing.setValidaAl(dto.getValidaAl());
        existing.setExemptions(dto.getExemptions());
        existing.setNotes(dto.getNotes());
        regolaDAO.update(existing);

        // Replace completo dei figli
        fasciaEtaDAO.deleteByRegolaId(id);
        stagioneDAO.deleteByRegolaId(id);
        zonaDAO.deleteByRegolaId(id);
        saveChildren(id, dto);

        auditService.log("tourist_tax.update", "RegolaTassaSoggiorno", id,
                "Aggiornata regola tassa soggiorno per " + existing.getComune());
        return toDetailDTO(getOwnedRegola(tenantId, id));
    }

    @Transactional
    public RegolaTassaSoggiornoDetailDTO updateStatus(Integer tenantId, Integer id, Boolean attivo) {
        log.info("TouristTaxService.updateStatus() - tenantId={} id={} attivo={}", tenantId, id, attivo);
        getOwnedRegola(tenantId, id);
        regolaDAO.updateStatus(id, attivo);
        return toDetailDTO(getOwnedRegola(tenantId, id));
    }

    // ---------------------------------------------------------------

    private RegolaTassaSoggiorno getOwnedRegola(Integer tenantId, Integer id) {
        RegolaTassaSoggiorno r = regolaDAO.findById(id)
                .orElseThrow(() -> new RuntimeException("Regola tassa di soggiorno non trovata: id=" + id));
        if (r.getFkTenantId() == null || !r.getFkTenantId().equals(tenantId)) {
            throw new RuntimeException("Regola tassa di soggiorno non trovata: id=" + id);
        }
        return r;
    }

    private void saveChildren(Integer regolaId, RegolaTassaSoggiornoCreateDTO dto) {
        if (dto.getFascieEta() != null) {
            for (TassaFasciaEtaDTO f : dto.getFascieEta()) {
                fasciaEtaDAO.insert(TassaFasciaEta.builder()
                        .fkRegolaId(regolaId)
                        .label(f.getLabel())
                        .minAge(f.getMinAge())
                        .maxAge(f.getMaxAge())
                        .reductionPct(f.getReductionPct() != null ? f.getReductionPct() : 0)
                        .build());
            }
        }
        if (dto.getStagioni() != null) {
            for (TassaStagioneDTO s : dto.getStagioni()) {
                stagioneDAO.insert(TassaStagione.builder()
                        .fkRegolaId(regolaId)
                        .label(s.getLabel())
                        .startMonth(s.getStartMonth())
                        .startDay(s.getStartDay())
                        .endMonth(s.getEndMonth())
                        .endDay(s.getEndDay())
                        .reductionPct(s.getReductionPct() != null ? s.getReductionPct() : 0)
                        .build());
            }
        }
        if (dto.getZone() != null) {
            for (TassaZonaDTO z : dto.getZone()) {
                zonaDAO.insert(TassaZona.builder()
                        .fkRegolaId(regolaId)
                        .label(z.getLabel())
                        .reductionPct(z.getReductionPct() != null ? z.getReductionPct() : 0)
                        .build());
            }
        }
    }

    private RegolaTassaSoggiornoDetailDTO toDetailDTO(RegolaTassaSoggiorno r) {
        List<TassaFasciaEtaDTO> fascie = fasciaEtaDAO.findByRegolaId(r.getId()).stream()
                .map(f -> TassaFasciaEtaDTO.builder()
                        .label(f.getLabel())
                        .minAge(f.getMinAge())
                        .maxAge(f.getMaxAge())
                        .reductionPct(f.getReductionPct())
                        .build()).toList();
        List<TassaStagioneDTO> stagioni = stagioneDAO.findByRegolaId(r.getId()).stream()
                .map(s -> TassaStagioneDTO.builder()
                        .label(s.getLabel())
                        .startMonth(s.getStartMonth())
                        .startDay(s.getStartDay())
                        .endMonth(s.getEndMonth())
                        .endDay(s.getEndDay())
                        .reductionPct(s.getReductionPct())
                        .build()).toList();
        List<TassaZonaDTO> zone = zonaDAO.findByRegolaId(r.getId()).stream()
                .map(z -> TassaZonaDTO.builder()
                        .label(z.getLabel())
                        .reductionPct(z.getReductionPct())
                        .build()).toList();

        return RegolaTassaSoggiornoDetailDTO.builder()
                .id(r.getId())
                .comune(r.getComune())
                .provincia(r.getProvincia())
                .region(r.getRegion())
                .importoPerNotte(r.getImportoPerNotte())
                .maxNotti(r.getMaxNotti())
                .maxAmountPerPerson(r.getMaxAmountPerPerson())
                .attivo(r.getAttivo())
                .validaDal(r.getValidaDal())
                .validaAl(r.getValidaAl())
                .fascieEtaCount(fascie.size())
                .stagioniCount(stagioni.size())
                .zoneCount(zone.size())
                .fascieEta(fascie)
                .stagioni(stagioni)
                .zone(zone)
                .exemptions(r.getExemptions())
                .notes(r.getNotes())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
