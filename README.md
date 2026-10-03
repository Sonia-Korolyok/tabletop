# TableTop

A personal cookbook with an embedded AI agent: save recipes from anywhere (websites, YouTube, screenshots), organize them into folders, plan your weekly menu and build a grocery list.

> Solo pet project. Status: stage 2 in progress — recipes and library (schema migration V2 done).

- Product spec: [docs/mvp-spec.md](docs/mvp-spec.md)
- Competitor teardown (Honeydew): [docs/honeydew-teardown.md](docs/honeydew-teardown.md)
- Database schema: [docs/database-schema.md](docs/database-schema.md)
- Stage 2 flows: [docs/stage-2-flows.md](docs/stage-2-flows.md)

## Stack
Java 21 · Spring Boot 3.5 · Spring Security (JWT, HS256) · PostgreSQL 16 · Flyway · JUnit 5 + Testcontainers · React + TypeScript PWA (planned) · Gemini API (planned)

## Run locally
```bash
docker compose up -d            # PostgreSQL on localhost:5432
cd backend
mvn spring-boot:run             # API on http://localhost:8080
```
Environment variables (see `.env.example`): `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (min 32 chars).

## Tests
```bash
cd backend && mvn test          # integration tests need Docker running (Testcontainers)
```

## API (stage 1)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | — | `{email, password, displayName}` → JWT |
| POST | `/api/auth/login` | — | `{email, password}` → JWT |
| GET | `/api/me` | Bearer | current user |
| GET | `/actuator/health` | — | health check |

```bash
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"me@example.com","password":"secret123","displayName":"Me"}'
```

## Roadmap
1. ✅ Skeleton, PostgreSQL, Flyway, auth
2. 🚧 Recipes CRUD + library (search with typos, keyset pagination, folders, photos)
3. App catalog of classic recipes
4. Import from websites + online search
5. Import from YouTube and screenshots via Gemini
6. Grocery list + meal plan
7. Ingredient substitutions + cache
8. Load test with 1000 recipes, polish, deploy
