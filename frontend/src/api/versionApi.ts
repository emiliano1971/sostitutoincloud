import { getConfig } from '@/config/AppConfig';

export interface BuildInfo {
  version: string;
  commitHash: string;
  commitDate: string;
}

/** Versione dell'applicazione — endpoint pubblico, nessun token. */
export async function getBuildInfo(): Promise<BuildInfo> {
  const res = await fetch(`${getConfig().apiBaseUrl}/public/version`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
