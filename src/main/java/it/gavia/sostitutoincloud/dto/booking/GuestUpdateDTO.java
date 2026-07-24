package it.gavia.sostitutoincloud.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuestUpdateDTO {
    private String guestName;
    private String guestTaxCode;
    private String guestBirthDate;      // ISO yyyy-MM-dd
    private String guestSesso;          // M/F
    private String guestBirthPlace;     // nome comune
    private String guestBirthBelfiore;  // codice Belfiore
    private String guestDocType;
    private String guestDocNumber;
    private String guestCountry;
}
