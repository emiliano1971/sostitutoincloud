package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.CanaleOtaDAO;
import it.gavia.sostitutoincloud.dao.RegimeFiscaleDAO;
import it.gavia.sostitutoincloud.dao.ScenarioFiscaleDAO;
import it.gavia.sostitutoincloud.dao.StatoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.StatoPrenotazioneDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.TipoImmobileDAO;
import it.gavia.sostitutoincloud.dto.lookup.LookupCollectionDTO;
import it.gavia.sostitutoincloud.dto.lookup.LookupItemDTO;
import it.gavia.sostitutoincloud.model.CanaleOta;
import it.gavia.sostitutoincloud.model.RegimeFiscale;
import it.gavia.sostitutoincloud.model.ScenarioFiscale;
import it.gavia.sostitutoincloud.model.StatoDocumento;
import it.gavia.sostitutoincloud.model.StatoPrenotazione;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import it.gavia.sostitutoincloud.model.TipoImmobile;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Log4j2
public class LookupService {

    private final RegimeFiscaleDAO regimeFiscaleDAO;
    private final TipoImmobileDAO tipoImmobileDAO;
    private final CanaleOtaDAO canaleOtaDAO;
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final StatoPrenotazioneDAO statoPrenotazioneDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;
    private final ScenarioFiscaleDAO scenarioFiscaleDAO;

    public LookupService(RegimeFiscaleDAO regimeFiscaleDAO,
                         TipoImmobileDAO tipoImmobileDAO,
                         CanaleOtaDAO canaleOtaDAO,
                         TipoDocumentoDAO tipoDocumentoDAO,
                         StatoPrenotazioneDAO statoPrenotazioneDAO,
                         StatoDocumentoDAO statoDocumentoDAO,
                         ScenarioFiscaleDAO scenarioFiscaleDAO) {
        this.regimeFiscaleDAO = regimeFiscaleDAO;
        this.tipoImmobileDAO = tipoImmobileDAO;
        this.canaleOtaDAO = canaleOtaDAO;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
        this.statoPrenotazioneDAO = statoPrenotazioneDAO;
        this.statoDocumentoDAO = statoDocumentoDAO;
        this.scenarioFiscaleDAO = scenarioFiscaleDAO;
    }

    public LookupCollectionDTO getAll() {
        log.debug("LookupService.getAll()");
        return LookupCollectionDTO.builder()
                .regimiFiscali(mapRegimiFiscali(regimeFiscaleDAO.findByMetadata("REGIME_FISCALE")))
                .regimiFiscaliPm(mapRegimiFiscali(regimeFiscaleDAO.findByMetadata("REGIME_FISCALE_PM")))
                .naturaIva(mapRegimiFiscali(regimeFiscaleDAO.findByMetadata("NATURA_IVA")))
                .aliquoteIva(mapRegimiFiscali(regimeFiscaleDAO.findByMetadata("ALIQUOTA_IVA")))
                .tipiImmobile(mapTipiImmobile(tipoImmobileDAO.findAll()))
                .canaliOta(mapCanaliOta(canaleOtaDAO.findAll()))
                .tipiDocumento(mapTipiDocumento(tipoDocumentoDAO.findAll()))
                .statiPrenotazione(mapStatiPrenotazione(statoPrenotazioneDAO.findAll()))
                .statiDocumento(mapStatiDocumento(statoDocumentoDAO.findAll()))
                .scenariFiscali(mapScenari(scenarioFiscaleDAO.findAll()))
                .build();
    }

    private List<LookupItemDTO> mapRegimiFiscali(List<RegimeFiscale> list) {
        return list.stream()
                .map(r -> LookupItemDTO.builder()
                        .id(r.getId()).codice(r.getCodice())
                        .descrizione(r.getDescrizione()).attivo(r.getAttivo())
                        .build())
                .toList();
    }

    private List<LookupItemDTO> mapTipiImmobile(List<TipoImmobile> list) {
        return list.stream()
                .map(r -> LookupItemDTO.builder()
                        .id(r.getId()).codice(r.getCodice())
                        .descrizione(r.getDescrizione()).attivo(r.getAttivo())
                        .build())
                .toList();
    }

    private List<LookupItemDTO> mapCanaliOta(List<CanaleOta> list) {
        return list.stream()
                .map(r -> LookupItemDTO.builder()
                        .id(r.getId()).codice(r.getCodice())
                        .descrizione(r.getNome())   // CanaleOta usa `nome`, non `descrizione`
                        .attivo(r.getAttivo())
                        .build())
                .toList();
    }

    private List<LookupItemDTO> mapTipiDocumento(List<TipoDocumento> list) {
        return list.stream()
                .map(r -> LookupItemDTO.builder()
                        .id(r.getId()).codice(r.getCodice())
                        .descrizione(r.getDescrizione()).attivo(r.getAttivo())
                        .build())
                .toList();
    }

    private List<LookupItemDTO> mapStatiPrenotazione(List<StatoPrenotazione> list) {
        return list.stream()
                .map(r -> LookupItemDTO.builder()
                        .id(r.getId()).codice(r.getCodice())
                        .descrizione(r.getDescrizione()).attivo(r.getAttivo())
                        .build())
                .toList();
    }

    private List<LookupItemDTO> mapStatiDocumento(List<StatoDocumento> list) {
        return list.stream()
                .map(r -> LookupItemDTO.builder()
                        .id(r.getId()).codice(r.getCodice())
                        .descrizione(r.getDescrizione()).attivo(r.getAttivo())
                        .build())
                .toList();
    }

    private List<LookupItemDTO> mapScenari(List<ScenarioFiscale> list) {
        return list.stream()
                .map(r -> LookupItemDTO.builder()
                        .id(r.getId()).codice(r.getCodice())
                        .descrizione(r.getDescrizione()).attivo(r.getAttivo())
                        .build())
                .toList();
    }
}
