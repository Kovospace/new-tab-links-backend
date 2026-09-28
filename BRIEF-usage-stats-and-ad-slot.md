# Brief — new-tab-links-backend: usage statistics endpoints

> **Status:** the extension side is merged (new-tab-links-extension PR #54). It already calls the
> endpoints below and frames the slot page; until they exist, the slot stays empty and new-tab
> counts wait in the browser (the last 7 days are kept). Start this repo's work on
> `feature/usage-stats-and-ad-slot`, created from `main`.

**Kind:** implementation. **Branch:** `feature/usage-stats-and-ad-slot` (same name in all four repos).
**Depends on:** the migrations brief (`daily_metric`, `website_visitor_hash`). Bump
`flyway.migrations.schema.version` to that image's tag.

## Why
Anonymous usage counts for the admin page and for ad-network applications. Aggregates only:
never store or log a user id, installation id, URL, or raw IP for these endpoints.

## 1. `POST /api/v1/stats/new-tabs` — public, called by the extension

Request (days are the **client's local dates**; the extension batches and sends every ~15 min):
```json
{ "days": [ { "day": "2026-09-28", "count": 12 }, { "day": "2026-09-27", "count": 3 } ] }
```
- No authentication. If an `Authorization` header is present, ignore it — do not tie counts to a user.
- At most 8 entries. Per entry: `day` within **[server today − 7 days, server today + 1 day]**
  (timezones), `count` 1..2000. Entries outside those bounds are **silently dropped**, not an error —
  the client must not retry them forever.
- For each accepted entry: `INSERT INTO daily_metric (day, metric, value) VALUES (?, 'new_tabs', ?)
  ON CONFLICT (day, metric) DO UPDATE SET value = daily_metric.value + EXCLUDED.value`.
- Response **200** always on a well-formed body (even if every entry was dropped):
  ```json
  { "nextReportAfterSeconds": 900 }
  ```
  The value comes from a property, env `STATS_REPORT_INTERVAL_SECONDS`, **default 900** (15 min).
  It is how the reporting interval is tuned without an extension release.
- **400** on a malformed body. **429** when rate-limited (below).

## 2. `POST /api/v1/stats/website-visit` — public, called by the website

- Empty body. No authentication.
- If the `User-Agent` is missing or matches
  `(?i)bot|crawl|spider|slurp|headless|preview|scan|monitor|curl|wget|python|java/|go-http|httpclient`
  → **204**, count nothing.
- Otherwise `visitor_hash = HMAC-SHA256(secret, clientIp + "|" + userAgent + "|" + day)`, where
  `day` is the server's current date (UTC) and `secret` comes from env `STATS_VISITOR_HASH_SECRET`
  (a secret → Infisical, set by the user; generate a random fallback at startup if absent and log a
  warning — counts then dedupe per pod only). The client IP must be the real one behind the ingress
  (forwarded headers); check how the app already resolves it.
- `INSERT INTO website_visitor_hash (day, visitor_hash) VALUES (?, ?) ON CONFLICT DO NOTHING`; only
  if a row was inserted, upsert `daily_metric (day, 'website_visitors')` by 1, in the same transaction.
- Response **204**. **429** when rate-limited.

## 3. Rate limit (both public endpoints)
Simple per-IP limit, in memory per pod, e.g. 120 requests / hour / IP (an office behind one NAT
has many installations). Reuse the existing throttle machinery if it fits; do not add a dependency
for this.

## 4. Nightly cleanup
Scheduled job (same pattern as `VisitorTokenCleanupScheduler`): `DELETE FROM website_visitor_hash
WHERE day < current_date`. Safe on several replicas.

## 5. `GET /api/v1/admin/metrics?metric=new_tabs&month=2026-09` — admin only
Under the existing administration path/security. `metric` ∈ {`new_tabs`, `website_visitors`},
`month` = `YYYY-MM`.
```json
{ "metric": "new_tabs", "month": "2026-09",
  "days": [ { "day": "2026-09-01", "value": 0 }, … one entry for every day of the month … ],
  "total": 1234 }
```
Days with no row are returned as 0, so the graph needs no gap handling. The two metrics are never
summed together — the admin page draws two separate graphs.

## 6. Security / CORS
- Both `POST /api/v1/stats/*` added to the public endpoints in `SecurityConfiguration`.
- CORS: `new-tabs` is called from `chrome-extension://*` (already allowed), `website-visit` from the
  website origin.

## Acceptance
- Tests: upsert adds (two reports for one day sum); out-of-range days and counts are dropped and
  still 200; bot user agents are not counted; the same IP+UA is counted once per day; admin endpoint
  fills missing days with 0; the interval property reaches the response.
- Tell the user if `STATS_VISITOR_HASH_SECRET` must be created in Infisical before deploying.
