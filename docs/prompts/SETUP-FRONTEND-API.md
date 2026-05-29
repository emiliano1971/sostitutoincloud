Leggi il file CLAUDE.md prima di procedere.

Crea la configurazione API runtime per il frontend React.
La configurazione è caricata a runtime da un file config.json
pubblico — NON baked nel bundle al momento della build.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. FILE DI CONFIGURAZIONE PER AMBIENTE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/public/config.local.json:
```json
{
  "apiBaseUrl": "http://localhost:8081/sostitutoincloud/api",
  "environment": "local"
}
```

Crea frontend/public/config.test.json:
```json
{
  "apiBaseUrl": "https://testpms.siv.cloud.it:8443/sostitutoincloud/api",
  "environment": "test"
}
```

Crea frontend/public/config.prod.json:
```json
{
  "apiBaseUrl": "https://prodpms.siv.cloud.it:8443/sostitutoincloud/api",
  "environment": "prod"
}
```

Crea frontend/public/config.json (copia di config.local.json
per sviluppo locale — questo file è gitignored):
```json
{
  "apiBaseUrl": "http://localhost:8081/sostitutoincloud/api",
  "environment": "local"
}
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. TIPO TYPESCRIPT PER LA CONFIGURAZIONE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/config/AppConfig.ts:
```ts
export interface AppConfig {
  apiBaseUrl: string;
  environment: 'local' | 'test' | 'prod';
}

let _config: AppConfig | null = null;

export async function loadConfig(): Promise<AppConfig> {
  if (_config) return _config;
  const res = await fetch('/config.json');
  if (!res.ok) throw new Error('Impossibile caricare config.json');
  _config = await res.json();
  return _config!;
}

export function getConfig(): AppConfig {
  if (!_config) throw new Error('Config non ancora caricata — chiamare loadConfig() prima');
  return _config;
}
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. API CLIENT
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea frontend/src/lib/apiClient.ts:
```ts
import { getConfig } from '@/config/AppConfig';

function apiUrl(path: string): string {
  const base = getConfig().apiBaseUrl;
  return `${base}${path.startsWith('/') ? path : '/' + path}`;
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const error = await res.text().catch(() => res.statusText);
    throw new Error(`HTTP ${res.status}: ${error}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

const defaultHeaders = {
  'Content-Type': 'application/json',
  'Accept': 'application/json',
};

export async function get<T>(path: string): Promise<T> {
  const res = await fetch(apiUrl(path), { headers: defaultHeaders });
  return handleResponse<T>(res);
}

export async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(apiUrl(path), {
    method: 'POST',
    headers: defaultHeaders,
    body: JSON.stringify(body),
  });
  return handleResponse<T>(res);
}

export async function put<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(apiUrl(path), {
    method: 'PUT',
    headers: defaultHeaders,
    body: JSON.stringify(body),
  });
  return handleResponse<T>(res);
}

export async function del<T>(path: string): Promise<T> {
  const res = await fetch(apiUrl(path), {
    method: 'DELETE',
    headers: defaultHeaders,
  });
  return handleResponse<T>(res);
}
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. CARICA LA CONFIG ALL'AVVIO IN main.tsx
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Modifica frontend/src/main.tsx per caricare
la config prima del render dell'app:

```ts
import { loadConfig } from '@/config/AppConfig';

loadConfig().then(() => {
  ReactDOM.createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
}).catch(err => {
  console.error('Errore caricamento configurazione:', err);
  document.body.innerHTML = '<h1>Errore configurazione applicazione</h1>';
});
```

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. AGGIORNA .gitignore
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi al .gitignore nella root del progetto:
# Config runtime frontend (contiene URL ambiente specifici)
frontend/public/config.json

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. AGGIORNA my-build.xml
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Aggiungi nel target "deploy" di my-build.xml
DOPO il passo 7 (copia log4j2):

```xml
<!-- ── 8. Copia config.{env}.json → WEB-INF/classes/static/config.json ──
     Permette di cambiare URL API senza ricompilare il frontend.
     Il file viene cercato in src/main/resources/frontend/
     dove vengono messi i config per ambiente. -->
<copy tofile="${project.basedir}/WEB-INF/classes/static/config.json"
      file="${project.basedir}/frontend/public/config.${deploy.env}.json"
      overwrite="true"
      failonerror="false"/>
```

NON modificare nessun altro file esistente oltre
a main.tsx, my-build.xml e .gitignore.
Verifica che il frontend compili senza errori con:
npm run build
dalla cartella frontend/ e riporta l'output.