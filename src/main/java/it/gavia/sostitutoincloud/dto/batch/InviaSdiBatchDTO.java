package it.gavia.sostitutoincloud.dto.batch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Body delle azioni massive sui documenti (ZIP XML, invio SDI). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InviaSdiBatchDTO {
    private List<Integer> documentIds;
}
