Leggi il file CLAUDE.md prima di procedere.

Implementa un sistema di lookup dinamici per eliminare
le label hardcoded nel frontend.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DTO
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea dto/lookup/LookupItemDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- Integer id
- String codice
- String descrizione
- Boolean attivo

Crea dto/lookup/LookupCollectionDTO.java:
- @Data @Builder @NoArgsConstructor @AllArgsConstructor
- List<LookupItemDTO> regimiFiscali
- List<LookupItemDTO> tipiImmobile
- List<LookupItemDTO> canaliOta
- List<LookupItemDTO> tipiDocumento
- List<LookupItemDTO> statiPrenotazione
- List<LookupItemDTO> statiDocumento
- List<LookupItemDTO> scenariFiscali

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. SERVICE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea service/LookupService.java:
- @Service @Log4j2
- Costruttore con RegimeFiscaleDAO, TipoImmobileDAO,
  CanaleOtaDAO, TipoDocumentoDAO,
  StatoPrenotazioneDAO, StatoDocumentoDAO,
  ScenarioFiscaleDAO

- LookupCollectionDTO getAll()
    - carica tutti i lookup dal DB
    - mappa ogni entità su LookupItemDTO
      usando i campi id, codice, descrizione, attivo
    - restituisce LookupCollectionDTO
    - Log DEBUG: "LookupService.getAll()"

- Helper privato per mapping:
  List<LookupItemDTO> toLookupList(List<?> items)
    - usa reflection oppure mappa manualmente
      ogni tipo — preferisci mapping esplicito
      per chiarezza

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. CONTROLLER
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea controller/LookupController.java:
- @RestController @Log4j2
- @RequestMapping("/api/public/lookup")
- Costruttore con LookupService
- Endpoint pubblico (già coperto da SecurityConfig
  /api/public/** → permitAll())

  GET /api/public/lookup
    - ResponseEntity<LookupCollectionDTO>
    - restituisce tutti i lookup in un colpo solo

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. FRONTEND — LookupContext
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/api/lookupApi.ts:

```ts
export interface LookupItem {
  id: number;
  codice: string;
  descrizione: string;
  attivo: boolean;
}

export interface LookupCollection {
  regimiFiscali: LookupItem[];
  tipiImmobile: LookupItem[];
  canaliOta: LookupItem[];
  tipiDocumento: LookupItem[];
  statiPrenotazione: LookupItem[];
  statiDocumento: LookupItem[];
  scenariFiscali: LookupItem[];
}

export async function getLookups(): Promise<LookupCollection>
// GET /api/public/lookup — pubblico, no auth
// Usa fetch diretto senza Authorization header:
// const res = await fetch(apiUrl('/public/lookup'));
```

Crea frontend/src/contexts/LookupContext.tsx:
- Carica i lookup UNA VOLTA SOLA all'avvio dell'app
- Espone hook useLookup() con:
    * lookups: LookupCollection | null
    * isLoading: boolean
    * getLabelByCodice(lista: LookupItem[],
      codice: string): string
      → restituisce descrizione oppure codice se non trovato
    * getItemByCodice(lista: LookupItem[],
      codice: string): LookupItem | undefined

Crea frontend/src/contexts/LookupContext.tsx:
```tsx
const LookupContext = createContext<LookupContextType>(...);

export const LookupProvider = ({ children }) => {
  const [lookups, setLookups] = useState<LookupCollection|null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    getLookups()
      .then(setLookups)
      .catch(err => console.error('Lookup load error:', err))
      .finally(() => setIsLoading(false));
  }, []);

  const getLabelByCodice = (lista: LookupItem[], 
    codice: string) =>
    lista.find(i => i.codice === codice)?.descrizione ?? codice;

  return (
    <LookupContext.Provider value={{ lookups, isLoading,
      getLabelByCodice }}>
      {children}
    </LookupContext.Provider>
  );
};

export const useLookup = () => useContext(LookupContext);
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. AGGIORNA main.tsx e App.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In frontend/src/main.tsx avvolgi l'app con
LookupProvider DOPO il caricamento della config:

```tsx
loadConfig().then(() => {
  ReactDOM.createRoot(...).render(
    <React.StrictMode>
      <LookupProvider>
        <App />
      </LookupProvider>
    </React.StrictMode>
  );
});
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. AGGIORNA OwnerDetail.tsx e OwnerCreate.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

In OwnerDetail.tsx:
- Rimuovi la costante hardcoded regimeLabels
- Rimuovi la costante hardcoded ownerTypeLabels
  (ownerType non è una lookup DB ma può restare
  hardcoded — è un enum PostgreSQL fisso)
- Usa useLookup() per ottenere lookups
- Sostituisci regimeLabels[owner.fiscalRegime] con:
  getLabelByCodice(lookups.regimiFiscali,
  owner.fiscalRegime)
- Nel dialog modifica: sostituisci le opzioni
  hardcoded del Select regime fiscale con:
  lookups.regimiFiscali.filter(r => r.attivo)
  .map(r => <SelectItem value={r.codice}>
  {r.descrizione}</SelectItem>)
- Mappa codice → id con:
  lookups.regimiFiscali.find(r =>
  r.codice === selectedCodice)?.id

In OwnerCreate.tsx:
- Sostituisci i valori hardcoded del Select
  regime fiscale con lookups.regimiFiscali

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima lancia il build backend:
mvn -Plocal clean package

Verifica endpoint pubblico (senza token):
curl -s http://localhost:8081/sostitutoincloud/api/public/lookup \
| python3 -m json.tool | head -40

Poi verifica frontend:
cd frontend && npm run build

Riporta output di entrambi.