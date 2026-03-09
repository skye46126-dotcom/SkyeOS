# SkyeOS / LifeOS Product Manual v1

## 1. Product Positioning

SkyeOS (LifeOS) is a long-term personal life management app focused on one core chain:

`Time -> Project -> Income -> Freedom`

It helps users capture daily actions and convert them into structured records, then review outcomes through ROI, hourly rate, and freedom-related metrics.

## 2. Target Users

- Personal users who want long-term self-management (10-30 years).
- Small pilot groups (3-4 people) running isolated data usage.
- Users who need both manual input and AI-assisted parsing.

## 3. Core Capabilities

### 3.1 Daily Dashboard (Today)

- Key daily metrics: Work Time, Income, Expense, Freedom.
- Hourly rate vs time debt summary.
- Daily time allocation summary.
- Quick entry to Capture and Review.

### 3.2 Capture (Manual + AI)

- Manual entry for:
  - Time log
  - Income
  - Expense
  - Learning
  - Project creation
- AI Parse flow:
  - Natural language input
  - Structured preview
  - One-tap commit
- AI ratio and efficiency/value/state scores supported in records.

### 3.3 Projects

- Project list with status segmentation (Active/Paused/Done).
- Project detail with:
  - ROI
  - Hourly rate
  - Time cost / total cost
  - Break-even line
  - Profit/Loss
  - Recent records

### 3.4 Review

- Day / Week / Month windows.
- Snapshot summary and trend comparison.
- Time allocation.
- ROI leaderboard.
- Time sinkholes (high time, low ROI).
- Income history and full history ledger.
- Tag board (time tags / expense tags / tag detail).

### 3.5 Cloud & Settings

- Cloud sync config:
  - Server URL
  - API key
  - Device ID
- Backup operations:
  - Upload latest backup
  - List remote backups
  - Download and restore by filename
- Cost settings:
  - Ideal hourly rate
  - Monthly basic living cost
  - Monthly fixed subscription cost
- AI API settings:
  - Provider (`custom/deepseek/siliconflow`)
  - Base URL
  - API key
  - Model
  - System prompt
- Tag management for custom tags.

## 4. Data Scope

The current data model includes:

- time_log
- project
- income
- expense
- learning_record
- daily_review
- metric_snapshot
- backup_record / restore_record
- tag and tag-relations

Multi-user isolation is implemented with user-scoped data access in repositories (pilot-grade isolation for shared server testing).

## 5. Deployment Modes

### 5.1 Single-server Self-host

- Recommended stack:
  - `lifeos-api` (Python/FastAPI)
  - `nginx` (reverse proxy)
- Works on domestic cloud servers (e.g., JD Cloud ECS).
- Supports direct IP access; domain + HTTPS recommended for production-like usage.

### 5.2 Environment Config (Server)

In `deploy/.env`:

```env
LIFEOS_API_KEY=<strong_random_secret>
LIFEOS_MAX_UPLOAD_MB=200
```

Client side users must use the same server URL and API key.

## 6. User Onboarding Flow

1. Install Android app (debug/release APK).
2. Open `Settings` and fill:
   - Server URL
   - API key
   - Unique device ID
3. (Optional) Fill AI provider config.
4. Save config.
5. Start with `Capture`:
   - Add first project
   - Add first time log/income/expense
6. Check `Today` and `Review` pages for metrics.

## 7. Operating Guidelines

### 7.1 Daily Usage (5-10 minutes)

- Log time.
- Log income/expense.
- Add short notes and tags.
- Use quick review.

### 7.2 Weekly Usage (10-20 minutes)

- Open `Review -> Week`.
- Check:
  - ROI ranking
  - Time sinkholes
  - AI assist ratio
  - Work/Learning efficiency trends

### 7.3 Monthly Usage (20-40 minutes)

- Update cost settings if needed.
- Review freedom trend.
- Clean up projects (close paused/low ROI items).

## 8. Current Constraints (v1)

- Trend charts are currently list-heavy; chart visualization refactor is planned.
- Access control is API-key based (shared key mode by default).
- No full account switch UI yet (user isolation exists at data layer/pilot mode).
- VCP plugin bridge is planned architecture, not fully merged into this manual flow.

## 9. Security Notes

- Use a strong API key.
- Do not expose `.env` publicly.
- Use HTTPS + domain in production-like environments.
- Assign different `Device ID` values for different users/devices.

## 10. Recommended Next Milestones

1. Chart refactor:
   - Income/Expense trend line
   - Hourly rate vs debt line
   - Time allocation stacked bars
2. Per-user API key issuance (revoke/rotate support).
3. UI design system freeze (spacing/type/color tokenized).
4. VCP plugin integration as external AI middleware.

## 11. Version Metadata

- Manual version: `v1`
- Product stage: `Pilot / Self-hosted`
- Last updated: `2026-03-08`

