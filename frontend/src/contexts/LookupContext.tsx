import { createContext, useContext, useEffect, useState } from 'react';
import { getLookups, type LookupCollection, type LookupItem } from '@/api/lookupApi';

interface LookupContextType {
  lookups: LookupCollection | null;
  isLoading: boolean;
  getLabelByCodice: (lista: LookupItem[], codice: string) => string;
  getItemByCodice: (lista: LookupItem[], codice: string) => LookupItem | undefined;
}

const LookupContext = createContext<LookupContextType>({
  lookups: null,
  isLoading: true,
  getLabelByCodice: (_lista, codice) => codice,
  getItemByCodice: () => undefined,
});

export const LookupProvider = ({ children }: { children: React.ReactNode }) => {
  const [lookups, setLookups] = useState<LookupCollection | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    getLookups()
      .then(setLookups)
      .catch(err => console.error('Lookup load error:', err))
      .finally(() => setIsLoading(false));
  }, []);

  const getLabelByCodice = (lista: LookupItem[], codice: string): string =>
    lista.find(i => i.codice === codice)?.descrizione ?? codice;

  const getItemByCodice = (lista: LookupItem[], codice: string): LookupItem | undefined =>
    lista.find(i => i.codice === codice);

  return (
    <LookupContext.Provider value={{ lookups, isLoading, getLabelByCodice, getItemByCodice }}>
      {children}
    </LookupContext.Provider>
  );
};

export const useLookup = () => useContext(LookupContext);
