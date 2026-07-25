# Shoonya Market Movers

A full-stack Java (Spring Boot) app that connects to the **Shoonya by Finvasia**
market-data API and shows, in real time, the biggest **gainers and losers** over
several short time windows (**30s, 1m, 2m, 5m, 10m**), with **Nifty 50** and
**Sensex** pinned at the top.

> Info / monitoring only. It **never** places, modifies or cancels orders.

---

## What it does

- Authenticates with Shoonya (Noren `QuickAuth`: SHA-256 password + app key, TOTP second factor).
- Opens the Noren WebSocket feed and subscribes to touchline data for the configured indices and watchlist.
- Keeps a short, memory-bounded **tick history** per scrip.
- Every 5 seconds computes absolute + percentage change over each window and ranks **top gainers / losers**.
- Adds **insights** per row: short vs medium-term momentum (gaining / rebounding / pulling back / under pressure), position within the day's range (near high / near low) and day change %.
- Streams everything to a live browser dashboard over a WebSocket (no polling).

---

## Quick start (no credentials needed)

The app ships in **mock mode** so you can run it immediately with a synthetic feed:

```bash
mvn spring-boot:run
```

Then open <http://localhost:8080>. You'll see index cards, interval tabs and the
gainers/losers tables updating live. Longer windows (5m, 10m) show a
"collecting history" note until enough time has elapsed.

> Requires JDK 17+ and Maven. Check with `java -version` and `mvn -version`.

---

## Going live with your Shoonya account

1. Enable the API for your Shoonya account and note your **vendor code** and **API key**
   (from the Shoonya API portal). You also need your **user id**, **password** and
   your TOTP setup.

2. Turn off mock mode and provide credentials. Prefer environment variables:

   **PowerShell (Windows):**
   ```powershell
   $env:SHOONYA_USER_ID   = "FA123456"
   $env:SHOONYA_PASSWORD  = "your-login-password"
   $env:SHOONYA_VENDOR_CODE = "FA123456_U"
   $env:SHOONYA_API_KEY   = "your-api-key"
   $env:SHOONYA_TOTP_SECRET = "BASE32TOTPSECRET"   # or set SHOONYA_FACTOR2 with a live 6-digit code
   mvn spring-boot:run "-Dspring-boot.run.arguments=--shoonya.mock=false"
   ```

   **bash/zsh:**
   ```bash
   export SHOONYA_USER_ID=FA123456
   export SHOONYA_PASSWORD='your-login-password'
   export SHOONYA_VENDOR_CODE=FA123456_U
   export SHOONYA_API_KEY=your-api-key
   export SHOONYA_TOTP_SECRET=BASE32TOTPSECRET
   mvn spring-boot:run -Dspring-boot.run.arguments=--shoonya.mock=false
   ```

   Alternatively edit `src/main/resources/application.properties` directly and set
   `shoonya.mock=false` plus the `shoonya.*` credential values.

### Second factor: two options
- `shoonya.totp-secret` — the Base32 secret behind your authenticator QR code. The
  app generates the current 6-digit code automatically (best for unattended runs).
- `shoonya.factor2` — a live 6-digit code you paste in just before starting (expires in 30s).

---

## Configuration reference (`application.properties`)

| Property | Default | Meaning |
|---|---|---|
| `shoonya.mock` | `true` | Synthetic feed (no creds) when true; live feed when false. |
| `shoonya.rest-base` | `https://api.shoonya.com/NorenWClientTP/` | REST base URL. |
| `shoonya.ws-url` | `wss://api.shoonya.com/NorenWSTP/` | WebSocket URL. |
| `shoonya.user-id` / `password` / `vendor-code` / `api-key` / `imei` | (env) | Login credentials. |
| `shoonya.factor2` / `totp-secret` | (env) | Second factor (one of the two). |
| `shoonya.indices` | `NSE\|26000\|NIFTY 50,BSE\|1\|SENSEX` | Indices pinned at top. |
| `shoonya.watchlist` | 25 Nifty names | Scrips scanned for movers. |
| `shoonya.top-n` | `10` | Rows per gainers/losers table. |
| `shoonya.broadcast-interval-ms` | `5000` | Push cadence to the browser. |
| `shoonya.retention-ms` | `720000` | Tick history kept in memory (must exceed largest window). |
| `shoonya.windows-seconds` | `30,60,120,300,600` | Movement windows. |

Instrument format is `EXCHANGE|TOKEN|DISPLAY NAME` (comma separated).

> **Tokens matter.** The watchlist ships with standard NSE tokens for common
> Nifty names, but tokens can differ. For live use, verify each token against
> Shoonya's symbol master (the `NSE_symbols.txt` / `BSE_symbols.txt` files from
> the Shoonya API downloads) and adjust `shoonya.watchlist` accordingly.

---

## How it works

```
Shoonya WS (tk/tf)  ->  FeedIngest  ->  TickStore (per-scrip history + latest state)
                                             |
                          MovementService (windowed change + ranking + insights)
                                             |
        SnapshotBroadcaster (every 5s) -> /ws/market -> browser dashboard
```

Key packages under `com.example.shoonyamonitor`:
- `config` — typed properties.
- `shoonya` — auth (`ShoonyaAuthService`, `CryptoUtil`, `TotpGenerator`), live feed (`ShoonyaFeedClient`), mock feed (`MockFeedService`), ingest (`FeedIngest`).
- `store` — `InstrumentRegistry`, `TickStore`.
- `service` — `MovementService`, `SnapshotBroadcaster`.
- `web` — `MarketWebSocketHandler`, `WebSocketConfig`, `SnapshotController`.

REST helpers:
- `GET /api/snapshot` — current snapshot as JSON (handy for debugging).

---

## Notes & limits

- A single Noren WebSocket connection handles a few hundred subscriptions; keep
  the watchlist modest (dozens, not thousands).
- "Change over 30s/1m/…" is computed from the data collected **since the app
  started**, so longer windows need the app to run for at least that long before
  they populate.
- Percentages are point-in-time and meant for spotting momentum, not for
  execution decisions.
- 52-week high/low is intentionally omitted from the touchline path to keep
  latency low; it can be added later via the `GetSecurityInfo` REST call.
