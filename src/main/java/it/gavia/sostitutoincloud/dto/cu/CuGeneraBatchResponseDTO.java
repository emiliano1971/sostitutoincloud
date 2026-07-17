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
    private List<CuListDTO> records;
}
