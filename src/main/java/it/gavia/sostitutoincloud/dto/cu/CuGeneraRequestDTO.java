package it.gavia.sostitutoincloud.dto.cu;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CuGeneraRequestDTO {

    /** Nullable: se null la generazione è batch su tutti i proprietari con ritenute. */
    private Integer ownerId;
    /** Obbligatorio. */
    private Integer taxYear;
}
