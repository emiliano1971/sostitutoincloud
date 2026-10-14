import { useEffect, useState } from 'react';
import { getBuildInfo, type BuildInfo } from '@/api/versionApi';

export default function AboutPage() {
  const [buildInfo, setBuildInfo] = useState<BuildInfo | null>(null);

  useEffect(() => {
    getBuildInfo().then(setBuildInfo).catch(() => {});
  }, []);

  return (
    <div className="space-y-4 max-w-md">
      <h1 className="text-2xl font-bold">Sostituto in Cloud</h1>

      <div className="space-y-2">
        <div className="flex justify-between text-sm">
          <span className="text-muted-foreground">Versione</span>
          <span className="font-mono">{buildInfo?.version}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-muted-foreground">Commit</span>
          <span className="font-mono">{buildInfo?.commitHash}</span>
        </div>
        <div className="flex justify-between text-sm">
          <span className="text-muted-foreground">Data build</span>
          <span className="font-mono">{buildInfo?.commitDate}</span>
        </div>
      </div>
    </div>
  );
}
