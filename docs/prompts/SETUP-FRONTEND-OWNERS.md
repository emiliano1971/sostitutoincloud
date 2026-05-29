Leggi il file CLAUDE.md e docs/analisi-frontend.md
prima di procedere.

Collega la pagina OwnersList al backend reale
sostituendo i dati mock.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ANALISI PRELIMINARE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Prima leggi questi file e descrivi brevemente
cosa trovano:
- frontend/src/pages/tenant/OwnersList.tsx
- frontend/src/types/index.ts (tipo Owner/OwnerProfile)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. CREA frontend/src/api/ownerApi.ts
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/api/ownerApi.ts:

Definisci il tipo OwnerListItem che corrisponde
esattamente a quello che restituisce
GET /api/owners (OwnerListDTO.java):
```ts
export interface OwnerListItem {
  id: number;
  ownerType: string;
  firstName: string;
  lastName: string;
  legalName?: string;
  taxCode: string;
  vatNumber?: string;
  fiscalRegime: string;
  email: string;
  phone: string;
  iban: string;
  attivo: boolean;
  propertiesCount: number;
  createdAt: string;
}

export interface OwnerDetail extends OwnerListItem {
  fkTenantId: number;
  updatedAt: string;
  bookingsCount: number;
  totalGrossAmount: number;
  totalOwnerNet: number;
  settlementsCount: number;
}
```

Esporta le funzioni:
```ts
export async function getOwners(
  attivo?: boolean
): Promise<OwnerListItem[]>
// chiama GET /api/owners o GET /api/owners?attivo=true/false

export async function getOwnerById(
  id: number
): Promise<OwnerDetail>
// chiama GET /api/owners/{id}
```

Usa get<T>() da apiClient.ts

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. AGGIORNA OwnersList.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/pages/tenant/OwnersList.tsx:
- Mantieni TUTTO il layout, stile e UI esistente
- Sostituisci solo la sorgente dati:

  DA:
  import { mockOwners } from '@/data/mock-data'
  const owners = mockOwners.filter(...)

  A:
  const [owners, setOwners] = useState<OwnerListItem[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
  getOwners()
  .then(setOwners)
  .catch(err => setError(err.message))
  .finally(() => setIsLoading(false))
  }, [])

- Aggiungi stato di loading:
  se isLoading → mostra spinner o skeleton
  se error → mostra messaggio di errore
  se owners vuoto dopo load → mostra "Nessun proprietario"

- Mantieni la search locale in memoria
  (filtra su firstName+lastName+taxCode)
- Adatta i campi se i nomi differiscono tra mock e API:
    * mock usava owner.status — API usa owner.attivo (boolean)
    * mock usava owner.fiscal_regime — API usa owner.fiscalRegime
    * Adatta i badge e le condizioni di conseguenza

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. BUILD E TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che il frontend compili:
cd frontend && npm run build

Riporta eventuali errori TypeScript e il risultato.
NON serve riavviare Tomcat — le modifiche sono
solo frontend.