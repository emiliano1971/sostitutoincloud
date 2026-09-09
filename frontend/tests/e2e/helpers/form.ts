import { Locator, Page } from '@playwright/test';

/**
 * I form dell'app usano <Label> shadcn senza htmlFor e <Input> senza id: label e
 * input sono fratelli dentro un div, quindi getByLabel() non li associa. Questi
 * helper risalgono dall'etichetta al primo input che la segue nel DOM.
 *
 * L'etichetta va passata come appare a schermo, asterisco incluso
 * (es. "Ragione Sociale *"), oppure come prefisso con `exact: false`.
 */

function inputDopoLabel(scope: Page | Locator, label: string, exact: boolean): Locator {
  const predicato = exact
    ? `normalize-space(.)=${xpathLiteral(label)}`
    : `starts-with(normalize-space(.), ${xpathLiteral(label)})`;
  return scope.locator(`xpath=.//label[${predicato}]/following::input[1]`);
}

/** Riempie il campo la cui etichetta inizia con `label`. */
export async function fillCampo(
  scope: Page | Locator,
  label: string,
  valore: string,
  opts: { exact?: boolean } = {},
): Promise<void> {
  const input = inputDopoLabel(scope, label, opts.exact ?? false);
  await input.waitFor({ state: 'visible' });
  await input.fill(valore);
}

/**
 * Literal XPath a prova di apice: l'unico modo di includere un ' in XPath 1.0 è
 * spezzare la stringa in concat(). Serve per etichette tipo "Cognome dell'utente".
 */
function xpathLiteral(s: string): string {
  if (!s.includes("'")) return `'${s}'`;
  const parti = s.split("'").map(p => `'${p}'`).join(`, "'", `);
  return `concat(${parti})`;
}
