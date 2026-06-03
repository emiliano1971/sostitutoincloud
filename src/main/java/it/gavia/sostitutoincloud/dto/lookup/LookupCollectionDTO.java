package it.gavia.sostitutoincloud.dto.lookup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LookupCollectionDTO {

    private List<LookupItemDTO> regimiFiscali;
    private List<LookupItemDTO> tipiImmobile;
    private List<LookupItemDTO> canaliOta;
    private List<LookupItemDTO> tipiDocumento;
    private List<LookupItemDTO> statiPrenotazione;
    private List<LookupItemDTO> statiDocumento;
    private List<LookupItemDTO> scenariFiscali;
}
