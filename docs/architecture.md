# Architettura Target

## Frontend (React)
- Rimane invariato nella struttura
- Le chiamate API passano da Supabase client → fetch/axios verso Spring Boot
- Base URL backend: http://localhost:8080/api

## Backend (Spring Boot)
- Controllers → Services → Repositories (JPA)
- Autenticazione: [da definire]
- Porta: 8080

## Database (PostgreSQL)
- Host locale in sviluppo
- Schema da definire in docs/db/schema-target.sql
- Migrations gestite con Flyway o Liquibase