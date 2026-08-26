package it.gavia.sostitutoincloud.dto.sdi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** File XML SDI restituito per il download: nome e contenuto. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SdiFileDTO {

    private String nomeFile;
    private byte[] contenuto;
}
