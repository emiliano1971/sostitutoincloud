Leggi il file CLAUDE.md e docs/db/schema-target.sql
prima di procedere.

Implementa la tabella comuni italiani e il
calcolo automatico del codice fiscale ospite.
Il file docs/db/seed-comuni.sql è già presente
nel repo con 7.894 comuni (fonte ISTAT).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. MIGRATION DB
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea docs/db/migrations/
006_comune_italiano.sql:

CREATE TABLE IF NOT EXISTS comune_italiano (
id               SERIAL PRIMARY KEY,
nome             VARCHAR(150) NOT NULL,
sigla_provincia  CHAR(2)      NOT NULL,
regione          VARCHAR(100) NOT NULL,
codice_belfiore  CHAR(4)      NOT NULL UNIQUE,
created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS
idx_comune_nome
ON comune_italiano
(LOWER(nome), sigla_provincia);

CREATE INDEX IF NOT EXISTS
idx_comune_belfiore
ON comune_italiano(codice_belfiore);

Esegui migration e seed:
psql -U sostitutoincloud -d sostitutoincloud \
-h localhost \
-f docs/db/migrations/006_comune_italiano.sql

psql -U sostitutoincloud -d sostitutoincloud \
-h localhost \
-f docs/db/seed-comuni.sql

Verifica:
psql -U sostitutoincloud -d sostitutoincloud \
-h localhost -c "
SELECT COUNT(*) AS totale,
COUNT(DISTINCT sigla_provincia) AS province,
COUNT(DISTINCT regione) AS regioni
FROM comune_italiano;"

Aggiorna docs/db/schema-target.sql
aggiungendo la tabella comune_italiano.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. MODEL + MAPPER + DAO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea model/ComuneItaliano.java:
- @Data @Builder @NoArgsConstructor
  @AllArgsConstructor
- Integer id
- String nome
- String siglaProvincia
- String regione
- String codiceBelfiore

Crea dao/mapper/ComuneItalianoRowMapper.java:
- implements RowMapper<ComuneItaliano>

Crea dao/ComuneItalianoDAO.java:
- @Repository @Log4j2

List<ComuneItaliano> findByNome(String nome)
- SELECT * FROM comune_italiano
  WHERE LOWER(nome) LIKE LOWER(?) || '%'
  ORDER BY nome
  LIMIT 20
- parametro: nome (stringa parziale)
- Log DEBUG

Optional<ComuneItaliano> findByNomeEsatto(
String nome)
- SELECT * FROM comune_italiano
  WHERE LOWER(nome) = LOWER(?)
  LIMIT 1
- Log DEBUG

Optional<ComuneItaliano> findByBelfiore(
String codice)
- SELECT * FROM comune_italiano
  WHERE codice_belfiore = ?
- Log DEBUG

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CodiceFiscaleService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/CodiceFiscaleService.java:
- @Service @Log4j2
- Costruttore con ComuneItalianoDAO

Il calcolo CF italiano segue l'algoritmo
ufficiale Agenzia Entrate:

String calcola(
String cognome,
String nome,
LocalDate dataNascita,
String sesso,        ← "M" o "F"
String comuneNascita ← nome comune o
codice Belfiore
)

Lancia IllegalArgumentException se:
- cognome o nome vuoti
- dataNascita null
- sesso non è "M" o "F"
- comune non trovato in comune_italiano

Algoritmo:
1. COGNOME (3 chars):
    - Prendi le consonanti nell'ordine
    - Se meno di 3, aggiungi le vocali
    - Se ancora meno di 3, padding con 'X'

2. NOME (3 chars):
    - Se 4+ consonanti: prendi la 1ª, 3ª, 4ª
    - Altrimenti: consonanti + vocali + 'X'

3. ANNO (2 chars): ultimi 2 cifre anno nascita

4. MESE (1 char): codice mese
   A B C D E H L M P R S T
   (gen-dic)

5. GIORNO (2 chars):
    - M: giorno nascita (01-31)
    - F: giorno + 40 (41-71)

6. COMUNE (4 chars): codice_belfiore del comune
    - cerca prima per nome esatto
    - se non trovato cerca come codice
      Belfiore diretto

7. CARATTERE CONTROLLO (1 char):
    - Algoritmo ufficiale con tabelle pari/dispari
    - Tabella dispari:
      0→1, 1→0, 2→5, 3→7, 4→9, 5→13,
      6→15, 7→17, 8→19, 9→21,
      A→1, B→0, C→5, D→7, E→9, F→13,
      G→15, H→17, I→19, J→21, K→2,
      L→4, M→18, N→20, O→11, P→3,
      Q→6, R→8, S→12, T→14, U→16,
      V→10, W→22, X→25, Y→24, Z→23
    - Tabella pari: A=0,B=1,...Z=25, 0=0,...9=9
    - Somma posizioni dispari (1,3,5...) con
      tabella dispari
    - Somma posizioni pari (2,4,6...) con
      tabella pari
    - Totale MOD 26 → lettera A-Z

Restituisce CF in UPPERCASE (16 chars).

Log INFO "CodiceFiscaleService.calcola()
- nome={} cognome={} nato={} comune={}"

Aggiungi metodo:
Optional<String> calcolaSafe(
String cognome, String nome,
LocalDate dataNascita, String sesso,
String comuneNascita)
- chiama calcola() in try/catch
- se IllegalArgumentException → return empty
- Log WARN se empty

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. LookupController — aggiungi endpoint comuni
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi a controller/LookupController.java
(o PublicLookupController se esiste):

GET /api/public/comuni?q={stringa}
- chiama comuneItalianoDAO.findByNome(q)
- ResponseEntity<List<ComuneItaliano>>
- Accessibile senza autenticazione
  (serve nel form import/ospite)
- Log DEBUG

GET /api/public/comuni/{belfiore}
- chiama comuneItalianoDAO.findByBelfiore()
- ResponseEntity<ComuneItaliano>
- 404 se non trovato

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. Integrazione BookingImportService
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica service/BookingImportService.java:

Aggiungi CodiceFiscaleService al costruttore.

Nel metodo parseRowV2(), dopo il merge
dati ospite, se il CF è mancante:
- se comuneNascita, dataNascita, nome,
  cognome e sesso sono presenti:
    * chiama codiceFiscaleService
      .calcolaSafe(...)
    * se presente: imposta guestTaxCode
      sul booking
    * rimuovi il warning
      "CF non calcolabile: ..."
- se mancano dati per il calcolo:
    * mantieni i warning esistenti

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. FRONTEND — comuneApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/api/comuneApi.ts:

export interface ComuneItaliano {
id: number
nome: string
siglaProvincia: string
regione: string
codiceBelfiore: string
}

export async function cercaComuni(
q: string
): Promise<ComuneItaliano[]>
// GET /api/public/comuni?q={q}
// solo se q.length >= 2

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. FRONTEND — ComuneAutocomplete.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/components/
ComuneAutocomplete.tsx:

Props:
value: string
onChange: (nome: string,
belfiore: string) => void
placeholder?: string
disabled?: boolean

Comportamento:
- Input testo con debounce 300ms
- Da 2+ caratteri chiama cercaComuni()
- Dropdown con risultati:
  "{nome} ({siglaProvincia})"
- Click su risultato → chiama onChange
  con nome e codiceBelfiore
- Se nessun risultato → "Nessun comune
  trovato"
- Loading spinner durante la ricerca

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
8. TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal clean package
cd frontend && npm run build

# Verifica comuni caricati
curl -s "http://localhost:8081/\
sostitutoincloud/api/public/comuni?q=Roma" \
| python3 -m json.tool | head -20

# Test calcolo CF (caso noto)
# Mario Rossi, M, 01/01/1980, Roma → RSSMRA80A01H501U
TOKEN=$(curl -s -X POST \
http://localhost:8081/sostitutoincloud/\
api/public/login \
-H "Content-Type: application/json" \
-d '{"email":"admin@casavacanze.it",
"password":"atena"}' \
| python3 -c "import sys,json; \
print(json.load(sys.stdin)['token'])")

curl -s -X POST \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"cognome": "Rossi",
"nome": "Mario",
"dataNascita": "1980-01-01",
"sesso": "M",
"comuneNascita": "Roma"
}' \
"http://localhost:8081/sostitutoincloud/\
api/cf/calcola" \
| python3 -m json.tool

CF atteso: RSSMRA80A01H501U

Riporta output build, curl comuni
e verifica CF.