# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This is the **Spring Boot backend** for ANAPCO — the service the sibling Angular front-end and Python ML service talk to (the parent `~/CLAUDE.md` describes those and calls this backend "not part of this repo"; this file covers this repo).

## Common Commands

Use the Maven wrapper (`mvnw.cmd` on Windows / `./mvnw` in bash); no local Maven needed.

- **Run the app**: `./mvnw spring-boot:run` (starts on `http://localhost:8089`)
- **Build a jar**: `./mvnw clean package` (jar in `target/`)
- **Run all tests**: `./mvnw test`
- **Run a single test class**: `./mvnw test -Dtest=nomPrenomClasseExamenApplicationTests`
- **Run a single test method**: `./mvnw test -Dtest=ClassName#methodName`
- **API docs (Swagger UI)**: `http://localhost:8089/swagger-ui.html` — the fastest way to see the full endpoint surface, which is broad and evolving.

There is currently only one (context-load) test; there is no configured linter beyond the compiler.

## Configuration & Secrets (do this first)

`src/main/resources/application.properties` is **git-ignored** (the `application*.properties` rule) — only `application.properties.template` is tracked. To run locally, copy the template to `application.properties` and fill in real values. The template documents every external credential the app needs:

- **MySQL** at `localhost:3306`, database `ETAP_DB` (auto-created; `ddl-auto=update`, so schema follows the entities). `data.sql` seeds reference data.
- **JWT** signing key + expiry; **Google OAuth2** client id/secret.
- **SMTP** (Gmail) for account-activation / reset-password emails (Thymeleaf templates in `resources/templates`).
- **Groq** LLM (`groq.*` — separate intent vs. answer models), **Python ML service** (`ml.service.base-url`, default `http://localhost:5001`), **Open-Meteo** weather, **EIA** oil prices, **Fixer** FX rates.

Never commit a filled-in `application.properties`.

## Architecture

Spring Boot 3.3.4, Java 17. All code lives under the base package `tn.esprit.examen.nomPrenomClasseExamen` (the artifact name `nomPrenomClasseExamen` is a leftover exam template — do not read meaning into it).

### Package layout — a core module plus feature slices
- **Core** (flat under the base package): `entities/`, `repositories/`, `services/`, `controllers/`, `dto/` — sites, equipment, categories, incidents, maintenance, finance KPIs, budgets, manual expenses, FX rates. These are the primary domain.
- **`ai/`** — Groq-powered assistant. `GroqAssistantService` parses a user question into an `AssistantIntent` (FINANCE_KPI / INCIDENT_RISK / WEATHER_RISK / ML_COST_FORECAST), routes to the matching core service to build a DTO, then calls Groq once more to render a natural-language answer in FR/EN/AR. It orchestrates other modules rather than owning domain data.
- **`market/`** — imports oil (EIA) and energy prices, builds `MonthlyFeatureSnapshot` rows, and is the gateway to the Python ML service. `MlPredictionController` (`/api/ml/**`) exposes train / predict cost / predict risk / SHAP-explain — these proxy to the Flask ML service (XGBoost models); Spring Boot holds no ML model itself.
- **`weather/`** — Open-Meteo client, weather data storage, and a risk/advisory layer (`WeatherRiskService`).
- **`cost/`** and **`rul/`** — equipment cost analysis and remaining-useful-life estimation (self-contained controller/service/dto slices).
- **`auth/`** — registration, JWT authentication, Google sign-in, activation, forgot/reset password.
- **`security/`** — `SecurityConfig`, `JwtFilter`, `JwtService`, `UserDetailsServiceImpl`, and `SecurityRoles`.
- **`aspects/`** — `LoggingAspect` / `PerformanceAspect` (AOP cross-cutting logging).

Each feature slice follows the same internal shape: `client/` (external HTTP), `entities/` + `repositories/` (JPA), `services/`, `controllers/`, `dto/`. When adding to a domain, place code in its slice, not in the core folders.

### Security model
Stateless JWT: `JwtFilter` runs before `UsernamePasswordAuthenticationFilter`, CSRF disabled, `SessionCreationPolicy.STATELESS`. Four roles are defined in `SecurityRoles` (`ADMIN`, `OPS_MANAGER`, `FINANCE_CONTROLLER`, `VIEWER`) and method security is enabled (`@EnableMethodSecurity(securedEnabled = true)`).

**Important caveat:** in the current `SecurityConfig`, `/auth/**`, `/users/**`, and **`/api/**` are `permitAll()` — so most business endpoints are effectively open regardless of the role constants. The role-path arrays (`READ_ONLY_API_PATHS`, `OPERATIONAL_WRITE_PATHS`, `FINANCE_WRITE_PATHS`) are defined but not yet wired into `authorizeHttpRequests`. If you touch authorization, reconcile these intended mappings with what is actually enforced rather than assuming the roles are active. Note `security/` and `auth/` currently have uncommitted local changes.

### Startup behavior
`nomPrenomClasseExamenApplication` is annotated `@EnableJpaAuditing` and `@EnableAsync`, and a `CommandLineRunner` seeds the four roles into the DB on boot if missing.

## Data Flow (end to end)
Angular → **this Spring Boot backend** → Python Flask ML service (`localhost:5001`) for predictions, and → external APIs (Groq, Open-Meteo, EIA, Fixer). The backend persists domain + market + weather data in MySQL and assembles KPIs/feature snapshots that the ML service consumes.
