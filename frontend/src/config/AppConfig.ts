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
