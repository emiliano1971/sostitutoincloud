BOOKING
  │
  ├─► RICEVUTA OWNER (fiscal_document tipo='ricevuta')
  │     │
  │     └─► WITHHOLDING_LEDGER
  │           fk_booking_id
  │           canone_locazione
  │           ritenuta_amount
  │           stato = 'da_versare'
  │           fk_f24_record_id = NULL
  │
  ├─► FATTURA PM (fiscal_document tipo='fattura_pm')
  │
  └─► BOOKING stato = 'doc_issued' 
  
  
WITHHOLDING_LEDGER (da_versare)
│
└─► GENERA F24 (f24_record)
    fk_tenant_id
    periodo_mese / periodo_anno
    total_amount = Σ ritenute
    stato = 'ready'
    │
    └─► WITHHOLDING_LEDGER
          stato = 'versata'
          fk_f24_record_id = F24_ID
          │
          ├─► [F24 NON PAGATO]
          │     F24.stato = 'ready'
          │     │
          │     └─► NDC sulla fattura
          │           │
          │           ├─► WITHHOLDING_LEDGER riga originale
          │           │     stato = 'stornata'
          │           │     fk_ndc_id = NDC_ID
          │           │     fk_f24_record_id = NULL
          │           │     ← tolta dall'F24
          │           │
          │           ├─► F24 ricalcolato
          │           │     total_amount -= ritenuta stornata
          │           │
          │           ├─► RICEVUTA annullata
          │           │     stato = 'annullata'
          │           │
          │           └─► BOOKING stato = 'stornata'
          │
          └─► [F24 PAGATO]
                F24.stato = 'paid'
                │
                └─► NDC sulla fattura
                      │
                      ├─► WITHHOLDING_LEDGER riga originale
                      │     stato = 'versata' (invariata)
                      │     fk_ndc_id = NDC_ID
                      │     ← resta nell'F24 pagato
                      │
                      ├─► WITHHOLDING_LEDGER nuova riga
                      │     stato = 'credito_imposta'
                      │     ritenuta_amount = -X (negativo)
                      │     fk_ndc_id = NDC_ID
                      │     fk_f24_record_id = NULL
                      │
                      ├─► RICEVUTA annullata
                      │     stato = 'annullata'
                      │
                      └─► BOOKING stato = 'stornata'
                          
ANNULLAMENTO NDC
│
├─► [SE F24 NON ERA PAGATO]
│     WITHHOLDING_LEDGER
│       stato = 'da_versare'
│       fk_ndc_id = NULL
│       fk_f24_record_id = NULL
│     RICEVUTA → stato ripristinato
│     BOOKING → stato ripristinato
│
├─► [SE F24 ERA PAGATO]
│     WITHHOLDING_LEDGER credito
│       eliminata
│     RICEVUTA → stato ripristinato
│     BOOKING → stato ripristinato
│
└─► [SE CREDITO GIÀ COMPENSATO]
    → ANNULLAMENTO BLOCCATO (400)    
    
SETTLEMENT (liquidazione)
├─► Prende booking in stato 'doc_issued'
│     ← 'stornata' esclusa
│     ← withholding_ledger con
│       fk_ndc_id IS NULL
│
└─► settlement_booking
    fk_settlement_id
    fk_booking_id

CU (certificazione unica)
├─► Prende withholding_ledger con
│     fk_ndc_id IS NULL
│     stato != 'stornata'
│
└─► cu_record per owner + anno                         
