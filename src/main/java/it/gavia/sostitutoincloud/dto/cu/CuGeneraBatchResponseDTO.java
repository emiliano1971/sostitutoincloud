package it.gavia.sostitutoincloud.dto.cu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuGeneraBatchResponseDTO {

    private Integer generated;
    private Integer skipped;
    /** CU draft/generated orfane (nessuna ritenuta valida nell'anno) eliminate. */
    private Integer eliminated;
    private List<CuListDTO> records;
}
