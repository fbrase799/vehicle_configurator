# 8. SQLite embedded in backend (replaces MySQL container)

Date: 2026-07-12

## Status

Accepted (supersedes the MySQL-as-Container-App decision)

## Context

The prototype needs a relational database for catalog, configurations,
and orders. The previous approach (MySQL as a separate Azure Container
App with `minReplicas=1`) had two problems for a **job-application demo**:

1. **Cost** — the database container ran 24/7 (~€10–15/month) even when
   frontend and backend scaled to zero.
2. **Cold start** — frontend and backend scaled to zero, causing 10–30 s
   delays on the first request. Unacceptable when a recruiter opens the
   link at an unpredictable time.

Orders and configurations are **demo data only** — data loss on replica
recycling is acceptable.

## Decision

Embed **SQLite** in the backend container. Schema and seed data live in
`backend/src/main/resources/db/001-init.sql`, applied by
`DatabaseInitializer` on first start when the catalog is empty.

Run **both** Azure Container Apps with `minReplicas=1` so the public
URL is always instantly reachable.

## Consequences

- Stack reduced from three containers to **two** (frontend + backend).
- No separate database image, no MySQL secrets, no TCP ingress on :3306.
- Cloud cost shifts from "warm DB + cold apps" to "warm frontend +
  warm backend" (~€12–18/month combined).
- SQLite single-writer constraint → backend `maxReplicas=1`.
- Replica recycling wipes demo orders/configurations; catalog is
  re-seeded automatically.
- `01-setup.sh` removes a legacy `database` container app on re-run.

## Alternatives considered

- **Keep MySQL container + scale-to-zero apps** — rejected: cold start
  UX problem for recruiters.
- **MySQL container + minReplicas=1 on all three** — rejected: three
  always-warm containers, higher cost, more complexity.
- **Azure Database for MySQL Flexible Server** — rejected: ~€15–25/month
  minimum, overkill for demo data.
- **Warmup cron (GitHub Actions)** — rejected: small risk window where
  the link is still cold; harder to explain operationally.
