import { useEffect, useState } from 'react';
import { cn } from '@/lib/utils';
import { getBuildInfo, type BuildInfo } from '@/api/versionApi';

/** Versione dell'applicazione (V{yy}.{mmdd}-{hash}) in fondo alla sidebar. */
export function AppFooter({ className }: { className?: string }) {
  const [buildInfo, setBuildInfo] = useState<BuildInfo | null>(null);

  useEffect(() => {
    getBuildInfo().then(setBuildInfo).catch(() => {});
  }, []);

  return (
    <div className={cn('text-xs text-muted-foreground px-4 py-2 border-t', className)}>
      {buildInfo ? buildInfo.version : '...'}
    </div>
  );
}
