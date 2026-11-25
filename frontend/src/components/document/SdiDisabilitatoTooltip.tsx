import type { ReactNode } from 'react';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { MSG_SDI_DISABILITATO } from '@/hooks/useSdiAbilitato';

/**
 * Avvolge un pulsante di trasmissione SDI: con l'invio disabilitato aggiunge il tooltip
 * "Invio SDI disabilitato nelle impostazioni" (il pulsante va passato già disabilitato).
 */
const SdiDisabilitatoTooltip = ({ disabilitato, children }: { disabilitato: boolean; children: ReactNode }) => {
  if (!disabilitato) return <>{children}</>;
  return (
    <Tooltip>
      {/* span: un pulsante disabilitato non riceve gli eventi del mouse */}
      <TooltipTrigger asChild><span tabIndex={0} className="inline-flex">{children}</span></TooltipTrigger>
      <TooltipContent className="text-xs">{MSG_SDI_DISABILITATO}</TooltipContent>
    </Tooltip>
  );
};

export default SdiDisabilitatoTooltip;
