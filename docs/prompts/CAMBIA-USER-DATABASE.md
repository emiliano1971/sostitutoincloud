-- Collegarsi a postgres: 
psql -U postgres -h localhost 
e poi :

-- Crea l'utente
CREATE USER sostitutoincloud WITH PASSWORD 'latuapassword';

-- Assegna i permessi sul database
GRANT ALL PRIVILEGES ON DATABASE sostitutoincloud TO sostitutoincloud;

-- Assegna i permessi sullo schema public
\c sostitutoincloud
GRANT ALL ON SCHEMA public TO sostitutoincloud;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO sostitutoincloud;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO sostitutoincloud;

-- Per le tabelle future
ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT ALL ON TABLES TO sostitutoincloud;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT ALL ON SEQUENCES TO sostitutoincloud;