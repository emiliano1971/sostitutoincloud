# Sostituto in Cloud - Architettura logica completa del PMS fiscale

## Stato del documento

Documento di lavoro operativo per allineare:

- modello di business
- architettura software
- flussi fiscali
- flussi documentali
- integrazioni con channel manager
- domande aperte da validare con il commercialista

Questo file non dà per risolti i punti fiscali controversi: li rende espliciti, li collega ai documenti caricati e li traduce in decisioni architetturali.

## 1\. Obiettivo del prodotto

Costruire un **PMS fiscale per property manager** che faccia davvero, in modo operativo, le seguenti cose:

- acquisizione dati prenotazione da sistemi terzi
- ricostruzione economica della prenotazione
- calcolo della base imponibile rilevante
- calcolo ritenute locazioni brevi
- gestione rendiconti proprietari
- generazione documenti fiscali
- generazione F24
- generazione Certificazione Unica (CU)
- nessuna predisposizione dati per modello 770
- eventuale invio telematico degli adempimenti, dove tecnicamente e giuridicamente possibile

Il prodotto non sostituisce il channel manager e, salvo scelta strategica diversa, non sostituisce neppure il PMS operativo del property manager. Il prodotto nasce come motore fiscale-contabile verticale.

## 2\. Fonti considerate

### 2.1 Documenti usati

- Accordo di Ripartizione delle Responsabilità - Sostituto in Cloud
- Architettura Tecnica e Fiscale - Implementazione del Sostituto d'Imposta
- Struttura del Sostituto d'Imposta nel Modello
- PM fatturazione schema
- Business plan e fogli di valutazione

### 2.2 Fonti ufficiali esterne da verificare ogni anno

- Agenzia delle Entrate - Contratti di locazione breve
- Agenzia delle Entrate - Le regole per gli intermediari
- Agenzia delle Entrate - Certificazione Unica 2026
- Agenzia delle Entrate - 770/2026
- Agenzia delle Entrate - F24/770 anno 2026 specifiche tecniche
- Agenzia delle Entrate - FAQ fattura elettronica

## 3\. Sintesi esecutiva

Il nodo vero del progetto non è solo il sostituto d'imposta. Il nodo vero è questo:

- **i property manager hanno già quasi sempre un channel manager**
- i dati economicamente rilevanti non arrivano quasi mai in modo perfetto dalle OTA
- i documenti caricati non chiudono in modo definitivo il tema di chi fattura a chi
- i tempi di emissione dei documenti non sono stati ancora tradotti in regole software certe
- F24 e CU possono essere prodotti dal gestionale, ma l'eventuale invio telematico richiede un canale abilitato e responsabilità ben definite

Quindi il sistema va progettato come:

- motore di acquisizione dati
- motore di riconciliazione economica
- motore fiscale
- motore documentale
- motore adempimenti
- motore di audit

## 4\. Attori del sistema

### 4.1 Ospite

Soggetto che prenota e paga il soggiorno.

### 4.2 OTA

Piattaforme come:

- Airbnb
- Booking
- Vrbo
- Expedia

Le OTA possono:

- acquisire la prenotazione
- incassare dal guest
- trattenere fee
- accreditare un payout successivo

### 4.3 Channel manager

Sistema già usato dal property manager per:

- distribuzione annunci
- sincronizzazione calendari
- prezzi
- disponibilità
- talvolta ricezione prenotazioni e payout data

Esempi di mercato:

- Guesty
- Hostaway
- Smoobu
- Lodgify
- Beds24

### 4.4 PMS fiscale - Sostituto in Cloud

Sistema oggetto del progetto. Deve gestire:

- acquisizione dati
- motore regole fiscali
- documenti
- F24
- CU
- rendiconti
- controllo e audit

### 4.5 Property manager

Intermediario operativo. Può:

- promuovere gli immobili
- coordinare l'operatività
- incassare direttamente
- ricevere payout dalle OTA
- pagare il proprietario
- agire come sostituto d'imposta, se ricorrono i presupposti

### 4.6 Proprietario

Titolare del reddito sottostante.

Attenzione: i documenti caricati contemplano più casistiche, ma la disciplina delle locazioni brevi va riletta con cura perché le fonti ufficiali recenti la descrivono come riferita a contratti stipulati da persone fisiche fuori dall'esercizio di impresa.

### 4.7 Commercialista / intermediario fiscale

Figura che valida le regole, assiste su casi dubbi, gestisce eventuali adempimenti residui e presidia la compliance finale.

## 5\. Problema architetturale di mercato: il channel manager esiste già

### 5.1 Flusso reale di mercato

Nella maggior parte dei casi il flusso reale non è:

OTA -> Sostituto in Cloud

ma:

Ospite  
\-> OTA  
\-> Channel manager del PM  
\-> Sostituto in Cloud

### 5.2 Conseguenza

Il prodotto **non può essere pensato come integrazione diretta standard con Airbnb e Booking** per tutti i clienti.

Deve invece essere progettato come uno dei seguenti modelli:

- integrazione API con channel manager
- import periodico da channel manager / PMS / contabilità
- layer fiscale sopra ecosistema software già esistente

### 5.3 Decisione di progetto

Il documento deve dare per aperta, ma esplicita, questa decisione:

**Serve davvero il realtime?**

**Entro quando devo mandare le ricevute/fatture all'ospite?**

**Servono veramente le fatture o potrei fare delle ricevute all'ospite? Questo eviterebbe di avere i dati fiscali dell'ospite.**

**A questo punto se non serve real time, ma posso attendere il mese successivo, potrei anche 'scaricare' i file di contabilità dagli altri gestionali/ota.**

Risposta architetturale preliminare:

- per l'adempimento F24 e CU il realtime non è necessariamente indispensabile
- per blocchi operativi, alert, check-in, richiesta dati fiscali guest e documenti immediati, il realtime può invece diventare molto utile

Quindi il sistema va progettato con doppio modello di alimentazione:

- realtime_events
- batch_reconciliation

## 6\. Architettura logica a layer

### 6.1 Layer 1 - Ingestion

Responsabilità:

- acquisire prenotazioni
- acquisire modifiche prenotazioni
- acquisire cancellazioni
- acquisire payout OTA
- acquisire anagrafiche immobili e owner
- acquisire eventuali dati guest

Input possibili:

- API channel manager
- webhook channel manager
- import CSV/XLSX
- import da PMS esistente
- import da contabilità / estratti conto

### 6.2 Layer 2 - Canonical booking model

Serve un modello dati unico interno, indipendente dal fornitore esterno.

Entità minime:

- property_manager
- owner
- property
- booking
- booking_line
- guest
- payout_event
- tax_profile
- document_profile
- withholding_event
- owner_statement
- fiscal_period

### 6.3 Layer 3 - Economic split engine

Funzione:

ricostruire per ogni prenotazione almeno:

- importo lordo prenotazione
- canone locazione
- commissione OTA
- commissione PM
- cleaning fee
- extra
- sconti
- imposta di soggiorno, se gestita fuori base **(ogni proprietà può avere i prezzi con o senza tassa di soggiorno inclusa)**
- importo netto proprietario

### 6.4 Layer 4 - Fiscal rules engine

Funzione:

applicare regole fiscali parametrizzabili in base a:

- natura del contratto **(il proprietario ha partita iva o persona fisica con ritenuta al 21%/cedolare secca fino al secondo immobile**)
- ruolo del PM
- soggetto beneficiario
- evento che fa sorgere l'obbligo
- tipo documento da emettere
- regole ritenuta

### 6.5 Layer 5 - Document engine

Funzione:

- generare ricevute
- generare fatture
- generare rendiconti
- generare CU sintetica per il percipiente
- generare file / payload adempimenti

### 6.6 Layer 6 - Compliance and filing engine

Funzione:

- generare F24
- generare file telematici CU
- non generare dataset 770
- generare log di trasmissione
- gestire esiti, scarti, reinvii

### 6.7 Layer 7 - Audit

Funzione:

rendere sempre ricostruibile:

- dato originario ricevuto
- dato trasformato
- regola fiscale applicata
- documento emesso
- adempimento generato
- eventuale invio telematico ed esito

## 7\. Dati minimi che il sistema deve ricevere

Per funzionare davvero, il PMS fiscale deve ricevere almeno:

### 7.1 Dati prenotazione

- booking_id sorgente
- channel / OTA
- property_id
- owner_id
- check-in
- check-out
- date prenotazione
- stato prenotazione
- data cancellazione, se presente

### 7.2 Dati economici

- gross booking amount
- cleaning fee
- OTA fee
- PM fee
- extra
- coupon / sconto
- payout amount
- payout date
- currency

### 7.3 Dati guest

- nome e cognome
- email
- telefono
- paese
- codice fiscale, se necessario per documenti
- indirizzo, se necessario per documenti

### 7.4 Dati owner

- nome / ragione sociale
- codice fiscale
- partita IVA, se esistente
- IBAN
- regime fiscale dichiarato
- natura rapporto con PM
- dati utili a CU

### 7.5 Dati fiscali di controllo

- chi ha incassato
- chi paga il proprietario
- chi emette il documento al guest
- chi figura come sostituto d'imposta
- data evento fiscalmente rilevante

## 8\. Split economico della prenotazione

Esempio logico:

Prenotazione totale: 100,00  
Commissione OTA: 15,00  
Pulizie: 10,00  
Commissione PM: 15,00  
Canone locazione: 60,00

Questo layer non deve essere hardcoded su un solo modello.

Deve supportare almeno:

- OTA fee lato host
- OTA fee lato guest
- cleaning inside gross
- cleaning fuori gross
- PM fee percentuale
- PM fee fissa
- extra soggetti a trattamento diverso

Il vero prodotto qui è il **motore di ricostruzione economica fiscalmente leggibile**.

## 9\. Modulo ritenute

### 9.1 Regola di base operativa

Per i contratti che rientrano nella disciplina delle locazioni brevi, l'intermediario che incassa o interviene nel pagamento deve operare la ritenuta del 21% e versarla con F24.

### 9.2 Trigger event della ritenuta

Questo punto è fondamentale per il software.

**Il motore ritenute non deve essere triggerato dal check-out in automatico.**

**Deve invece essere progettato intorno a un evento esplicito di pagamento al beneficiario, perché le fonti ufficiali dell'Agenzia descrivono la ritenuta come effettuata al momento del pagamento al beneficiario.**

**Quindi l'evento è in fin dei conti il pagamento da parte dell'OTA?**

Quindi l'entità chiave è:

- beneficiary_payment_event

Campi minimi:

- booking_id
- beneficiary_id
- gross_due_to_beneficiary
- taxable_base
- withholding_rate
- withholding_amount
- payment_date
- accounting_period

### 9.3 Problema normativo aperto da validare

I documenti caricati assumono in alcuni punti che, se il proprietario è società o ha partita IVA, la ritenuta non si applichi.

Le fonti ufficiali recenti, però, descrivono la disciplina delle locazioni brevi come riferita a contratti stipulati da persone fisiche fuori dall'esercizio di impresa, e hanno modificato alcune regole sugli intermediari dal 2024.

Quindi il sistema deve prevedere una **matrice di regole fiscale-versionata**, non un solo if/else rigido.

## 10\. F24 - cosa deve fare davvero il gestionale

### 10.1 Obiettivo prodotto

Il gestionale deve essere in grado di:

- calcolare le ritenute mese per mese
- aggregarle per soggetto obbligato
- generare la delega / dataset F24
- generare un export controllabile
- opzionalmente inviare tramite canale abilitato (non nelle prime versioni)
- registrare esito, quietanza o fallimento

### 10.2 Chi fa l'F24 nel modello target

Nel modello target del progetto:

- il gestionale genera l'F24
- l'eventuale invio (non gestito nelle prime version) può essere:
    - diretto, se il soggetto obbligato opera con canale abilitato
    - delegato a commercialista / intermediario

### 10.3 Cosa contiene l'F24 in questo contesto

Il sistema deve almeno compilare i dati essenziali della sezione Erario:

- codice fiscale del soggetto che versa
- codice tributo: 1919
- mese di riferimento
- anno di riferimento
- importo a debito versato
- eventuali dati accessori richiesti dal tracciato / canale di pagamento

### 10.4 Frequenza

Mensile.

### 10.5 Scadenza operativa

Entro il giorno 16 del mese successivo a quello in cui la ritenuta è stata effettuata.

**Sempre legato alla data di pagamento dell'ota?**

### 10.6 Implicazione software

Il modulo F24 deve lavorare con tre date diverse, da non confondere:

- stay_end_date
- beneficiary_payment_date
- f24_due_date

Il prodotto **non deve assumere** che queste tre date coincidano.

### 10.7 Oggetti software necessari

- withholding_ledger
- monthly_f24_batch
- f24_line
- submission_channel
- submission_result
- payment_reconciliation

### 10.8 Invio telematico

Se si vuole fare anche l'invio dal gestionale, servono:

- credenziali o deleghe del soggetto obbligato o dell'intermediario
- rispetto delle specifiche tecniche del canale usato
- conservazione esiti
- gestione scarti e reinvii

Questa parte non è un semplice export PDF: è una vera funzione di compliance (non gestita ora).

## 11\. CU - cosa deve fare il gestionale

### 11.1 Obiettivo prodotto

Il gestionale deve essere in grado di:

- aggregare i dati annuali per percipiente
- distinguere i redditi / corrispettivi rilevanti
- riportare le ritenute operate
- produrre la CU da consegnare al percipiente
- produrre il file telematico CU da trasmettere all'Agenzia
- tracciare esiti e rettifiche

### 11.2 Chi fa la CU nel modello target

Nel modello target del progetto:

- il gestionale genera la CU
- l'eventuale invio può essere:
    - diretto tramite canale telematico abilitato
    - oppure delegato a commercialista / intermediario

### 11.3 Cosa deve contenere la CU in questo contesto

A livello minimo il sistema deve saper gestire:

- dati del sostituto / soggetto certificante
- dati del percipiente
- importi corrisposti / corrispettivi rilevanti
- ritenute operate
- anno fiscale
- eventuali marcatori / casistiche previste dal modello vigente per locazioni brevi

### 11.4 Output da produrre

- CU sintetica da consegnare al proprietario / percipiente
- CU ordinaria telematica da trasmettere all'Agenzia
    - Non ora
- tracciato di controllo pre-invio
- log esito invio

### 11.5 Scadenza operativa standard 2026

Per il periodo d'imposta 2025:

- consegna al percipiente entro il 16 marzo 2026
- trasmissione telematica entro il 16 marzo 2026

### 11.6 Oggetti software necessari

- annual_recipient_summary
- cu_record
- cu_delivery_event
- cu_telematic_batch
- cu_submission_result
- cu_correction_event

### 11.7 Nota molto importante

La CU è un output annuale, ma i dati per costruirla si raccolgono a livello di evento durante tutto l'anno.

Se il ledger mensile non è corretto, la CU diventa inattendibile.

## 12\. Modello 770 - ruolo del gestionale

Nel modello prudenziale:

- il gestionale non deve necessariamente inviare il 770 in prima fase
- deve però generare dataset completi e riconciliati per permetterne la compilazione

Funzioni minime:

- export annuale ritenute versate
- export annuale CU generate
- riconciliazione F24 vs CU vs ledger
- segnalazione anomalie

Scadenza standard 2026 per il modello 770/2026:

- 31 ottobre 2026

## 13\. Motore documentale: la parte più pericolosa del progetto

Qui stanno le decisioni più rischiose.

### 13.1 Domanda aperta n. 1

**Io che agisco come PM sono davvero obbligato a fare fattura all'ospite?**

Questa domanda cambia tutto perché modifica:

- i dati da raccogliere dal guest
- il tipo documento
- il timing di emissione
- la necessità di self check-in fiscale o recuperare i dati real time dal channel manager
- il carico di integrazione

### 13.2 Possibili modelli documentali

#### Modello A - PM fattura servizi al guest

Il sistema deve poter emettere:

- fattura PM al guest
- ricevuta / documento proprietario per la locazione, se dovuto

#### Modello B - Proprietario documenta verso guest

Il sistema deve generare o assistere:

- ricevuta proprietario al guest
- eventuale fattura PM al proprietario

#### Modello C - Modelli diversi per diversi cluster clienti

Il prodotto deve essere parametrico per cliente / mandato / profilo fiscale.

### 13.3 Implicazione operativa

Se il PM deve fatturare al guest, quasi sicuramente il prodotto deve risolvere il tema:

- codice fiscale guest
- dati anagrafici completi guest
- consenso / raccolta dati
- momento di acquisizione dei dati

In pratica, potrebbe servire un **modulo self check-in fiscale** o un'integrazione con chi lo fa.

## 14\. Domanda critica che era rimasta fuori: quando va emessa la fattura o ricevuta?

Questa è una delle decisioni più importanti del progetto.

### 14.1 Domanda formulata correttamente

Il sistema deve sapere quale evento genera l'obbligo di emissione del documento:

- check-in?
- check-out?
- incasso guest da parte OTA?
- payout OTA al PM?
- pagamento del PM al proprietario?
- richiesta del cliente?

**La risposta a questo cambia la modalità d'uso dell'intero sistema.**

### 14.2 Punto fermo architetturale

Il software non può hardcodare un solo evento finché il commercialista non chiude la questione.

Serve un campo configurabile:

- document_trigger_policy

Valori possibili:

- on_checkout
- on_guest_payment
- on_ota_payout
- on_pm_collection
- on_owner_payout
- manual_review

### 14.3 Regola generale da tenere presente

**Per le fatture IVA esistono regole generali di emissione collegate al momento di effettuazione dell'operazione; la fattura immediata segue la regola generale dei 12 giorni e in alcuni casi esiste la fattura differita entro il 15 del mese successivo.**

Ma **questa regola generale non basta da sola** a decidere il comportamento del prodotto, perché prima va stabilito se nel tuo caso stai davvero emettendo una fattura del PM al guest, una ricevuta del proprietario, o un altro documento.

### 14.4 Traduzione software

Il motore documentale deve registrare separatamente:

- operation_date
- document_issue_deadline
- actual_issue_date
- document_reasoning_rule

## 15\. Seconda domanda critica rimasta fuori: devo attendere di essere pagato dalle OTA?

### 15.1 Distinzione necessaria

Questa domanda in realtà si divide in due.

#### A. Per la ritenuta

L'architettura deve assumere, salvo diversa conferma del commercialista, che il trigger della ritenuta sia collegato al momento in cui l'intermediario paga il beneficiario oppure interviene nel pagamento in modo fiscalmente rilevante.

#### B. Per il documento

Non è detto che il trigger documentale coincida con il trigger della ritenuta.

### 15.2 Conseguenza software

Il prodotto deve separare nettamente:

- document_event
- withholding_event
- cash_event

Questi tre eventi possono coincidere in alcuni modelli, ma **non vanno mai fusi a livello dati**.

### 15.3 Domanda aperta da chiudere col commercialista

**Tra l'uscita dell'ospite e il momento di emissione del documento, quanti giorni hai davvero a disposizione e qual è l'evento corretto da usare?**

###

### 15.4 Paletti normativi già noti da tenere fermi nel software

Questi punti non chiudono i dubbi di business del progetto, ma sono vincoli operativi già noti e da modellare:

- la ritenuta sulle locazioni brevi, quando dovuta, va operata dall'intermediario al momento del pagamento al beneficiario
- il versamento avviene con F24, codice tributo 1919
- il versamento ordinario va effettuato entro il giorno 16 del mese successivo
- la CU per il periodo d'imposta 2025 va consegnata al percipiente e trasmessa telematicamente entro il 16 marzo 2026
- il modello 770/2026 resta un adempimento annuale separato che il sistema deve almeno saper alimentare con dataset riconciliati?
- per le fatture IVA, in via generale, la fattura immediata segue il termine dei 12 giorni dall'effettuazione dell'operazione e la fattura differita può arrivare entro il 15 del mese successivo, ma solo se il modello documentale applicabile al caso concreto consente davvero di usare quella logica

Conclusione: il gestionale deve conoscere la regola generale, ma non deve dedurre automaticamente da essa che nel tuo modello il documento vada emesso al check-out.

## 15-bis. Matrice decisionale operativa: chi fattura, quando, su quale evento, con quali dati guest

Questa sezione serve a trasformare i dubbi fiscali in una matrice implementabile nel software.

### 15-bis.1 Principio architetturale

Il sistema deve essere progettato per gestire **scenari multipli**, non per assumere un solo flusso documentale.

Ogni cliente / mandato / cluster fiscale deve poter avere almeno questi parametri:

- guest_document_issuer
- guest_document_type
- guest_document_trigger
- guest_document_deadline_policy
- owner_document_issuer
- owner_statement_policy
- withholding_trigger
- requires_guest_tax_data
- requires_realtime_channel_sync
- requires_manual_tax_review

### 15-bis.2 Matrice scenario - livello business/fiscale

| **Scenario** | **Chi documenta verso l'ospite**   | **Tipo documento verso l'ospite** | **Chi documenta verso il proprietario**         | **Evento candidato per il documento guest**              | **Evento ritenuta**                                              | **Dati guest minimi**                                    | **Realtime utile?** | **Stato**                  |
| ------------ | ---------------------------------- | --------------------------------- | ----------------------------------------------- | -------------------------------------------------------- | ---------------------------------------------------------------- | -------------------------------------------------------- | ------------------- | -------------------------- |
| A            | PM                                 | Fattura servizi PM                | Proprietario o sistema per suo conto, se dovuto | da validare: checkout / pagamento guest / payout OTA     | pagamento al beneficiario                                        | nome, cognome, indirizzo, CF se necessario, paese, email | medio-alto          | da validare                |
| B            | Proprietario                       | Ricevuta / documento locazione    | PM fattura al proprietario o rende conto        | da validare: checkout / pagamento / richiesta cliente    | pagamento al beneficiario                                        | nome, cognome; CF se richiesto dal tipo documento        | medio               | da validare                |
| C            | Proprietario                       | Fattura al guest                  | PM fattura al proprietario                      | effettuazione operazione secondo modello IVA applicabile | ritenuta solo se il caso rientra davvero nel perimetro normativo | dati completi fattura guest                              | medio               | da validare                |
| D            | Modello ibrido per cluster clienti | dipende dal profilo fiscale       | dipende dal profilo fiscale                     | policy parametrica                                       | policy parametrica                                               | configurabile                                            | alto                | raccomandato lato software |

### 15-bis.3 Matrice evento/tempo: cosa succede al check-out, al payout OTA e al pagamento al proprietario

| **Evento**                            | **Cosa sappiamo con certezza**               | **Cosa NON possiamo assumere automaticamente**   | **Utilità software**                |
| ------------------------------------- | -------------------------------------------- | ------------------------------------------------ | ----------------------------------- |
| Prenotazione confermata               | esiste un booking e una previsione economica | che il documento fiscale sia già emettibile      | preallerta, raccolta dati guest     |
| Check-in                              | l'ospite ha iniziato il soggiorno            | che il corrispettivo finale sia definitivo       | completamento anagrafica            |
| Check-out                             | il soggiorno è terminato                     | che il documento vada sempre emesso qui          | possibile trigger in alcuni modelli |
| Pagamento guest alla OTA              | esiste un incasso lato piattaforma OTA       | che equivalga all'obbligo documentale del PM     | utile per riconciliazione           |
| Payout OTA al PM                      | il PM riceve cassa o credito                 | che coincida sempre con effettuazione operazione | forte utilità per accounting        |
| Pagamento PM al proprietario          | nasce l'evento più vicino alla ritenuta      | che sia anche il trigger del documento al guest  | trigger ritenuta / owner statement  |
| Richiesta esplicita fattura dal guest | il cliente ha chiesto documento              | che tu possa emetterlo senza dati completi       | trigger workflow dati guest         |

### 15-bis.4 Domanda chiave trasformata in regola software

La domanda dell'utente diventa questa regola di prodotto:

**Tra l'uscita dell'ospite e l'emissione del documento, il sistema deve calcolare una deadline sulla base del tipo documento e dell'evento trigger corretto, non sulla base di una sola data fissa.**

Quindi non basta memorizzare checkout_date. Servono almeno:

- guest_stay_end_date
- document_trigger_event_type
- document_trigger_event_date
- document_issue_deadline_date
- document_issue_actual_date
- cash_collection_event_date
- ota_payout_date
- owner_payment_date

### 15-bis.5 Regole candidate da sottoporre al commercialista

Il gestionale deve poter configurare almeno queste policy senza sviluppo custom:

- issue_on_checkout_plus_n_days
- issue_on_guest_payment_plus_n_days
- issue_on_ota_payout_plus_n_days
- issue_on_owner_payment_plus_n_days
- issue_only_if_requested_by_guest
- issue_immediate_invoice
- issue_deferred_invoice_with_month_end_batch
- manual_tax_review_before_issue

### 15-bis.6 Dati guest: matrice minima per tipo documento

| **Tipo documento**                        | **Dati minimi pratici da raccogliere**                                              | **Rischio se mancano**                                                                                       |
| ----------------------------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Ricevuta semplice / documento non IVA     | nome e cognome, periodo soggiorno, importo, riferimenti prenotazione                | documento incompleto o contestabile                                                                          |
| Fattura verso privato residente in Italia | nome e cognome, indirizzo, codice fiscale, data operazione, descrizione prestazione | fattura non emettibile correttamente o scarto in canali elettronici se i dati anagrafici/fiscali sono errati |
| Fattura verso soggetto IVA                | denominazione, P.IVA / CF, indirizzo, dati fiscali completi                         | impossibilità di emissione corretta                                                                          |
| Documento da emettere solo su richiesta   | almeno dati identificativi minimi e workflow per raccolta dati entro la deadline    | richiesta cliente non evasa nei tempi                                                                        |

### 15-bis.7 Impatto sul channel manager e sul realtime

Se il modello documentale richiede dati guest completi prima della deadline, allora il realtime diventa molto più prezioso.

Matrice sintetica:

| **Caso**                                           | **Serve integrazione realtime con channel manager?** | **Motivo**                                 |
| -------------------------------------------------- | ---------------------------------------------------- | ------------------------------------------ |
| Solo F24 + CU + rendiconti mensili                 | non necessariamente                                  | il batch può bastare                       |
| Fattura guest automatica vicino all'evento fiscale | spesso sì                                            | servono booking update e dati guest rapidi |
| Self check-in fiscale obbligatorio                 | sì o quasi                                           | serve orchestrare raccolta dati e reminder |
| Solo output per commercialista                     | no                                                   | basta riconciliazione periodica            |

### 15-bis.8 Domande aggiuntive da fare al commercialista / fiscalista

- Nel tuo modello operativo specifico il PM rientra tra i soggetti per cui la fattura non è obbligatoria salvo richiesta del cliente, oppure no?
- Se il guest chiede fattura, qual è l'ultimo momento utile per la richiesta nel tuo caso concreto?
- Per il documento verso l'ospite, l'evento corretto è l'effettuazione dell'operazione, il check-out, l'incasso OTA o altro evento convenzionale supportato da idonea documentazione?
- Se si usa fattura differita per prestazioni di servizi, quale documentazione interna / esterna la giustifica nel tuo flusso?
- Se la OTA paga il PM giorni o settimane dopo il check-out, il documento verso l'ospite va comunque emesso prima del payout?
- Se il payout OTA arriva aggregato per più booking, come si determinano trigger e scadenza del singolo documento?
- In quali casi il gestionale deve fermarsi in manual_tax_review invece di emettere automaticamente?
- Se i dati guest completi arrivano tardi, il sistema deve emettere comunque un documento minimale, attendere, o bloccare il caso come anomalia?
- La raccolta del codice fiscale è obbligatoria in tutti i casi in cui il PM emette documento verso privato, oppure solo in specifiche configurazioni?
- Per i casi in cui il documento sia dovuto solo su richiesta del guest, il gestionale deve mantenere una finestra configurabile di richiesta fino a un determinato evento?

### 15-bis.9 Decisione consigliata per MVP documentale

Per non sbagliare architettura nel MVP:

- NON assumere che il documento guest nasca al check-out
- NON assumere che il documento guest debba attendere sempre il payout OTA
- separare sempre evento soggiorno, evento cassa, evento ritenuta, evento documento
- introdurre policy configurabili per trigger e deadline
- prevedere una coda document_review_queue per i casi dubbi
- progettare fin da subito i reminder guest-data se il modello richiede fattura al guest

## 16\. Motore decisionale: realtime o non realtime?

### 16.1 Domanda aperta n. 2

**Devo collegarmi ai channel manager per fare qualcosa di realtime?**

### 16.2 Risposta architetturale prudenziale

Il realtime è utile se vuoi:

- chiedere subito i dati guest mancanti
- emettere documenti a ridosso dell'evento fiscale corretto
- bloccare anomalie prima del payout
- fare workflow automatici di check-in / documento / owner statement

Il realtime non è strettamente necessario se vuoi solo:

- calcolare ritenute mensili
- generare F24
- generare CU
- fare rendiconti a posteriori

### 16.3 Decisione consigliata

MVP:

- import batch + riconciliazione

Versione avanzata:

- webhook / API realtime per clienti che lo richiedono

## 17\. Flussi operativi completi che il sistema deve supportare

### 17.1 Flusso base prenotazione

OTA  
\-> channel manager  
\-> ingestion  
\-> canonical booking  
\-> economic split  
\-> fiscal profile assignment

### 17.2 Flusso documentale

booking / payment / checkout event  
\-> document trigger policy  
\-> documento da emettere  
\-> generazione documento  
\-> consegna / invio  
\-> audit log

### 17.3 Flusso ritenute

beneficiary payment event  
\-> calcolo base  
\-> calcolo ritenuta  
\-> registrazione ledger  
\-> aggregazione mensile  
\-> F24

### 17.4 Flusso annuale CU

ledger annuale  
\-> aggregazione per percipiente  
\-> CU sintetica  
\-> CU ordinaria telematica  
\-> invio / esito

### 17.5 Flusso annuale 770

ledger annuale  
\+ F24 inviati  
\+ CU inviate  
\-> dataset 770  
\-> consegna al commercialista / eventuale filing

## 18\. Decisioni di progetto da prendere prima dello sviluppo serio

### 18.1 Decisione A - Modello di integrazione

Il prodotto parte come:

- batch/import
- oppure realtime/API
- oppure ibrido

### 18.2 Decisione B - Modello documentale

Il PM:

- fattura il guest?
- non fattura il guest?
- fattura solo in alcuni casi?

### 18.3 Decisione C - Trigger del documento

Quale evento determina l'emissione?

- checkout
- incasso guest
- payout OTA
- altro

### 18.4 Decisione D - Trigger della ritenuta

Quale evento determina l'effettuazione della ritenuta?

- pagamento al beneficiario
- altro caso specifico da formalizzare

### 18.5 Decisione E - Filing

Il prodotto fa solo:

- generazione output

oppure anche:

- invio telematico F24
- invio telematico CU
- gestione esiti

### 18.6 Decisione F - Self check-in fiscale

Serve un modulo interno o una integrazione esterna per ottenere dal guest i dati necessari ai documenti?

## 19\. Domande da fare al commercialista - checklist completa

### 19.1 Ambito normativo

- Nei casi di property management descritti, quando siamo davvero dentro la disciplina delle locazioni brevi e quando ne siamo fuori?
- Come va trattato il caso in cui il proprietario opera con partita IVA o società rispetto ai documenti caricati?
- I documenti attuali del progetto vanno aggiornati alla disciplina e prassi più recente?

### 19.2 Fatturazione / ricevute

- Il property manager è obbligato a emettere fattura all'ospite in questo modello?
- Se no, in quali casi no?
- Se sì, quale documento emette esattamente e per quali voci?
- Quando il documento va emesso: al check-out, al pagamento guest, al payout OTA, al pagamento del PM, o con altra regola?
- Quanti giorni di tempo ci sono tra l'evento rilevante e l'emissione del documento?
- La regola cambia tra fattura PM, ricevuta proprietario e altri documenti?
- È necessario attendere il pagamento da parte delle OTA prima di emettere il documento?
- Se il guest chiede fattura, quali dati minimi vanno raccolti e in quale momento?

### 19.3 Ritenute

- Qual è esattamente la base di calcolo della ritenuta nel modello che vuoi implementare?
- Pulizie ed extra entrano o no nella base?
- L'evento che fa scattare la ritenuta è il pagamento al beneficiario?
- In quali casi il PM è sostituto d'imposta e in quali no?
- Come gestire casi di payout OTA tardivi o payout aggregati?

### 19.4 F24

- Il gestionale può generare direttamente la delega F24 come output ufficiale da usare?
- L'F24 può essere inviato dal gestionale o deve sempre passare da intermediario / home banking / altro canale?
- Quali controlli vanno eseguiti prima dell'invio?
- Come va gestito il caso di scarto, ravvedimento o correzione?

### 19.5 CU

- La CU deve essere emessa sempre per tutti i percipienti interessati dal modello?
- Quali campi specifici del modello CU sono essenziali nel vostro caso?
- Il gestionale può produrre il file telematico CU pronto per l'invio?
- Chi si assume la responsabilità dell'invio telematico?
- Come si gestiscono CU correttive o sostitutive?

### 19.6 770

- In prima fase basta produrre il dataset 770 per il commercialista?
- Quali quadrature minime devono risultare tra ledger, F24, CU e 770?

### 19.7 Dati guest

- Se il PM deve fatturare il guest, serve obbligatoriamente un flusso per acquisire il codice fiscale?
- Questo implica un modulo di self check-in o basta un form separato?
- Qual è il comportamento corretto se il guest non fornisce in tempo i dati fiscali richiesti?

## 20\. Alert normativi e contraddizioni da tenere accese

### 20.1 Alert n. 1 - Proprietario con partita IVA / società

I documenti caricati descrivono casistiche in cui il proprietario ha partita IVA o è società.

Le fonti ufficiali recenti dell'Agenzia descrivono però la disciplina delle locazioni brevi come riferita a contratti stipulati da persone fisiche fuori dall'esercizio di impresa.

**Conclusione operativa:** il software deve prevedere uno stato requires_tax_validation e non automatizzare in modo cieco queste casistiche finché non vengono chiuse.

### 20.2 Alert n. 2 - Ritenuta e documento non sono la stessa cosa

Il progetto rischia di confondere:

- data checkout
- data documento
- data ritenuta
- data payout

Queste date vanno distinte nel modello dati.

### 20.3 Alert n. 3 - F24 e CU non sono semplici stampe

Se il prodotto deve davvero farle e magari inviarle, servono:

- tracciati corretti
- controlli di validazione
- versioning annuale modelli
- esiti e reinvii
- audit trail

## 21\. Scelta consigliata per MVP

### MVP consigliato

- ingestione batch da channel manager / CSV
- canonical booking model
- motore split economico
- ledger ritenute
- rendiconto proprietario
- generazione F24 in output controllato
- generazione CU in output controllato
- dataset 770 per commercialista
- modulo configurazione regole documentali
- audit completo

### Non mettere nel MVP se non chiarito prima

- filing telematico diretto F24
- filing telematico diretto CU
- automazione definitiva della fatturazione guest
- automatismi rigidi su checkout come trigger unico

## 22\. Dati e tabelle minime da prevedere a database

### Anagrafiche

- property_managers
- owners
- properties
- guests
- tax_profiles
- document_profiles

### Prenotazioni

- bookings
- booking_events
- booking_amounts
- booking_channels
- payout_events

### Fiscale

- withholding_events
- withholding_batches
- fiscal_periods
- owner_tax_summaries

### Documentale

- documents
- document_lines
- document_triggers
- document_delivery_events

### Adempimenti

- f24_batches
- f24_lines
- f24_submissions
- cu_records
- cu_submissions
- filing_credentials_registry
- filing_audit_logs

## 23\. Decision log da tenere vivo

Aggiornare questo blocco ogni volta che una scelta viene chiusa.

### Da decidere

- ☐ Il PM fattura il guest?
- ☐ Quale evento fa nascere il documento?
- ☐ Quanti giorni massimi ci sono per emetterlo?
- ☐ Occorre attendere il payout OTA?
- ☐ Serve realtime con channel manager?
- ☐ Il prodotto invia davvero F24?
- ☐ Il prodotto invia davvero CU?
- ☐ Il caso owner con partita IVA / società è dentro o fuori il perimetro del prodotto?

### Già deciso

- ☒ Il prodotto va pensato come PMS fiscale, non come channel manager
- ☒ Il prodotto deve poter generare F24 e CU
- ☒ Le integrazioni con channel manager sono un punto strutturale del progetto
- ☒ Le date fiscali e documentali non vanno fuse nello stesso campo

## 24\. Conclusione pratica

Per funzionare davvero, Sostituto in Cloud non deve essere sviluppato come "tool che calcola il 21%".

Deve essere sviluppato come **sistema fiscale-event driven**, con queste caratteristiche:

- ingestione da ecosistema esterno
- motore di regole configurabile
- separazione tra evento economico, evento documentale ed evento fiscale
- generazione adempimenti
- audit trail completo

La priorità assoluta, prima di coding serio, è chiudere con il commercialista queste tre domande:

- il PM deve fare fattura al guest oppure no?
- qual è l'evento corretto che fa nascere documento e ritenuta?
- il payout OTA è solo un evento di cassa o è anche l'evento fiscale da usare?

Email:

- **Obbligo di fatturazione verso il turista**

Il property manager è proprio obbligato ad emettere fattura al turista oppure può emettere una semplice ricevuta non fiscale (senza codice fiscale dell'ospite)?

- **Tempistiche di emissione dei documenti**

Ci sono vincoli temporali specifici per l'emissione dei documenti (fattura o ricevuta) nelle locazioni brevi gestite da un property manager?

In particolare:

Sappiamo che per le fatture IVA esistono regole generali legate al momento di effettuazione dell'operazione:

Vorremmo capire se queste regole si applicano anche a questo modello operativo.

- - il documento deve essere emesso al momento del soggiorno / checkout?
    - oppure può essere emesso quando il property manager riceve il pagamento dalla OTA?
    - oppure è possibile emetterlo nel mese successivo al pagamento ota?
    - fattura immediata entro 12 giorni
    - **fattura differita entro il 15 del mese successivo &lt;<chiediamo&gt;>**

- **Ambito di utilizzo del gestionale**

Il gestionale nasce per gestire i casi in cui il property manager opera come sostituto d'imposta nelle locazioni brevi.

In questo scenario normalmente i proprietari sono persone fisiche senza partita IVA. &lt;<va bene, per ora lavoriamo su questa tipologia di clienti finali&gt;>

Dobbiamo prevedere che il sistema gestisca anche casi in cui il proprietario

- - ha partita IVA
    - oppure è una società?

Il PM e' in regime ordinario (no forfettario)

- [**Cedolare Secca**](https://www.google.com/search?q=Cedolare+Secca&rlz=1C1ONGR_itIT1180IT1180&oq=quando+scatta+l%27obbligo+partita+iva+locazioni+turistiche&gs_lcrp=EgZjaHJvbWUyCQgAEEUYORigATIHCAEQIRigATIHCAIQIRigAdIBCTE0MDc0ajBqN6gCALACAA&sourceid=chrome&ie=UTF-8&mstk=AUtExfAvSThNuTRP-YUi01EAFnWrh15acKs0er7_SLc0baEQFA46tbP2G0AWWBeja9CPKUDAwD_9grf8r3jSut7ErCMCgB1iOdBtLuVgZnno74Fsy73n-ZABoe12FjKOycjJvnPWO8kMz7Pg82XXDBePl88GqZDMsYPc8VOP6k2evhRDRKY&csui=3&ved=2ahUKEwjg09f3oaSTAxVpyAIHHWXuOksQgK4QegQIAxAE)**:** Anche se si ha una P.IVA per l'attività, dal 2° immobile in poi, la cedolare secca applicabile è al 26% (mentre per il primo immobile resta al 21%). Sceglie il PM/CLIENTE qual è il primo ed il secondo? Ordine cronologico (quindi viene deciso in anagrafica immobile)
- Il PM ovviamente potrà essere in regime forfettario oppure SRL (non può essere altro) ovviamente ricevute potranno essere quindi con iva al 22 o esenti
    - "ATTENZIONE: SE IL PM E' UN FORFETTARIO VERSA IVA DA REVERSE CHARGE SEMPRE"
- Il 770 va calcolato?
- RICEVUTA DEVE ESSERE MANDATA ALL'ADE, marche da bollo
- Tempo parametrico per appartamento dal check-out (15 gg dal checkout) non possibile in quanto non abbiamo info su checkin e checkout (1gg airbnb pagamento, 5-6gg pagamento booking, legge impone fatturazione entro 14gg dal pagamento OTA)
- Ipotizziamo caricamento ricevuta OTA + alloggiati web (prima dell'integrazione con i CM)
- Cav (casa affitto vacanza) IVA al 10% (non le gestiamo in questo momento)
- Lt (locazione turistica)