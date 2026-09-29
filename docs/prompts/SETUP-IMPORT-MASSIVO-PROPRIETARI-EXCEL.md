Leggi il CLAUDE.md prima di procedere.
Leggi il file /mnt/skills/public/xlsx/SKILL.md
prima di procedere.

Crea un template Excel .xlsx per
l'importazione massiva di proprietari
e immobili.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. STRUTTURA COLONNE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Foglio 1: "Importazione"

Colonne obbligatorie (sfondo giallo #FFF2CC,
testo grassetto):
A: Cognome Proprietario *
B: Nome Proprietario *
C: Codice Fiscale *
D: Nome Immobile *
E: Città *

Colonne facoltative (sfondo azzurro
chiaro #DDEEFF, testo normale):
F: IBAN
G: Regime Fiscale
← dropdown: cedolare_secca /
ordinario / iva_10
← default: cedolare_secca
H: Email Proprietario
I: Telefono Proprietario
J: Indirizzo Immobile
K: Primo Immobile
← dropdown: Si / No
← default: Si

Colonne regole contratto
(sfondo verde chiaro #E2EFDA,
testo normale):
L: Commissione OTA %
← percentuale sul lordo
← canale OTA default del tenant
M: Pulizie € (fisso)
N: Cambio Biancheria € (per persona)
O: Commissione PM %
P: Tipo Commissione PM
← dropdown: lordo / netto

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. INTESTAZIONE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Riga 1: Titolo centrato su tutta la riga
"Template Importazione Proprietari e Immobili"
sfondo blu scuro #1F4E79, testo bianco,
grassetto, font size 14

Riga 2: Legenda colori
Cella A2: "■ Obbligatorio"
sfondo #FFF2CC
Cella C2: "■ Facoltativo"
sfondo #DDEEFF
Cella E2: "■ Regole Contratto"
sfondo #E2EFDA

Riga 3: intestazioni colonne
con i nomi delle colonne A-P
sfondo grigio #D9D9D9, grassetto

Riga 4: riga di esempio con dati fittizi:
A4: Rossi
B4: Mario
C4: RSSMRA80A01H501Z
D4: Appartamento Centro
E4: Roma
F4: IT60X0542811101000000123456
G4: cedolare_secca
H4: mario.rossi@email.it
I4: 3331234567
J4: Via Roma 1
K4: Si
L4: 15
M4: 60
N4: 20
O4: 10
P4: netto

Riga 5 in poi: righe vuote per i dati

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. DROPDOWN VALIDATION
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi data validation dropdown
sulle colonne G, K, P
per le righe 5:1000:

Colonna G (Regime Fiscale):
Lista: cedolare_secca,ordinario,iva_10

Colonna K (Primo Immobile):
Lista: Si,No

Colonna P (Tipo Commissione PM):
Lista: lordo,netto

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FORMATTAZIONE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

- Larghezza colonne:
  A-B: 20
  C: 18 (CF)
  D: 25 (nome immobile)
  E: 15 (città)
  F: 28 (IBAN)
  G: 18 (regime)
  H: 25 (email)
  I: 15 (telefono)
  J: 25 (indirizzo)
  K: 14 (primo immobile)
  L-O: 12 (importi/percentuali)
  P: 16 (tipo PM)

- Freeze prima riga (riga 3 intestazioni)
- Bordi sulle celle intestazione
- Numeri in L,M,N,O: formato numerico
  con 2 decimali

Foglio 2: "Istruzioni"
Testo con le istruzioni d'uso:

ISTRUZIONI PER L'IMPORTAZIONE

1. CAMPI OBBLIGATORI (sfondo giallo):
    - Cognome e Nome: del proprietario
    - Codice Fiscale: 16 caratteri,
      usato per identificare il
      proprietario. Se già esiste
      nel sistema, l'immobile viene
      associato al proprietario
      esistente senza sovrascriverlo.
    - Nome Immobile: nome display
      dell'immobile
    - Città: comune dell'immobile

2. PROPRIETARIO CON PIÙ IMMOBILI:
   Ripetere le colonne del proprietario
   (A-I) su ogni riga, una per immobile.
   Il sistema riconosce lo stesso
   proprietario tramite il Codice Fiscale.

3. DUPLICATI:
   Se un proprietario o un immobile
   esiste già nel sistema, la riga
   viene saltata senza errori.

4. REGIME FISCALE:
    - cedolare_secca: default,
      locazioni brevi persone fisiche
    - ordinario: con IVA
    - iva_10: IVA agevolata

5. REGOLE CONTRATTO (sfondo verde):
    - Commissione OTA %: percentuale
      applicata al canale OTA default
      configurato nel sistema
    - Pulizie €: importo fisso netto
      (senza IVA)
    - Cambio Biancheria €/persona:
      importo per ospite netto
      (senza IVA)
    - Commissione PM %: percentuale
      netto o sul lordo
    - Tipo PM: 'lordo' = sul lordo
      ospite; 'netto' = sul lordo
      meno le spese

6. ERRORI:
   Le righe con errori vengono saltate.
   Al termine dell'import viene mostrato
   un report con le righe importate,
   saltate e gli errori.

Salva il file come:
/mnt/user-data/outputs/
template_importazione_proprietari.xlsx

Poi presenta il file.