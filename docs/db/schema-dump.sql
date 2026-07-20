--
-- PostgreSQL database dump
--

\restrict uVKuEPwEt1EpIVXyTZ1DARBTTpL1ymLwdUK1kYh4L1jaRnxQCmHBFD72AU0F1EJ

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
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

-- *not* creating schema, since initdb creates it


--
-- Name: cu_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.cu_status AS ENUM (
    'draft',
    'generated',
    'sent',
    'delivered'
);


--
-- Name: f24_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.f24_status AS ENUM (
    'draft',
    'ready',
    'sent',
    'paid',
    'error'
);


--
-- Name: owner_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.owner_type AS ENUM (
    'persona_fisica',
    'piva',
    'societa'
);


--
-- Name: payment_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.payment_status AS ENUM (
    'pending',
    'received',
    'failed'
);


--
-- Name: settlement_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.settlement_status AS ENUM (
    'pending',
    'calculated',
    'approved',
    'paid'
);


--
-- Name: tenant_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.tenant_status AS ENUM (
    'draft',
    'active',
    'suspended',
    'closed'
);


--
-- Name: tourist_tax_collection; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.tourist_tax_collection AS ENUM (
    'contanti',
    'payment_link',
    'altro'
);


--
-- Name: user_role; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.user_role AS ENUM (
    'super_admin',
    'tenant_admin',
    'pm_user',
    'owner_user'
);


--
-- Name: set_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_log (
    id integer NOT NULL,
    fk_tenant_id integer,
    fk_utente_id integer,
    user_email character varying(150) NOT NULL,
    action character varying(100) NOT NULL,
    entity_type character varying(50) NOT NULL,
    entity_id integer,
    details text,
    ip_address character varying(45) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: audit_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_log_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.audit_log_id_seq OWNED BY public.audit_log.id;


--
-- Name: booking; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.booking (
    id integer NOT NULL,
    fk_tenant_id integer NOT NULL,
    fk_property_id integer NOT NULL,
    fk_canale_ota_id integer,
    fk_scenario_fiscale_id integer,
    external_booking_id character varying(100),
    guest_name character varying(150) NOT NULL,
    guest_tax_code character varying(20),
    checkin_date date NOT NULL,
    checkout_date date NOT NULL,
    nights smallint NOT NULL,
    guests smallint NOT NULL,
    gross_amount numeric(10,2) NOT NULL,
    ota_commission_amount numeric(10,2) DEFAULT 0 NOT NULL,
    cleaning_amount numeric(10,2) DEFAULT 0 NOT NULL,
    pm_fee_amount numeric(10,2) DEFAULT 0 NOT NULL,
    owner_net_amount numeric(10,2) NOT NULL,
    withholding_amount numeric(10,2) DEFAULT 0 NOT NULL,
    tourist_tax_amount numeric(10,2) DEFAULT 0 NOT NULL,
    tourist_tax_included_in_gross boolean DEFAULT false NOT NULL,
    tourist_tax_collection public.tourist_tax_collection DEFAULT 'contanti'::public.tourist_tax_collection NOT NULL,
    fk_stato_prenotazione_id integer DEFAULT 1 NOT NULL,
    payment_status public.payment_status DEFAULT 'pending'::public.payment_status NOT NULL,
    settlement_status public.settlement_status DEFAULT 'pending'::public.settlement_status NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    aliquota_ritenuta numeric(5,2) DEFAULT 21.00 NOT NULL,
    fk_owner_id integer,
    CONSTRAINT chk_checkout_after_checkin CHECK ((checkout_date > checkin_date)),
    CONSTRAINT chk_guests_positive CHECK ((guests > 0)),
    CONSTRAINT chk_nights_positive CHECK ((nights > 0))
);


--
-- Name: booking_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.booking_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: booking_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.booking_id_seq OWNED BY public.booking.id;


--
-- Name: canale_ota; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.canale_ota (
    id integer NOT NULL,
    codice character varying(30) NOT NULL,
    nome character varying(100) NOT NULL,
    commissione_default_pct numeric(5,2) DEFAULT 0 NOT NULL,
    tassa_soggiorno_inclusa boolean DEFAULT false NOT NULL,
    attivo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    tourist_tax_collection character varying(20) DEFAULT 'contanti'::character varying
);


--
-- Name: canale_ota_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.canale_ota_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: canale_ota_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.canale_ota_id_seq OWNED BY public.canale_ota.id;


--
-- Name: codice_tributo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.codice_tributo (
    id integer NOT NULL,
    codice character(4) NOT NULL,
    descrizione character varying(250) NOT NULL,
    attivo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: codice_tributo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.codice_tributo_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: codice_tributo_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.codice_tributo_id_seq OWNED BY public.codice_tributo.id;


--
-- Name: cu_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cu_record (
    id integer NOT NULL,
    fk_tenant_id integer NOT NULL,
    fk_owner_id integer NOT NULL,
    tax_year smallint NOT NULL,
    total_compensi numeric(10,2) DEFAULT 0 NOT NULL,
    total_ritenute numeric(10,2) DEFAULT 0 NOT NULL,
    stato public.cu_status DEFAULT 'draft'::public.cu_status NOT NULL,
    generated_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    total_imponibile numeric(10,2) DEFAULT 0,
    sent_at timestamp without time zone
);


--
-- Name: cu_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.cu_record_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cu_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.cu_record_id_seq OWNED BY public.cu_record.id;


--
-- Name: f24_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.f24_record (
    id integer NOT NULL,
    fk_tenant_id integer NOT NULL,
    fk_codice_tributo_id integer NOT NULL,
    period character(7) NOT NULL,
    total_amount numeric(10,2) NOT NULL,
    withholdings_count smallint DEFAULT 0 NOT NULL,
    stato public.f24_status DEFAULT 'draft'::public.f24_status NOT NULL,
    deadline_date date NOT NULL,
    payment_date date,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    periodo_mese smallint,
    periodo_anno smallint,
    reference_year smallint,
    CONSTRAINT chk_f24_period_fmt CHECK ((period ~ '^\d{4}-(0[1-9]|1[0-2])$'::text))
);


--
-- Name: f24_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.f24_record_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: f24_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.f24_record_id_seq OWNED BY public.f24_record.id;


--
-- Name: fiscal_document; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fiscal_document (
    id integer NOT NULL,
    fk_tenant_id integer NOT NULL,
    fk_booking_id integer NOT NULL,
    fk_tipo_documento_id integer NOT NULL,
    fk_sdi_esito_id integer,
    document_number character varying(30) NOT NULL,
    issue_date date NOT NULL,
    recipient_name character varying(150) NOT NULL,
    recipient_tax_code character varying(20),
    total_amount numeric(10,2) NOT NULL,
    vat_amount numeric(10,2) DEFAULT 0 NOT NULL,
    fk_stato_documento_id integer DEFAULT 1 NOT NULL,
    sdi_identifier character varying(50),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    imponibile numeric(10,2),
    ritenuta_amount numeric(10,2),
    bollo_amount numeric(10,2),
    aliquota_iva numeric(5,2) DEFAULT 0.00 NOT NULL,
    fk_documento_collegato_id integer,
    canone_locazione numeric(10,2),
    fk_owner_id integer
);


--
-- Name: fiscal_document_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.fiscal_document_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: fiscal_document_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.fiscal_document_id_seq OWNED BY public.fiscal_document.id;


--
-- Name: import_template; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.import_template (
    id integer NOT NULL,
    fk_tenant_id integer NOT NULL,
    nome character varying(100) NOT NULL,
    descrizione character varying(255),
    header_row integer DEFAULT 0 NOT NULL,
    booking_mapping jsonb DEFAULT '{}'::jsonb NOT NULL,
    guest_mapping jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: import_template_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.import_template_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: import_template_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.import_template_id_seq OWNED BY public.import_template.id;


--
-- Name: owner_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.owner_profile (
    id integer NOT NULL,
    fk_tenant_id integer NOT NULL,
    owner_type public.owner_type DEFAULT 'persona_fisica'::public.owner_type NOT NULL,
    first_name character varying(80),
    last_name character varying(80),
    legal_name character varying(200),
    tax_code character(16) NOT NULL,
    vat_number character(11),
    fk_regime_fiscale_id integer DEFAULT 1 NOT NULL,
    email character varying(150),
    phone character varying(20),
    iban character varying(34),
    attivo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: owner_profile_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.owner_profile_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: owner_profile_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.owner_profile_id_seq OWNED BY public.owner_profile.id;


--
-- Name: property; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.property (
    id integer NOT NULL,
    fk_tenant_id integer NOT NULL,
    fk_owner_id integer NOT NULL,
    fk_pm_user_id integer,
    fk_tipo_immobile_id integer,
    internal_code character varying(20) NOT NULL,
    display_name character varying(150) NOT NULL,
    address character varying(200) NOT NULL,
    city character varying(80) NOT NULL,
    region character varying(80) NOT NULL,
    cin_code character varying(25),
    attivo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    primo_immobile boolean DEFAULT false NOT NULL
);


--
-- Name: property_contract_rule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.property_contract_rule (
    id integer NOT NULL,
    fk_property_id integer NOT NULL,
    fk_tenant_id integer NOT NULL,
    fk_canale_ota_id integer,
    tipo character varying(30) NOT NULL,
    calc_mode character varying(30) NOT NULL,
    valore numeric(10,2) DEFAULT 0 NOT NULL,
    is_remainder boolean DEFAULT false NOT NULL,
    ordine integer DEFAULT 0 NOT NULL,
    attivo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: property_contract_rule_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.property_contract_rule_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: property_contract_rule_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.property_contract_rule_id_seq OWNED BY public.property_contract_rule.id;


--
-- Name: property_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.property_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: property_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.property_id_seq OWNED BY public.property.id;


--
-- Name: property_ota_code; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.property_ota_code (
    id integer NOT NULL,
    fk_property_id integer NOT NULL,
    fk_canale_ota_id integer NOT NULL,
    external_id character varying(60) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: property_ota_code_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.property_ota_code_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: property_ota_code_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.property_ota_code_id_seq OWNED BY public.property_ota_code.id;


--
-- Name: regime_fiscale; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.regime_fiscale (
    id integer NOT NULL,
    codice character varying(30) NOT NULL,
    descrizione character varying(150) NOT NULL,
    attivo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    metadata character varying(50) DEFAULT 'REGIME_FISCALE'::character varying NOT NULL
);


--
-- Name: regime_fiscale_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.regime_fiscale_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: regime_fiscale_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.regime_fiscale_id_seq OWNED BY public.regime_fiscale.id;


--
-- Name: regola_tassa_soggiorno; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.regola_tassa_soggiorno (
    id integer NOT NULL,
    comune character varying(100) NOT NULL,
    provincia character(2) NOT NULL,
    importo_per_notte numeric(5,2) NOT NULL,
    max_notti smallint DEFAULT 7 NOT NULL,
    eta_esenzione smallint,
    valida_dal date NOT NULL,
    valida_al date,
    attivo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    region character varying(100),
    max_amount_per_person numeric(5,2),
    exemptions text,
    notes text,
    fk_tenant_id integer
);


--
-- Name: regola_tassa_soggiorno_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.regola_tassa_soggiorno_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: regola_tassa_soggiorno_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.regola_tassa_soggiorno_id_seq OWNED BY public.regola_tassa_soggiorno.id;


--
-- Name: scenario_fiscale; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.scenario_fiscale (
    id integer NOT NULL,
    codice character varying(30) NOT NULL,
    descrizione character varying(200) NOT NULL,
    attivo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: scenario_fiscale_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.scenario_fiscale_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: scenario_fiscale_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.scenario_fiscale_id_seq OWNED BY public.scenario_fiscale.id;


--
-- Name: sdi_esito; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sdi_esito (
    id integer NOT NULL,
    codice character(2) NOT NULL,
    descrizione character varying(150) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: sdi_esito_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sdi_esito_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sdi_esito_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sdi_esito_id_seq OWNED BY public.sdi_esito.id;


--
-- Name: settlement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.settlement (
    id integer NOT NULL,
    fk_tenant_id integer NOT NULL,
    fk_owner_id integer NOT NULL,
    period character(7) NOT NULL,
    total_amount numeric(10,2) NOT NULL,
    withholding_amount numeric(10,2) DEFAULT 0 NOT NULL,
    net_amount numeric(10,2) NOT NULL,
    stato public.settlement_status DEFAULT 'pending'::public.settlement_status NOT NULL,
    payment_date date,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    periodo_mese smallint,
    periodo_anno smallint,
    CONSTRAINT chk_settlement_period_fmt CHECK ((period ~ '^\d{4}-(0[1-9]|1[0-2])$'::text))
);


--
-- Name: settlement_booking; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.settlement_booking (
    id integer NOT NULL,
    fk_settlement_id integer NOT NULL,
    fk_booking_id integer NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: settlement_booking_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.settlement_booking_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: settlement_booking_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.settlement_booking_id_seq OWNED BY public.settlement_booking.id;


--
-- Name: settlement_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.settlement_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: settlement_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.settlement_id_seq OWNED BY public.settlement.id;


--
-- Name: stato_documento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stato_documento (
    id integer NOT NULL,
    codice character varying(30) NOT NULL,
    descrizione character varying(150) NOT NULL,
    is_error boolean DEFAULT false NOT NULL,
    finale boolean DEFAULT false NOT NULL,
    attivo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: stato_documento_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stato_documento_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stato_documento_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stato_documento_id_seq OWNED BY public.stato_documento.id;


--
-- Name: stato_prenotazione; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stato_prenotazione (
    id integer NOT NULL,
    codice character varying(30) NOT NULL,
    descrizione character varying(150) NOT NULL,
    finale boolean DEFAULT false NOT NULL,
    attivo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: stato_prenotazione_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stato_prenotazione_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stato_prenotazione_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stato_prenotazione_id_seq OWNED BY public.stato_prenotazione.id;


--
-- Name: tassa_fascia_eta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tassa_fascia_eta (
    id integer NOT NULL,
    fk_regola_id integer NOT NULL,
    label character varying(50) NOT NULL,
    min_age smallint NOT NULL,
    max_age smallint NOT NULL,
    reduction_pct smallint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: tassa_fascia_eta_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tassa_fascia_eta_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tassa_fascia_eta_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tassa_fascia_eta_id_seq OWNED BY public.tassa_fascia_eta.id;


--
-- Name: tassa_stagione; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tassa_stagione (
    id integer NOT NULL,
    fk_regola_id integer NOT NULL,
    label character varying(50) NOT NULL,
    start_month smallint NOT NULL,
    start_day smallint NOT NULL,
    end_month smallint NOT NULL,
    end_day smallint NOT NULL,
    reduction_pct smallint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: tassa_stagione_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tassa_stagione_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tassa_stagione_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tassa_stagione_id_seq OWNED BY public.tassa_stagione.id;


--
-- Name: tassa_zona; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tassa_zona (
    id integer NOT NULL,
    fk_regola_id integer NOT NULL,
    label character varying(100) NOT NULL,
    reduction_pct smallint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: tassa_zona_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tassa_zona_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tassa_zona_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tassa_zona_id_seq OWNED BY public.tassa_zona.id;


--
-- Name: tenant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant (
    id integer NOT NULL,
    legal_name character varying(200) NOT NULL,
    display_name character varying(100) NOT NULL,
    tax_code character(16) NOT NULL,
    vat_number character(11),
    stato public.tenant_status DEFAULT 'draft'::public.tenant_status NOT NULL,
    administrative_email character varying(150) NOT NULL,
    pec character varying(150),
    phone character varying(20),
    legal_address character varying(300) NOT NULL,
    activated_at date,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: tenant_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tenant_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tenant_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tenant_id_seq OWNED BY public.tenant.id;


--
-- Name: tenant_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tenant_settings (
    id integer NOT NULL,
    fk_tenant_id integer NOT NULL,
    withholding_rate_primary numeric(5,2) DEFAULT 21.00 NOT NULL,
    withholding_rate_secondary numeric(5,2) DEFAULT 26.00 NOT NULL,
    codice_tributo_f24 character varying(10) DEFAULT '1919'::character varying NOT NULL,
    document_window_days smallint DEFAULT 14 NOT NULL,
    cedolare_secca_enabled boolean DEFAULT true NOT NULL,
    sdi_auto_send boolean DEFAULT true NOT NULL,
    deroga_ricevuta_enabled boolean DEFAULT false NOT NULL,
    numerazione_automatica boolean DEFAULT true NOT NULL,
    alert_scadenze_documenti boolean DEFAULT true NOT NULL,
    alert_scadenze_f24 boolean DEFAULT true NOT NULL,
    notifiche_email boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    bollo_importo numeric(5,2) DEFAULT 2.00 NOT NULL,
    bollo_soglia numeric(10,2) DEFAULT 77.47 NOT NULL,
    bollo_addebitato_cliente boolean DEFAULT true NOT NULL,
    regime_fiscale_pm character varying(10) DEFAULT 'RF01'::character varying NOT NULL,
    natura_iva_esente character varying(10) DEFAULT 'N2.1'::character varying NOT NULL
);


--
-- Name: tenant_settings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tenant_settings_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tenant_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tenant_settings_id_seq OWNED BY public.tenant_settings.id;


--
-- Name: tipo_documento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tipo_documento (
    id integer NOT NULL,
    codice character varying(20) NOT NULL,
    descrizione character varying(100) NOT NULL,
    richiede_iva boolean DEFAULT false NOT NULL,
    trasmesso_sdi boolean DEFAULT false NOT NULL,
    attivo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: tipo_documento_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tipo_documento_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tipo_documento_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tipo_documento_id_seq OWNED BY public.tipo_documento.id;


--
-- Name: tipo_immobile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tipo_immobile (
    id integer NOT NULL,
    codice character varying(10) NOT NULL,
    descrizione character varying(100) NOT NULL,
    attivo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: tipo_immobile_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tipo_immobile_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tipo_immobile_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tipo_immobile_id_seq OWNED BY public.tipo_immobile.id;


--
-- Name: utente; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.utente (
    id integer NOT NULL,
    fk_tenant_id integer,
    email character varying(150) NOT NULL,
    first_name character varying(80) NOT NULL,
    last_name character varying(80) NOT NULL,
    password_hash character varying(255) NOT NULL,
    ruolo public.user_role NOT NULL,
    attivo boolean DEFAULT true NOT NULL,
    last_login timestamp without time zone,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    fk_owner_id integer
);


--
-- Name: utente_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.utente_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: utente_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.utente_id_seq OWNED BY public.utente.id;


--
-- Name: v_ricavi_mensili; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.v_ricavi_mensili AS
 SELECT b.fk_tenant_id,
    date_trunc('month'::text, (b.checkout_date)::timestamp with time zone) AS mese,
    sum(b.pm_fee_amount) AS ricavi_pm,
    sum(b.owner_net_amount) AS ricavi_ow,
    sum(b.ota_commission_amount) AS commissioni,
    sum(b.withholding_amount) AS ritenute
   FROM (public.booking b
     JOIN public.stato_prenotazione sp ON ((sp.id = b.fk_stato_prenotazione_id)))
  WHERE ((sp.codice)::text <> 'cancelled'::text)
  GROUP BY b.fk_tenant_id, (date_trunc('month'::text, (b.checkout_date)::timestamp with time zone));


--
-- Name: withholding_ledger; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.withholding_ledger (
    id integer NOT NULL,
    fk_tenant_id integer NOT NULL,
    fk_owner_id integer NOT NULL,
    fk_booking_id integer NOT NULL,
    fk_fiscal_document_id integer NOT NULL,
    periodo_mese smallint NOT NULL,
    periodo_anno smallint NOT NULL,
    canone_locazione numeric(10,2) NOT NULL,
    aliquota_ritenuta numeric(5,2) NOT NULL,
    ritenuta_amount numeric(10,2) NOT NULL,
    data_evento date NOT NULL,
    stato character varying(20) DEFAULT 'da_versare'::character varying NOT NULL,
    fk_f24_record_id integer,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: withholding_ledger_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.withholding_ledger_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: withholding_ledger_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.withholding_ledger_id_seq OWNED BY public.withholding_ledger.id;


--
-- Name: audit_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log ALTER COLUMN id SET DEFAULT nextval('public.audit_log_id_seq'::regclass);


--
-- Name: booking id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking ALTER COLUMN id SET DEFAULT nextval('public.booking_id_seq'::regclass);


--
-- Name: canale_ota id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.canale_ota ALTER COLUMN id SET DEFAULT nextval('public.canale_ota_id_seq'::regclass);


--
-- Name: codice_tributo id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.codice_tributo ALTER COLUMN id SET DEFAULT nextval('public.codice_tributo_id_seq'::regclass);


--
-- Name: cu_record id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cu_record ALTER COLUMN id SET DEFAULT nextval('public.cu_record_id_seq'::regclass);


--
-- Name: f24_record id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.f24_record ALTER COLUMN id SET DEFAULT nextval('public.f24_record_id_seq'::regclass);


--
-- Name: fiscal_document id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fiscal_document ALTER COLUMN id SET DEFAULT nextval('public.fiscal_document_id_seq'::regclass);


--
-- Name: import_template id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_template ALTER COLUMN id SET DEFAULT nextval('public.import_template_id_seq'::regclass);


--
-- Name: owner_profile id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.owner_profile ALTER COLUMN id SET DEFAULT nextval('public.owner_profile_id_seq'::regclass);


--
-- Name: property id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property ALTER COLUMN id SET DEFAULT nextval('public.property_id_seq'::regclass);


--
-- Name: property_contract_rule id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contract_rule ALTER COLUMN id SET DEFAULT nextval('public.property_contract_rule_id_seq'::regclass);


--
-- Name: property_ota_code id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_ota_code ALTER COLUMN id SET DEFAULT nextval('public.property_ota_code_id_seq'::regclass);


--
-- Name: regime_fiscale id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regime_fiscale ALTER COLUMN id SET DEFAULT nextval('public.regime_fiscale_id_seq'::regclass);


--
-- Name: regola_tassa_soggiorno id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regola_tassa_soggiorno ALTER COLUMN id SET DEFAULT nextval('public.regola_tassa_soggiorno_id_seq'::regclass);


--
-- Name: scenario_fiscale id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scenario_fiscale ALTER COLUMN id SET DEFAULT nextval('public.scenario_fiscale_id_seq'::regclass);


--
-- Name: sdi_esito id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sdi_esito ALTER COLUMN id SET DEFAULT nextval('public.sdi_esito_id_seq'::regclass);


--
-- Name: settlement id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settlement ALTER COLUMN id SET DEFAULT nextval('public.settlement_id_seq'::regclass);


--
-- Name: settlement_booking id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settlement_booking ALTER COLUMN id SET DEFAULT nextval('public.settlement_booking_id_seq'::regclass);


--
-- Name: stato_documento id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stato_documento ALTER COLUMN id SET DEFAULT nextval('public.stato_documento_id_seq'::regclass);


--
-- Name: stato_prenotazione id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stato_prenotazione ALTER COLUMN id SET DEFAULT nextval('public.stato_prenotazione_id_seq'::regclass);


--
-- Name: tassa_fascia_eta id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tassa_fascia_eta ALTER COLUMN id SET DEFAULT nextval('public.tassa_fascia_eta_id_seq'::regclass);


--
-- Name: tassa_stagione id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tassa_stagione ALTER COLUMN id SET DEFAULT nextval('public.tassa_stagione_id_seq'::regclass);


--
-- Name: tassa_zona id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tassa_zona ALTER COLUMN id SET DEFAULT nextval('public.tassa_zona_id_seq'::regclass);


--
-- Name: tenant id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant ALTER COLUMN id SET DEFAULT nextval('public.tenant_id_seq'::regclass);


--
-- Name: tenant_settings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_settings ALTER COLUMN id SET DEFAULT nextval('public.tenant_settings_id_seq'::regclass);


--
-- Name: tipo_documento id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_documento ALTER COLUMN id SET DEFAULT nextval('public.tipo_documento_id_seq'::regclass);


--
-- Name: tipo_immobile id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_immobile ALTER COLUMN id SET DEFAULT nextval('public.tipo_immobile_id_seq'::regclass);


--
-- Name: utente id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.utente ALTER COLUMN id SET DEFAULT nextval('public.utente_id_seq'::regclass);


--
-- Name: withholding_ledger id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.withholding_ledger ALTER COLUMN id SET DEFAULT nextval('public.withholding_ledger_id_seq'::regclass);


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);


--
-- Name: booking booking_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking
    ADD CONSTRAINT booking_pkey PRIMARY KEY (id);


--
-- Name: canale_ota canale_ota_codice_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.canale_ota
    ADD CONSTRAINT canale_ota_codice_key UNIQUE (codice);


--
-- Name: canale_ota canale_ota_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.canale_ota
    ADD CONSTRAINT canale_ota_pkey PRIMARY KEY (id);


--
-- Name: codice_tributo codice_tributo_codice_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.codice_tributo
    ADD CONSTRAINT codice_tributo_codice_key UNIQUE (codice);


--
-- Name: codice_tributo codice_tributo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.codice_tributo
    ADD CONSTRAINT codice_tributo_pkey PRIMARY KEY (id);


--
-- Name: cu_record cu_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cu_record
    ADD CONSTRAINT cu_record_pkey PRIMARY KEY (id);


--
-- Name: f24_record f24_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.f24_record
    ADD CONSTRAINT f24_record_pkey PRIMARY KEY (id);


--
-- Name: fiscal_document fiscal_document_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fiscal_document
    ADD CONSTRAINT fiscal_document_pkey PRIMARY KEY (id);


--
-- Name: import_template import_template_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_template
    ADD CONSTRAINT import_template_pkey PRIMARY KEY (id);


--
-- Name: owner_profile owner_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.owner_profile
    ADD CONSTRAINT owner_profile_pkey PRIMARY KEY (id);


--
-- Name: property_contract_rule property_contract_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contract_rule
    ADD CONSTRAINT property_contract_rule_pkey PRIMARY KEY (id);


--
-- Name: property_ota_code property_ota_code_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_ota_code
    ADD CONSTRAINT property_ota_code_pkey PRIMARY KEY (id);


--
-- Name: property property_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property
    ADD CONSTRAINT property_pkey PRIMARY KEY (id);


--
-- Name: regime_fiscale regime_fiscale_codice_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regime_fiscale
    ADD CONSTRAINT regime_fiscale_codice_key UNIQUE (codice);


--
-- Name: regime_fiscale regime_fiscale_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regime_fiscale
    ADD CONSTRAINT regime_fiscale_pkey PRIMARY KEY (id);


--
-- Name: regola_tassa_soggiorno regola_tassa_soggiorno_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regola_tassa_soggiorno
    ADD CONSTRAINT regola_tassa_soggiorno_pkey PRIMARY KEY (id);


--
-- Name: scenario_fiscale scenario_fiscale_codice_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scenario_fiscale
    ADD CONSTRAINT scenario_fiscale_codice_key UNIQUE (codice);


--
-- Name: scenario_fiscale scenario_fiscale_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.scenario_fiscale
    ADD CONSTRAINT scenario_fiscale_pkey PRIMARY KEY (id);


--
-- Name: sdi_esito sdi_esito_codice_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sdi_esito
    ADD CONSTRAINT sdi_esito_codice_key UNIQUE (codice);


--
-- Name: sdi_esito sdi_esito_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sdi_esito
    ADD CONSTRAINT sdi_esito_pkey PRIMARY KEY (id);


--
-- Name: settlement_booking settlement_booking_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settlement_booking
    ADD CONSTRAINT settlement_booking_pkey PRIMARY KEY (id);


--
-- Name: settlement settlement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settlement
    ADD CONSTRAINT settlement_pkey PRIMARY KEY (id);


--
-- Name: stato_documento stato_documento_codice_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stato_documento
    ADD CONSTRAINT stato_documento_codice_key UNIQUE (codice);


--
-- Name: stato_documento stato_documento_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stato_documento
    ADD CONSTRAINT stato_documento_pkey PRIMARY KEY (id);


--
-- Name: stato_prenotazione stato_prenotazione_codice_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stato_prenotazione
    ADD CONSTRAINT stato_prenotazione_codice_key UNIQUE (codice);


--
-- Name: stato_prenotazione stato_prenotazione_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stato_prenotazione
    ADD CONSTRAINT stato_prenotazione_pkey PRIMARY KEY (id);


--
-- Name: tassa_fascia_eta tassa_fascia_eta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tassa_fascia_eta
    ADD CONSTRAINT tassa_fascia_eta_pkey PRIMARY KEY (id);


--
-- Name: tassa_stagione tassa_stagione_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tassa_stagione
    ADD CONSTRAINT tassa_stagione_pkey PRIMARY KEY (id);


--
-- Name: tassa_zona tassa_zona_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tassa_zona
    ADD CONSTRAINT tassa_zona_pkey PRIMARY KEY (id);


--
-- Name: tenant tenant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT tenant_pkey PRIMARY KEY (id);


--
-- Name: tenant_settings tenant_settings_fk_tenant_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_settings
    ADD CONSTRAINT tenant_settings_fk_tenant_id_key UNIQUE (fk_tenant_id);


--
-- Name: tenant_settings tenant_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_settings
    ADD CONSTRAINT tenant_settings_pkey PRIMARY KEY (id);


--
-- Name: tenant tenant_tax_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT tenant_tax_code_key UNIQUE (tax_code);


--
-- Name: tenant tenant_vat_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant
    ADD CONSTRAINT tenant_vat_number_key UNIQUE (vat_number);


--
-- Name: tipo_documento tipo_documento_codice_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_documento
    ADD CONSTRAINT tipo_documento_codice_key UNIQUE (codice);


--
-- Name: tipo_documento tipo_documento_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_documento
    ADD CONSTRAINT tipo_documento_pkey PRIMARY KEY (id);


--
-- Name: tipo_immobile tipo_immobile_codice_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_immobile
    ADD CONSTRAINT tipo_immobile_codice_key UNIQUE (codice);


--
-- Name: tipo_immobile tipo_immobile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipo_immobile
    ADD CONSTRAINT tipo_immobile_pkey PRIMARY KEY (id);


--
-- Name: cu_record uq_cu_owner_year; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cu_record
    ADD CONSTRAINT uq_cu_owner_year UNIQUE (fk_owner_id, tax_year);


--
-- Name: fiscal_document uq_document_number_per_tenant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fiscal_document
    ADD CONSTRAINT uq_document_number_per_tenant UNIQUE (fk_tenant_id, document_number);


--
-- Name: booking uq_external_booking; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking
    ADD CONSTRAINT uq_external_booking UNIQUE (fk_canale_ota_id, external_booking_id);


--
-- Name: f24_record uq_f24_tenant_period_tributo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.f24_record
    ADD CONSTRAINT uq_f24_tenant_period_tributo UNIQUE (fk_tenant_id, period, fk_codice_tributo_id);


--
-- Name: import_template uq_import_template_nome; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_template
    ADD CONSTRAINT uq_import_template_nome UNIQUE (fk_tenant_id, nome);


--
-- Name: owner_profile uq_owner_tax_code_per_tenant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.owner_profile
    ADD CONSTRAINT uq_owner_tax_code_per_tenant UNIQUE (fk_tenant_id, tax_code);


--
-- Name: property uq_property_code_per_tenant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property
    ADD CONSTRAINT uq_property_code_per_tenant UNIQUE (fk_tenant_id, internal_code);


--
-- Name: property_ota_code uq_property_per_canale; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_ota_code
    ADD CONSTRAINT uq_property_per_canale UNIQUE (fk_property_id, fk_canale_ota_id);


--
-- Name: settlement_booking uq_settlement_booking; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settlement_booking
    ADD CONSTRAINT uq_settlement_booking UNIQUE (fk_settlement_id, fk_booking_id);


--
-- Name: settlement uq_settlement_owner_period; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settlement
    ADD CONSTRAINT uq_settlement_owner_period UNIQUE (fk_owner_id, period);


--
-- Name: withholding_ledger uq_withholding_per_document; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.withholding_ledger
    ADD CONSTRAINT uq_withholding_per_document UNIQUE (fk_fiscal_document_id);


--
-- Name: utente utente_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.utente
    ADD CONSTRAINT utente_email_key UNIQUE (email);


--
-- Name: utente utente_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.utente
    ADD CONSTRAINT utente_pkey PRIMARY KEY (id);


--
-- Name: withholding_ledger withholding_ledger_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.withholding_ledger
    ADD CONSTRAINT withholding_ledger_pkey PRIMARY KEY (id);


--
-- Name: idx_audit_action; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_action ON public.audit_log USING btree (action);


--
-- Name: idx_audit_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_created_at ON public.audit_log USING btree (created_at DESC);


--
-- Name: idx_audit_entity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_entity ON public.audit_log USING btree (entity_type, entity_id);


--
-- Name: idx_audit_fk_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_fk_tenant_id ON public.audit_log USING btree (fk_tenant_id);


--
-- Name: idx_booking_checkin_checkout; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_booking_checkin_checkout ON public.booking USING btree (fk_property_id, checkin_date, checkout_date);


--
-- Name: idx_booking_checkout_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_booking_checkout_date ON public.booking USING btree (fk_tenant_id, checkout_date DESC);


--
-- Name: idx_booking_fk_owner_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_booking_fk_owner_id ON public.booking USING btree (fk_tenant_id, fk_owner_id);


--
-- Name: idx_booking_fk_property_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_booking_fk_property_id ON public.booking USING btree (fk_property_id);


--
-- Name: idx_booking_fk_scenario_fiscale_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_booking_fk_scenario_fiscale_id ON public.booking USING btree (fk_scenario_fiscale_id);


--
-- Name: idx_booking_fk_stato_prenotazione_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_booking_fk_stato_prenotazione_id ON public.booking USING btree (fk_tenant_id, fk_stato_prenotazione_id);


--
-- Name: idx_booking_fk_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_booking_fk_tenant_id ON public.booking USING btree (fk_tenant_id);


--
-- Name: idx_booking_guest_tax_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_booking_guest_tax_code ON public.booking USING btree (guest_tax_code);


--
-- Name: idx_booking_settlement_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_booking_settlement_status ON public.booking USING btree (fk_tenant_id, settlement_status);


--
-- Name: idx_contract_rule_property; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contract_rule_property ON public.property_contract_rule USING btree (fk_property_id);


--
-- Name: idx_contract_rule_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contract_rule_tenant ON public.property_contract_rule USING btree (fk_tenant_id);


--
-- Name: idx_cu_fk_owner_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cu_fk_owner_id ON public.cu_record USING btree (fk_owner_id);


--
-- Name: idx_cu_fk_tenant_year; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cu_fk_tenant_year ON public.cu_record USING btree (fk_tenant_id, tax_year);


--
-- Name: idx_f24_fk_tenant_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_f24_fk_tenant_period ON public.f24_record USING btree (fk_tenant_id, period);


--
-- Name: idx_f24_stato; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_f24_stato ON public.f24_record USING btree (fk_tenant_id, stato);


--
-- Name: idx_fascia_eta_regola; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fascia_eta_regola ON public.tassa_fascia_eta USING btree (fk_regola_id);


--
-- Name: idx_fiscal_doc_fk_booking_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fiscal_doc_fk_booking_id ON public.fiscal_document USING btree (fk_booking_id);


--
-- Name: idx_fiscal_doc_fk_owner_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fiscal_doc_fk_owner_id ON public.fiscal_document USING btree (fk_tenant_id, fk_owner_id);


--
-- Name: idx_fiscal_doc_fk_stato_documento_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fiscal_doc_fk_stato_documento_id ON public.fiscal_document USING btree (fk_tenant_id, fk_stato_documento_id);


--
-- Name: idx_fiscal_doc_fk_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fiscal_doc_fk_tenant_id ON public.fiscal_document USING btree (fk_tenant_id);


--
-- Name: idx_fiscal_doc_issue_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fiscal_doc_issue_date ON public.fiscal_document USING btree (fk_tenant_id, issue_date DESC);


--
-- Name: idx_import_template_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_import_template_tenant ON public.import_template USING btree (fk_tenant_id);


--
-- Name: idx_owner_attivo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_owner_attivo ON public.owner_profile USING btree (fk_tenant_id, attivo);


--
-- Name: idx_owner_fk_regime_fiscale_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_owner_fk_regime_fiscale_id ON public.owner_profile USING btree (fk_regime_fiscale_id);


--
-- Name: idx_owner_fk_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_owner_fk_tenant_id ON public.owner_profile USING btree (fk_tenant_id);


--
-- Name: idx_owner_tax_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_owner_tax_code ON public.owner_profile USING btree (tax_code);


--
-- Name: idx_property_attivo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_property_attivo ON public.property USING btree (fk_tenant_id, attivo);


--
-- Name: idx_property_fk_owner_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_property_fk_owner_id ON public.property USING btree (fk_owner_id);


--
-- Name: idx_property_fk_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_property_fk_tenant_id ON public.property USING btree (fk_tenant_id);


--
-- Name: idx_property_ota_fk_canale_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_property_ota_fk_canale_id ON public.property_ota_code USING btree (fk_canale_ota_id, external_id);


--
-- Name: idx_property_ota_fk_property_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_property_ota_fk_property_id ON public.property_ota_code USING btree (fk_property_id);


--
-- Name: idx_regime_fiscale_attivo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_regime_fiscale_attivo ON public.regime_fiscale USING btree (attivo);


--
-- Name: idx_regime_fiscale_metadata; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_regime_fiscale_metadata ON public.regime_fiscale USING btree (metadata);


--
-- Name: idx_settlement_booking_fk_booking_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_settlement_booking_fk_booking_id ON public.settlement_booking USING btree (fk_booking_id);


--
-- Name: idx_settlement_booking_fk_settlement_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_settlement_booking_fk_settlement_id ON public.settlement_booking USING btree (fk_settlement_id);


--
-- Name: idx_settlement_fk_owner_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_settlement_fk_owner_id ON public.settlement USING btree (fk_owner_id);


--
-- Name: idx_settlement_fk_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_settlement_fk_tenant_id ON public.settlement USING btree (fk_tenant_id);


--
-- Name: idx_settlement_period; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_settlement_period ON public.settlement USING btree (fk_tenant_id, period);


--
-- Name: idx_settlement_stato; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_settlement_stato ON public.settlement USING btree (fk_tenant_id, stato);


--
-- Name: idx_stagione_regola; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stagione_regola ON public.tassa_stagione USING btree (fk_regola_id);


--
-- Name: idx_stato_documento_attivo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stato_documento_attivo ON public.stato_documento USING btree (attivo);


--
-- Name: idx_stato_prenotazione_attivo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stato_prenotazione_attivo ON public.stato_prenotazione USING btree (attivo);


--
-- Name: idx_tassa_soggiorno_attivo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tassa_soggiorno_attivo ON public.regola_tassa_soggiorno USING btree (attivo, valida_dal, valida_al);


--
-- Name: idx_tassa_soggiorno_comune; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tassa_soggiorno_comune ON public.regola_tassa_soggiorno USING btree (comune, provincia);


--
-- Name: idx_tenant_stato; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tenant_stato ON public.tenant USING btree (stato);


--
-- Name: idx_utente_fk_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_utente_fk_tenant_id ON public.utente USING btree (fk_tenant_id);


--
-- Name: idx_utente_ruolo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_utente_ruolo ON public.utente USING btree (ruolo);


--
-- Name: idx_withholding_f24; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_withholding_f24 ON public.withholding_ledger USING btree (fk_f24_record_id);


--
-- Name: idx_withholding_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_withholding_owner ON public.withholding_ledger USING btree (fk_owner_id);


--
-- Name: idx_withholding_tenant_periodo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_withholding_tenant_periodo ON public.withholding_ledger USING btree (fk_tenant_id, periodo_anno, periodo_mese);


--
-- Name: idx_zona_regola; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_zona_regola ON public.tassa_zona USING btree (fk_regola_id);


--
-- Name: import_template import_template_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER import_template_updated_at BEFORE UPDATE ON public.import_template FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: booking trg_booking_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_booking_updated_at BEFORE UPDATE ON public.booking FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: canale_ota trg_canale_ota_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_canale_ota_updated_at BEFORE UPDATE ON public.canale_ota FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: codice_tributo trg_codice_tributo_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_codice_tributo_updated_at BEFORE UPDATE ON public.codice_tributo FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: property_contract_rule trg_contract_rule_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_contract_rule_updated_at BEFORE UPDATE ON public.property_contract_rule FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: cu_record trg_cu_record_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_cu_record_updated_at BEFORE UPDATE ON public.cu_record FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: f24_record trg_f24_record_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_f24_record_updated_at BEFORE UPDATE ON public.f24_record FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: fiscal_document trg_fiscal_document_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_fiscal_document_updated_at BEFORE UPDATE ON public.fiscal_document FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: owner_profile trg_owner_profile_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_owner_profile_updated_at BEFORE UPDATE ON public.owner_profile FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: property_ota_code trg_property_ota_code_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_property_ota_code_updated_at BEFORE UPDATE ON public.property_ota_code FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: property trg_property_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_property_updated_at BEFORE UPDATE ON public.property FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: regime_fiscale trg_regime_fiscale_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_regime_fiscale_updated_at BEFORE UPDATE ON public.regime_fiscale FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: regola_tassa_soggiorno trg_regola_tassa_soggiorno_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_regola_tassa_soggiorno_updated_at BEFORE UPDATE ON public.regola_tassa_soggiorno FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: scenario_fiscale trg_scenario_fiscale_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_scenario_fiscale_updated_at BEFORE UPDATE ON public.scenario_fiscale FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: sdi_esito trg_sdi_esito_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_sdi_esito_updated_at BEFORE UPDATE ON public.sdi_esito FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: settlement trg_settlement_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_settlement_updated_at BEFORE UPDATE ON public.settlement FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: stato_documento trg_stato_documento_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_stato_documento_updated_at BEFORE UPDATE ON public.stato_documento FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: stato_prenotazione trg_stato_prenotazione_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_stato_prenotazione_updated_at BEFORE UPDATE ON public.stato_prenotazione FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: tenant_settings trg_tenant_settings_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_tenant_settings_updated_at BEFORE UPDATE ON public.tenant_settings FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: tenant trg_tenant_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_tenant_updated_at BEFORE UPDATE ON public.tenant FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: tipo_documento trg_tipo_documento_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_tipo_documento_updated_at BEFORE UPDATE ON public.tipo_documento FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: tipo_immobile trg_tipo_immobile_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_tipo_immobile_updated_at BEFORE UPDATE ON public.tipo_immobile FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: utente trg_utente_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_utente_updated_at BEFORE UPDATE ON public.utente FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: withholding_ledger trg_withholding_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_withholding_updated_at BEFORE UPDATE ON public.withholding_ledger FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: audit_log audit_log_fk_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_fk_tenant_id_fkey FOREIGN KEY (fk_tenant_id) REFERENCES public.tenant(id) ON DELETE SET NULL;


--
-- Name: audit_log audit_log_fk_utente_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_fk_utente_id_fkey FOREIGN KEY (fk_utente_id) REFERENCES public.utente(id) ON DELETE SET NULL;


--
-- Name: booking booking_fk_canale_ota_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking
    ADD CONSTRAINT booking_fk_canale_ota_id_fkey FOREIGN KEY (fk_canale_ota_id) REFERENCES public.canale_ota(id) ON DELETE SET NULL;


--
-- Name: booking booking_fk_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking
    ADD CONSTRAINT booking_fk_owner_id_fkey FOREIGN KEY (fk_owner_id) REFERENCES public.owner_profile(id) ON DELETE RESTRICT;


--
-- Name: booking booking_fk_property_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking
    ADD CONSTRAINT booking_fk_property_id_fkey FOREIGN KEY (fk_property_id) REFERENCES public.property(id) ON DELETE RESTRICT;


--
-- Name: booking booking_fk_scenario_fiscale_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking
    ADD CONSTRAINT booking_fk_scenario_fiscale_id_fkey FOREIGN KEY (fk_scenario_fiscale_id) REFERENCES public.scenario_fiscale(id) ON DELETE SET NULL;


--
-- Name: booking booking_fk_stato_prenotazione_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking
    ADD CONSTRAINT booking_fk_stato_prenotazione_id_fkey FOREIGN KEY (fk_stato_prenotazione_id) REFERENCES public.stato_prenotazione(id) ON DELETE RESTRICT;


--
-- Name: booking booking_fk_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking
    ADD CONSTRAINT booking_fk_tenant_id_fkey FOREIGN KEY (fk_tenant_id) REFERENCES public.tenant(id) ON DELETE RESTRICT;


--
-- Name: cu_record cu_record_fk_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cu_record
    ADD CONSTRAINT cu_record_fk_owner_id_fkey FOREIGN KEY (fk_owner_id) REFERENCES public.owner_profile(id) ON DELETE RESTRICT;


--
-- Name: cu_record cu_record_fk_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cu_record
    ADD CONSTRAINT cu_record_fk_tenant_id_fkey FOREIGN KEY (fk_tenant_id) REFERENCES public.tenant(id) ON DELETE RESTRICT;


--
-- Name: f24_record f24_record_fk_codice_tributo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.f24_record
    ADD CONSTRAINT f24_record_fk_codice_tributo_id_fkey FOREIGN KEY (fk_codice_tributo_id) REFERENCES public.codice_tributo(id) ON DELETE RESTRICT;


--
-- Name: f24_record f24_record_fk_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.f24_record
    ADD CONSTRAINT f24_record_fk_tenant_id_fkey FOREIGN KEY (fk_tenant_id) REFERENCES public.tenant(id) ON DELETE RESTRICT;


--
-- Name: fiscal_document fiscal_document_fk_booking_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fiscal_document
    ADD CONSTRAINT fiscal_document_fk_booking_id_fkey FOREIGN KEY (fk_booking_id) REFERENCES public.booking(id) ON DELETE RESTRICT;


--
-- Name: fiscal_document fiscal_document_fk_documento_collegato_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fiscal_document
    ADD CONSTRAINT fiscal_document_fk_documento_collegato_id_fkey FOREIGN KEY (fk_documento_collegato_id) REFERENCES public.fiscal_document(id) ON DELETE SET NULL;


--
-- Name: fiscal_document fiscal_document_fk_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fiscal_document
    ADD CONSTRAINT fiscal_document_fk_owner_id_fkey FOREIGN KEY (fk_owner_id) REFERENCES public.owner_profile(id) ON DELETE RESTRICT;


--
-- Name: fiscal_document fiscal_document_fk_sdi_esito_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fiscal_document
    ADD CONSTRAINT fiscal_document_fk_sdi_esito_id_fkey FOREIGN KEY (fk_sdi_esito_id) REFERENCES public.sdi_esito(id) ON DELETE SET NULL;


--
-- Name: fiscal_document fiscal_document_fk_stato_documento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fiscal_document
    ADD CONSTRAINT fiscal_document_fk_stato_documento_id_fkey FOREIGN KEY (fk_stato_documento_id) REFERENCES public.stato_documento(id) ON DELETE RESTRICT;


--
-- Name: fiscal_document fiscal_document_fk_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fiscal_document
    ADD CONSTRAINT fiscal_document_fk_tenant_id_fkey FOREIGN KEY (fk_tenant_id) REFERENCES public.tenant(id) ON DELETE RESTRICT;


--
-- Name: fiscal_document fiscal_document_fk_tipo_documento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fiscal_document
    ADD CONSTRAINT fiscal_document_fk_tipo_documento_id_fkey FOREIGN KEY (fk_tipo_documento_id) REFERENCES public.tipo_documento(id) ON DELETE RESTRICT;


--
-- Name: import_template import_template_fk_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_template
    ADD CONSTRAINT import_template_fk_tenant_id_fkey FOREIGN KEY (fk_tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: owner_profile owner_profile_fk_regime_fiscale_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.owner_profile
    ADD CONSTRAINT owner_profile_fk_regime_fiscale_id_fkey FOREIGN KEY (fk_regime_fiscale_id) REFERENCES public.regime_fiscale(id) ON DELETE RESTRICT;


--
-- Name: owner_profile owner_profile_fk_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.owner_profile
    ADD CONSTRAINT owner_profile_fk_tenant_id_fkey FOREIGN KEY (fk_tenant_id) REFERENCES public.tenant(id) ON DELETE RESTRICT;


--
-- Name: property_contract_rule property_contract_rule_fk_canale_ota_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contract_rule
    ADD CONSTRAINT property_contract_rule_fk_canale_ota_id_fkey FOREIGN KEY (fk_canale_ota_id) REFERENCES public.canale_ota(id) ON DELETE SET NULL;


--
-- Name: property_contract_rule property_contract_rule_fk_property_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contract_rule
    ADD CONSTRAINT property_contract_rule_fk_property_id_fkey FOREIGN KEY (fk_property_id) REFERENCES public.property(id) ON DELETE CASCADE;


--
-- Name: property_contract_rule property_contract_rule_fk_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contract_rule
    ADD CONSTRAINT property_contract_rule_fk_tenant_id_fkey FOREIGN KEY (fk_tenant_id) REFERENCES public.tenant(id) ON DELETE RESTRICT;


--
-- Name: property property_fk_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property
    ADD CONSTRAINT property_fk_owner_id_fkey FOREIGN KEY (fk_owner_id) REFERENCES public.owner_profile(id) ON DELETE RESTRICT;


--
-- Name: property property_fk_pm_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property
    ADD CONSTRAINT property_fk_pm_user_id_fkey FOREIGN KEY (fk_pm_user_id) REFERENCES public.utente(id) ON DELETE SET NULL;


--
-- Name: property property_fk_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property
    ADD CONSTRAINT property_fk_tenant_id_fkey FOREIGN KEY (fk_tenant_id) REFERENCES public.tenant(id) ON DELETE RESTRICT;


--
-- Name: property property_fk_tipo_immobile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property
    ADD CONSTRAINT property_fk_tipo_immobile_id_fkey FOREIGN KEY (fk_tipo_immobile_id) REFERENCES public.tipo_immobile(id) ON DELETE SET NULL;


--
-- Name: property_ota_code property_ota_code_fk_canale_ota_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_ota_code
    ADD CONSTRAINT property_ota_code_fk_canale_ota_id_fkey FOREIGN KEY (fk_canale_ota_id) REFERENCES public.canale_ota(id) ON DELETE RESTRICT;


--
-- Name: property_ota_code property_ota_code_fk_property_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_ota_code
    ADD CONSTRAINT property_ota_code_fk_property_id_fkey FOREIGN KEY (fk_property_id) REFERENCES public.property(id) ON DELETE CASCADE;


--
-- Name: regola_tassa_soggiorno regola_tassa_soggiorno_fk_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regola_tassa_soggiorno
    ADD CONSTRAINT regola_tassa_soggiorno_fk_tenant_id_fkey FOREIGN KEY (fk_tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: settlement_booking settlement_booking_fk_booking_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settlement_booking
    ADD CONSTRAINT settlement_booking_fk_booking_id_fkey FOREIGN KEY (fk_booking_id) REFERENCES public.booking(id) ON DELETE RESTRICT;


--
-- Name: settlement_booking settlement_booking_fk_settlement_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settlement_booking
    ADD CONSTRAINT settlement_booking_fk_settlement_id_fkey FOREIGN KEY (fk_settlement_id) REFERENCES public.settlement(id) ON DELETE CASCADE;


--
-- Name: settlement settlement_fk_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settlement
    ADD CONSTRAINT settlement_fk_owner_id_fkey FOREIGN KEY (fk_owner_id) REFERENCES public.owner_profile(id) ON DELETE RESTRICT;


--
-- Name: settlement settlement_fk_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.settlement
    ADD CONSTRAINT settlement_fk_tenant_id_fkey FOREIGN KEY (fk_tenant_id) REFERENCES public.tenant(id) ON DELETE RESTRICT;


--
-- Name: tassa_fascia_eta tassa_fascia_eta_fk_regola_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tassa_fascia_eta
    ADD CONSTRAINT tassa_fascia_eta_fk_regola_id_fkey FOREIGN KEY (fk_regola_id) REFERENCES public.regola_tassa_soggiorno(id) ON DELETE CASCADE;


--
-- Name: tassa_stagione tassa_stagione_fk_regola_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tassa_stagione
    ADD CONSTRAINT tassa_stagione_fk_regola_id_fkey FOREIGN KEY (fk_regola_id) REFERENCES public.regola_tassa_soggiorno(id) ON DELETE CASCADE;


--
-- Name: tassa_zona tassa_zona_fk_regola_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tassa_zona
    ADD CONSTRAINT tassa_zona_fk_regola_id_fkey FOREIGN KEY (fk_regola_id) REFERENCES public.regola_tassa_soggiorno(id) ON DELETE CASCADE;


--
-- Name: tenant_settings tenant_settings_fk_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenant_settings
    ADD CONSTRAINT tenant_settings_fk_tenant_id_fkey FOREIGN KEY (fk_tenant_id) REFERENCES public.tenant(id) ON DELETE CASCADE;


--
-- Name: utente utente_fk_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.utente
    ADD CONSTRAINT utente_fk_owner_id_fkey FOREIGN KEY (fk_owner_id) REFERENCES public.owner_profile(id) ON DELETE SET NULL;


--
-- Name: utente utente_fk_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.utente
    ADD CONSTRAINT utente_fk_tenant_id_fkey FOREIGN KEY (fk_tenant_id) REFERENCES public.tenant(id) ON DELETE SET NULL;


--
-- Name: withholding_ledger withholding_ledger_fk_booking_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.withholding_ledger
    ADD CONSTRAINT withholding_ledger_fk_booking_id_fkey FOREIGN KEY (fk_booking_id) REFERENCES public.booking(id) ON DELETE RESTRICT;


--
-- Name: withholding_ledger withholding_ledger_fk_f24_record_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.withholding_ledger
    ADD CONSTRAINT withholding_ledger_fk_f24_record_id_fkey FOREIGN KEY (fk_f24_record_id) REFERENCES public.f24_record(id) ON DELETE SET NULL;


--
-- Name: withholding_ledger withholding_ledger_fk_fiscal_document_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.withholding_ledger
    ADD CONSTRAINT withholding_ledger_fk_fiscal_document_id_fkey FOREIGN KEY (fk_fiscal_document_id) REFERENCES public.fiscal_document(id) ON DELETE RESTRICT;


--
-- Name: withholding_ledger withholding_ledger_fk_owner_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.withholding_ledger
    ADD CONSTRAINT withholding_ledger_fk_owner_id_fkey FOREIGN KEY (fk_owner_id) REFERENCES public.owner_profile(id) ON DELETE RESTRICT;


--
-- Name: withholding_ledger withholding_ledger_fk_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.withholding_ledger
    ADD CONSTRAINT withholding_ledger_fk_tenant_id_fkey FOREIGN KEY (fk_tenant_id) REFERENCES public.tenant(id) ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--

\unrestrict uVKuEPwEt1EpIVXyTZ1DARBTTpL1ymLwdUK1kYh4L1jaRnxQCmHBFD72AU0F1EJ

