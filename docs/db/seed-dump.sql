--
-- PostgreSQL database dump
--

\restrict hIjce423m7i6ngBhZeYmzx9m1vDhPOmFl05oXy5pPm1I7uUFBTW5ZMEAxQBSmLT

-- Dumped from database version 18.4 (Ubuntu 18.4-1.pgdg22.04+1)
-- Dumped by pg_dump version 18.4 (Ubuntu 18.4-1.pgdg22.04+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: canale_ota; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.canale_ota (id, codice, nome, commissione_default_pct, tassa_soggiorno_inclusa, attivo, created_at, updated_at, tourist_tax_collection) FROM stdin;
1	airbnb	Airbnb	15.00	t	t	2026-05-26 17:00:19.654349	2026-05-26 17:00:19.654349	contanti
5	tripadvisor	TripAdvisor	3.00	f	t	2026-05-26 17:00:19.654349	2026-05-26 17:00:19.654349	contanti
6	google_travel	Google Travel	0.00	f	t	2026-05-26 17:00:19.654349	2026-05-26 17:00:19.654349	contanti
2	booking	Booking.com	18.00	f	t	2026-05-26 17:00:19.654349	2026-06-01 16:54:07.459813	payment_link
3	vrbo	Vrbo	8.00	f	t	2026-05-26 17:00:19.654349	2026-06-01 16:54:07.459813	payment_link
4	expedia	Expedia	15.00	f	t	2026-05-26 17:00:19.654349	2026-06-01 16:54:07.459813	payment_link
7	diretto	Diretto	0.00	f	f	2026-05-26 17:00:19.654349	2026-06-01 17:04:22.567022	contanti
\.


--
-- Data for Name: codice_tributo; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.codice_tributo (id, codice, descrizione, attivo, created_at, updated_at) FROM stdin;
1	1919	Ritenute operate sui corrispettivi dovuti dai condomini — locazioni brevi art. 4 DL 50/2017	t	2026-05-26 17:00:19.656818	2026-05-26 17:00:19.656818
\.


--
-- Data for Name: regime_fiscale; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.regime_fiscale (id, codice, descrizione, attivo, created_at, updated_at, metadata) FROM stdin;
1	cedolare_secca	Cedolare secca	t	2026-05-26 17:00:19.653532	2026-05-26 17:00:19.653532	REGIME_FISCALE
2	iva_10	IVA 10%	t	2026-05-26 17:00:19.653532	2026-05-26 17:00:19.653532	REGIME_FISCALE
3	ordinario	Regime ordinario	t	2026-05-26 17:00:19.653532	2026-05-26 17:00:19.653532	REGIME_FISCALE
4	N1	Escluse art.13 (forfettario)	t	2026-06-22 16:32:23.128811	2026-06-22 16:32:23.128811	NATURA_IVA
5	N2.1	Fuori campo IVA art.4 D.L.50/2017	t	2026-06-22 16:32:23.128811	2026-06-22 16:32:23.128811	NATURA_IVA
6	N2.2	Fuori campo IVA altri casi	t	2026-06-22 16:32:23.128811	2026-06-22 16:32:23.128811	NATURA_IVA
7	N3.5	Non imponibili regime margine	t	2026-06-22 16:32:23.128811	2026-06-22 16:32:23.128811	NATURA_IVA
8	N4	Esenti	t	2026-06-22 16:32:23.128811	2026-06-22 16:32:23.128811	NATURA_IVA
9	N6.1	Inversione contabile rottami	t	2026-06-22 16:32:23.128811	2026-06-22 16:32:23.128811	NATURA_IVA
10	0	Esente / Fuori campo IVA	t	2026-06-22 16:32:23.130116	2026-06-22 16:32:23.130116	ALIQUOTA_IVA
11	10	IVA 10% (CAV)	t	2026-06-22 16:32:23.130116	2026-06-22 16:32:23.130116	ALIQUOTA_IVA
12	22	IVA 22% (ordinaria)	t	2026-06-22 16:32:23.130116	2026-06-22 16:32:23.130116	ALIQUOTA_IVA
13	RF01	Ordinario	t	2026-06-22 16:32:23.130561	2026-06-22 16:32:23.130561	REGIME_FISCALE_PM
14	RF19	Forfettario (art.1, commi 54-89, L.190/2014)	t	2026-06-22 16:32:23.130561	2026-06-22 16:32:23.130561	REGIME_FISCALE_PM
\.


--
-- Data for Name: tenant; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.tenant (id, legal_name, display_name, tax_code, vat_number, stato, administrative_email, pec, phone, legal_address, activated_at, created_at, updated_at) FROM stdin;
1	Casa Vacanze Italia SRL	Casa Vacanze Italia	CVITRL80A01H501Z	12345678901	active	admin@casavacanze.it	casavacanze@pec.it	+39 06 1234567	Via Roma 1, 00100 Roma RM	2024-01-20	2024-01-15 00:00:00	2026-06-01 15:50:04.828568
\.


--
-- Data for Name: owner_profile; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.owner_profile (id, fk_tenant_id, owner_type, first_name, last_name, legal_name, tax_code, vat_number, fk_regime_fiscale_id, email, phone, iban, attivo, created_at, updated_at) FROM stdin;
3	1	persona_fisica	Luigi	Verdi	\N	VRDLGU90B02F205X	\N	1	luigi.verdi@email.it	+39 340 1234567	IT40B0503411101000000001234	t	2026-06-01 10:15:50.250653	2026-06-01 10:15:50.250653
4	1	persona_fisica	Emiliano	Zerbinati	\N	ZRBMLN71C25F994V	\N	2	emiliano.zerbinati@gaviatech.it	\N	\N	t	2026-06-01 11:24:09.238768	2026-06-01 12:33:06.420167
2	1	persona_fisica	Mario	Rossi	\N	RSSMRA80A01H501Z	\N	1	mario.rossi@email.it	+39 333 123456789	IT60X0542811101000000999999	t	2026-06-01 10:15:14.286242	2026-06-16 12:56:54.007786
1	1	persona_fisica	Anna	Moretti	\N	MRTANN85A41H501X	\N	1	anna.moretti@email.it	+39 333 0000000	IT60X0542811101000000000001	t	2024-01-20 00:00:00	2026-06-23 11:02:54.904083
\.


--
-- Data for Name: tipo_immobile; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.tipo_immobile (id, codice, descrizione, attivo, created_at, updated_at) FROM stdin;
1	LT	Locazione Turistica	t	2026-05-26 17:00:19.655207	2026-05-26 17:00:19.655207
2	BB	Bed & Breakfast	t	2026-05-26 17:00:19.655207	2026-05-26 17:00:19.655207
3	AC	Affittacamere	t	2026-05-26 17:00:19.655207	2026-05-26 17:00:19.655207
4	CV	Casa Vacanze	t	2026-05-26 17:00:19.655207	2026-05-26 17:00:19.655207
5	AGR	Agriturismo	t	2026-05-26 17:00:19.655207	2026-05-26 17:00:19.655207
\.


--
-- Data for Name: utente; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.utente (id, fk_tenant_id, email, first_name, last_name, password_hash, ruolo, attivo, last_login, created_at, updated_at, fk_owner_id) FROM stdin;
1	1	admin@casavacanze.it	Laura	Bianchi	$2a$10$VkdKwWIQjwaH4Z3LFI0yRejkpiMg2o3wAfaqEEc73lRJFDgQR3p4G	tenant_admin	t	\N	2024-01-20 00:00:00	2026-05-28 17:30:30.710899	\N
2	1	proprietario@email.it	Anna	Moretti	$2a$10$VkdKwWIQjwaH4Z3LFI0yRejkpiMg2o3wAfaqEEc73lRJFDgQR3p4G	owner_user	t	\N	2024-01-20 00:00:00	2026-05-28 17:30:30.710899	1
3	\N	superadmin@sostitutoincloud.it	Super	Admin	$2a$10$/e2PE1TOqU1jaHFBx2iQwuHg5FO2vMkYJN7J8bIFd0Hw7DeMGAJqG	super_admin	t	\N	2026-06-01 14:54:15.763374	2026-06-01 14:57:44.268659	\N
5	1	pm@casavacanze.it	Giovanni	Verdi	$2a$10$xvoVq0oUzKeB/0u3O8uSuewVGPZlJHpblP3AXRRPlI9dcIAWAcVxS	pm_user	t	\N	2026-06-16 11:52:00.412034	2026-06-16 11:52:00.412034	\N
\.


--
-- Data for Name: property; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.property (id, fk_tenant_id, fk_owner_id, fk_pm_user_id, fk_tipo_immobile_id, internal_code, display_name, address, city, region, cin_code, attivo, created_at, updated_at, primo_immobile) FROM stdin;
4	1	2	1	1	ROM-003	Appartamento Test		Roma	Lazio	\N	t	2026-06-01 10:17:07.89155	2026-06-25 08:46:29.906599	t
2	1	1	1	1	ROM-002	Loft Monti	Via dei Serpenti 80	Roma	Lazio	IT058091C5E6F7G8H9	t	2024-01-25 00:00:00	2026-06-25 08:46:37.511909	t
5	1	4	1	1	MONZU-001	Cascina di Monzù 	Via Asdrubale	BOlogna	Emilia Romagna	\N	t	2026-06-01 11:29:13.351526	2026-06-25 09:41:49.394711	f
8	1	2	1	1	FE-01	Ferrara Vittoria	Domus Art | Vivi l'Arte nel Centro di Ferrara	Ferrarar	Emilia	\N	t	2026-07-06 17:13:31.621211	2026-07-06 17:13:31.621211	f
6	1	4	1	1	VEN-001	Ca' Serenella		Venezia		\N	t	2026-06-24 15:23:24.498216	2026-07-06 17:24:43.255786	t
1	1	1	1	1	ROM-001	Appartamento Trastevere		Roma	Lazio	IT058091C1A2B3C4D5	t	2024-01-25 00:00:00	2026-07-06 17:27:49.767447	t
\.


--
-- Data for Name: property_ota_code; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.property_ota_code (id, fk_property_id, fk_canale_ota_id, external_id, created_at, updated_at) FROM stdin;
6	8	1	Domus Art | Vivi l'Arte nel Centro di Ferrara	2026-07-06 17:13:31.635224	2026-07-06 17:13:31.635224
7	8	2	Domus Art | Vivi l'Arte nel Centro di Ferrara	2026-07-06 17:13:31.637334	2026-07-06 17:13:31.637334
8	8	3	Domus Art | Vivi l'Arte nel Centro di Ferrara	2026-07-06 17:13:31.638698	2026-07-06 17:13:31.638698
9	8	5	Domus Art | Vivi l'Arte nel Centro di Ferrara	2026-07-06 17:13:31.639918	2026-07-06 17:13:31.639918
10	8	4	Domus Art | Vivi l'Arte nel Centro di Ferrara	2026-07-06 17:13:31.641173	2026-07-06 17:13:31.641173
13	6	1	Ca' Serenella	2026-07-06 17:24:43.26042	2026-07-06 17:24:43.26042
14	6	2	Ca' Serenella	2026-07-06 17:24:43.262141	2026-07-06 17:24:43.262141
15	6	3	Ca' Serenella	2026-07-06 17:24:43.263934	2026-07-06 17:24:43.263934
16	6	5	Ca' Serenella	2026-07-06 17:24:43.271011	2026-07-06 17:24:43.271011
17	6	4	Ca' Serenella	2026-07-06 17:24:43.272551	2026-07-06 17:24:43.272551
\.


--
-- Data for Name: scenario_fiscale; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.scenario_fiscale (id, codice, descrizione, attivo, created_at, updated_at) FROM stdin;
1	scenario_A	Sostituto d'imposta PM: fattura PM + ricevuta owner (cedolare secca)	t	2026-05-26 17:02:39.908628	2026-05-26 17:02:39.908628
\.


--
-- Data for Name: stato_documento; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.stato_documento (id, codice, descrizione, is_error, finale, attivo, created_at, updated_at) FROM stdin;
1	draft	Bozza	f	f	t	2026-05-26 17:00:19.652594	2026-05-26 17:00:19.652594
2	ready	Pronto per invio SDI	f	f	t	2026-05-26 17:00:19.652594	2026-05-26 17:00:19.652594
3	sent_sdi	Inviato a SDI	f	f	t	2026-05-26 17:00:19.652594	2026-05-26 17:00:19.652594
4	accepted	Accettato da SDI	f	t	t	2026-05-26 17:00:19.652594	2026-05-26 17:00:19.652594
5	rejected	Rifiutato da SDI	t	t	t	2026-05-26 17:00:19.652594	2026-05-26 17:00:19.652594
6	error	Errore generico	t	f	t	2026-05-26 17:00:19.652594	2026-05-26 17:00:19.652594
\.


--
-- Data for Name: tenant_settings; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.tenant_settings (id, fk_tenant_id, withholding_rate_primary, withholding_rate_secondary, codice_tributo_f24, document_window_days, cedolare_secca_enabled, sdi_auto_send, deroga_ricevuta_enabled, numerazione_automatica, alert_scadenze_documenti, alert_scadenze_f24, notifiche_email, created_at, updated_at, bollo_importo, bollo_soglia, bollo_addebitato_cliente, regime_fiscale_pm, natura_iva_esente) FROM stdin;
1	1	21.00	26.00	1919	15	t	t	f	t	t	t	t	2026-05-29 17:51:23.079977	2026-06-23 16:28:57.67253	2.00	77.47	t	RF01	N2.1
\.


--
-- Data for Name: tipo_documento; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.tipo_documento (id, codice, descrizione, richiede_iva, trasmesso_sdi, attivo, created_at, updated_at) FROM stdin;
1	fattura	Fattura elettronica	t	t	t	2026-05-26 17:00:19.656004	2026-05-26 17:00:19.656004
2	ricevuta	Ricevuta semplice (fuori campo IVA)	f	f	t	2026-05-26 17:00:19.656004	2026-05-26 17:00:19.656004
3	nota_credito	Nota di credito elettronica	t	t	t	2026-05-26 17:00:19.656004	2026-05-26 17:00:19.656004
\.


--
-- Name: canale_ota_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.canale_ota_id_seq', 7, true);


--
-- Name: codice_tributo_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.codice_tributo_id_seq', 1, true);


--
-- Name: owner_profile_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.owner_profile_id_seq', 4, true);


--
-- Name: property_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.property_id_seq', 8, true);


--
-- Name: property_ota_code_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.property_ota_code_id_seq', 19, true);


--
-- Name: regime_fiscale_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.regime_fiscale_id_seq', 14, true);


--
-- Name: scenario_fiscale_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.scenario_fiscale_id_seq', 1, true);


--
-- Name: stato_documento_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.stato_documento_id_seq', 6, true);


--
-- Name: tenant_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tenant_id_seq', 1, true);


--
-- Name: tenant_settings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tenant_settings_id_seq', 14, true);


--
-- Name: tipo_documento_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tipo_documento_id_seq', 3, true);


--
-- Name: tipo_immobile_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tipo_immobile_id_seq', 5, true);


--
-- Name: utente_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.utente_id_seq', 5, true);


--
-- PostgreSQL database dump complete
--

\unrestrict hIjce423m7i6ngBhZeYmzx9m1vDhPOmFl05oXy5pPm1I7uUFBTW5ZMEAxQBSmLT

