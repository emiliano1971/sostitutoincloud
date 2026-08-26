import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { useState } from 'react';
import ComuneAutocomplete from './ComuneAutocomplete';
import { cercaComuni } from '@/api/comuneApi';

vi.mock('@/api/comuneApi', () => ({
  cercaComuni: vi.fn(),
}));

const ROMA = { id: 1, nome: 'Roma', siglaProvincia: 'RM', regione: 'Lazio', codiceBelfiore: 'H501' };
const MILANO = { id: 2, nome: 'Milano', siglaProvincia: 'MI', regione: 'Lombardia', codiceBelfiore: 'F205' };
const mockCerca = vi.mocked(cercaComuni);

/** Attende il debounce di 300ms della ricerca. */
const avanzaDebounce = async () => {
  await act(async () => {
    vi.advanceTimersByTime(400);
  });
};

/** Digita nel campo e attende i risultati del dropdown. */
const digitaEAttendi = async (testo: string) => {
  const input = screen.getByRole('textbox');
  fireEvent.focus(input);
  fireEvent.change(input, { target: { value: testo } });
  await avanzaDebounce();
  await waitFor(() => expect(screen.getByRole('listbox')).toBeInTheDocument());
  return input;
};

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  mockCerca.mockReset();
  mockCerca.mockResolvedValue([ROMA]);
});

afterEach(() => {
  vi.useRealTimers();
});

describe('ComuneAutocomplete — il dropdown non si apre da solo', () => {
  it('al mount con initialValue valorizzato: nessuna ricerca e nessun dropdown', async () => {
    render(<ComuneAutocomplete value="Roma" initialValue="Roma" onChange={() => {}} />);
    await avanzaDebounce();

    expect(screen.getByRole('textbox')).toHaveValue('Roma');
    expect(mockCerca).not.toHaveBeenCalled();
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('al mount con solo value valorizzato (senza initialValue): nessuna ricerca e nessun dropdown', async () => {
    render(<ComuneAutocomplete value="Ferrara" onChange={() => {}} />);
    await avanzaDebounce();

    expect(mockCerca).not.toHaveBeenCalled();
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('quando value arriva in modo asincrono (dati caricati dopo il mount): nessun dropdown', async () => {
    // Riproduce TenantSettings: al mount il valore è vuoto e arriva dopo la GET.
    const Wrapper = () => {
      const [comune, setComune] = useState('');
      return (
        <>
          <button type="button" onClick={() => setComune('Milano')}>carica</button>
          <ComuneAutocomplete value={comune} initialValue={comune} onChange={() => {}} />
        </>
      );
    };
    render(<Wrapper />);
    fireEvent.click(screen.getByRole('button', { name: 'carica' }));
    await avanzaDebounce();

    expect(screen.getByRole('textbox')).toHaveValue('Milano');
    expect(mockCerca).not.toHaveBeenCalled();
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('digitando 2+ caratteri con focus: il dropdown si apre', async () => {
    render(<ComuneAutocomplete value="" onChange={() => {}} />);
    await digitaEAttendi('Rom');

    expect(mockCerca).toHaveBeenCalledWith('Rom');
    expect(screen.getByRole('option', { name: 'Roma (RM)' })).toBeInTheDocument();
  });

  it('con meno di 2 caratteri il dropdown resta chiuso anche con focus', async () => {
    render(<ComuneAutocomplete value="" onChange={() => {}} />);
    const input = screen.getByRole('textbox');

    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: 'R' } });
    await avanzaDebounce();

    expect(mockCerca).not.toHaveBeenCalled();
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('la selezione col click chiude il dropdown e notifica il comune completo', async () => {
    const onChange = vi.fn();
    const onSelect = vi.fn();
    render(<ComuneAutocomplete value="" onChange={onChange} onSelect={onSelect} />);
    await digitaEAttendi('Rom');

    fireEvent.click(screen.getByRole('option', { name: 'Roma (RM)' }));

    expect(onChange).toHaveBeenCalledWith('Roma', 'H501');
    expect(onSelect).toHaveBeenCalledWith(ROMA);
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });
});

describe('ComuneAutocomplete — requireValidComune', () => {
  const ERRORE = 'Seleziona un comune dalla lista';

  it('testo libero + blur: svuota il valore nel form e mostra l\'errore', async () => {
    const onChange = vi.fn();
    render(<ComuneAutocomplete value="" onChange={onChange} requireValidComune />);
    const input = screen.getByRole('textbox');

    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: 'Comune inventato' } });
    await avanzaDebounce();
    fireEvent.blur(input);
    await avanzaDebounce();

    expect(onChange).toHaveBeenCalledWith('', '');
    expect(screen.getByText(ERRORE)).toBeInTheDocument();
    expect(input).toHaveValue('');
    expect(input).toHaveAttribute('aria-invalid', 'true');
  });

  it('selezione dalla lista + blur: nessun errore e valore conservato', async () => {
    const onChange = vi.fn();
    const onSelect = vi.fn();
    render(<ComuneAutocomplete value="" onChange={onChange} onSelect={onSelect} requireValidComune />);
    const input = await digitaEAttendi('ro');

    fireEvent.click(screen.getByRole('option', { name: 'Roma (RM)' }));
    fireEvent.blur(input);
    await avanzaDebounce();

    expect(onChange).toHaveBeenCalledWith('Roma', 'H501');
    expect(onChange).not.toHaveBeenCalledWith('', '');
    expect(screen.queryByText(ERRORE)).not.toBeInTheDocument();
    expect(input).toHaveValue('Roma');
  });

  it('selezione con Enter + blur: nessun errore', async () => {
    const onChange = vi.fn();
    render(<ComuneAutocomplete value="" onChange={onChange} requireValidComune />);
    const input = await digitaEAttendi('ro');

    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.keyDown(input, { key: 'Enter' });
    fireEvent.blur(input);
    await avanzaDebounce();

    expect(onChange).toHaveBeenCalledWith('Roma', 'H501');
    expect(onChange).not.toHaveBeenCalledWith('', '');
    expect(screen.queryByText(ERRORE)).not.toBeInTheDocument();
  });

  it('valore preesistente non toccato: focus e blur non lo cancellano', async () => {
    // Caso PropertyEdit / TenantSettings: il comune arriva dal DB, già valido.
    const onChange = vi.fn();
    render(<ComuneAutocomplete value="Roma" initialValue="Roma" onChange={onChange} requireValidComune />);
    const input = screen.getByRole('textbox');

    fireEvent.focus(input);
    fireEvent.blur(input);
    await avanzaDebounce();

    expect(onChange).not.toHaveBeenCalled();
    expect(screen.queryByText(ERRORE)).not.toBeInTheDocument();
    expect(input).toHaveValue('Roma');
  });

  it('modificare il testo dopo una selezione invalida di nuovo il valore', async () => {
    const onChange = vi.fn();
    render(<ComuneAutocomplete value="" onChange={onChange} requireValidComune />);
    const input = await digitaEAttendi('ro');

    fireEvent.click(screen.getByRole('option', { name: 'Roma (RM)' }));
    onChange.mockClear();

    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: 'Romaaa' } });
    await avanzaDebounce();
    fireEvent.blur(input);
    await avanzaDebounce();

    expect(onChange).toHaveBeenCalledWith('', '');
    expect(screen.getByText(ERRORE)).toBeInTheDocument();
  });

  it('l\'errore scompare appena l\'utente ricomincia a digitare', async () => {
    render(<ComuneAutocomplete value="" onChange={() => {}} requireValidComune />);
    const input = screen.getByRole('textbox');

    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: 'xyz' } });
    await avanzaDebounce();
    fireEvent.blur(input);
    await avanzaDebounce();
    expect(screen.getByText(ERRORE)).toBeInTheDocument();

    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: 'ro' } });

    expect(screen.queryByText(ERRORE)).not.toBeInTheDocument();
    expect(input).not.toHaveAttribute('aria-invalid', 'true');
  });

  it('senza requireValidComune il testo libero resta e non compare errore', async () => {
    const onChange = vi.fn();
    render(<ComuneAutocomplete value="" onChange={onChange} />);
    const input = screen.getByRole('textbox');

    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: 'Comune inventato' } });
    await avanzaDebounce();
    fireEvent.blur(input);
    await avanzaDebounce();

    expect(onChange).not.toHaveBeenCalled();
    expect(screen.queryByText(ERRORE)).not.toBeInTheDocument();
    expect(input).toHaveValue('Comune inventato');
  });
});

describe('ComuneAutocomplete — navigazione da tastiera', () => {
  beforeEach(() => {
    mockCerca.mockResolvedValue([ROMA, MILANO]);
  });

  it('nessun risultato è evidenziato prima di premere i tasti', async () => {
    render(<ComuneAutocomplete value="" onChange={() => {}} />);
    await digitaEAttendi('ro');

    for (const opt of screen.getAllByRole('option')) {
      expect(opt).toHaveAttribute('aria-selected', 'false');
    }
  });

  it("ArrowDown scorre i risultati verso il basso e si ferma sull'ultimo", async () => {
    render(<ComuneAutocomplete value="" onChange={() => {}} />);
    const input = await digitaEAttendi('ro');

    fireEvent.keyDown(input, { key: 'ArrowDown' });
    expect(screen.getByRole('option', { name: 'Roma (RM)' })).toHaveAttribute('aria-selected', 'true');

    fireEvent.keyDown(input, { key: 'ArrowDown' });
    expect(screen.getByRole('option', { name: 'Milano (MI)' })).toHaveAttribute('aria-selected', 'true');

    // Oltre l'ultimo non va: resta su Milano
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    expect(screen.getByRole('option', { name: 'Milano (MI)' })).toHaveAttribute('aria-selected', 'true');
  });

  it('ArrowUp torna indietro e si ferma sul primo', async () => {
    render(<ComuneAutocomplete value="" onChange={() => {}} />);
    const input = await digitaEAttendi('ro');

    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    expect(screen.getByRole('option', { name: 'Milano (MI)' })).toHaveAttribute('aria-selected', 'true');

    fireEvent.keyDown(input, { key: 'ArrowUp' });
    expect(screen.getByRole('option', { name: 'Roma (RM)' })).toHaveAttribute('aria-selected', 'true');

    // Prima del primo non va: resta su Roma
    fireEvent.keyDown(input, { key: 'ArrowUp' });
    expect(screen.getByRole('option', { name: 'Roma (RM)' })).toHaveAttribute('aria-selected', 'true');
  });

  it('Enter seleziona il risultato evidenziato', async () => {
    const onChange = vi.fn();
    const onSelect = vi.fn();
    render(<ComuneAutocomplete value="" onChange={onChange} onSelect={onSelect} />);
    const input = await digitaEAttendi('ro');

    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.keyDown(input, { key: 'Enter' });

    expect(onChange).toHaveBeenCalledWith('Milano', 'F205');
    expect(onSelect).toHaveBeenCalledWith(MILANO);
    expect(screen.getByRole('textbox')).toHaveValue('Milano');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('Enter senza risultato evidenziato non seleziona nulla', async () => {
    const onChange = vi.fn();
    render(<ComuneAutocomplete value="" onChange={onChange} />);
    const input = await digitaEAttendi('ro');

    fireEvent.keyDown(input, { key: 'Enter' });

    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByRole('listbox')).toBeInTheDocument();
  });

  it('Escape chiude il dropdown senza selezionare', async () => {
    const onChange = vi.fn();
    render(<ComuneAutocomplete value="" onChange={onChange} />);
    const input = await digitaEAttendi('ro');

    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.keyDown(input, { key: 'Escape' });

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });

  it('lo scroll segue il risultato evidenziato', async () => {
    // jsdom non implementa scrollIntoView: lo stubbo per verificare la chiamata.
    const scrollIntoView = vi.fn();
    (Element.prototype as unknown as { scrollIntoView: unknown }).scrollIntoView = scrollIntoView;
    try {
      render(<ComuneAutocomplete value="" onChange={() => {}} />);
      const input = await digitaEAttendi('ro');

      fireEvent.keyDown(input, { key: 'ArrowDown' });
      expect(scrollIntoView).toHaveBeenCalledWith({ block: 'nearest' });
    } finally {
      delete (Element.prototype as unknown as { scrollIntoView?: unknown }).scrollIntoView;
    }
  });

  it('una nuova ricerca azzera il risultato evidenziato', async () => {
    render(<ComuneAutocomplete value="" onChange={() => {}} />);
    const input = await digitaEAttendi('ro');

    fireEvent.keyDown(input, { key: 'ArrowDown' });
    expect(screen.getByRole('option', { name: 'Roma (RM)' })).toHaveAttribute('aria-selected', 'true');

    mockCerca.mockResolvedValue([MILANO, ROMA]);
    fireEvent.change(input, { target: { value: 'mil' } });
    await avanzaDebounce();

    await waitFor(() => {
      for (const opt of screen.getAllByRole('option')) {
        expect(opt).toHaveAttribute('aria-selected', 'false');
      }
    });
  });
});
