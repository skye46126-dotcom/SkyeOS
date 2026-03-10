# SkyeOS Base Architecture And Module Map

## 1) Current Top-Level Modules
- `Today`:
  - Role: daily dashboard only (read-focused).
  - Class: `ui/fragment/TodayFragment`.
- `Capture`:
  - Role: write entry for records.
  - Class: `ui/fragment/CaptureFragment`.
- `Management`:
  - Role: operations hub for maintenance and correction.
  - Classes: `ui/fragment/ManagementFragment`, `TimeManagementFragment`, `ProjectsFragment`, `CostManagementFragment`, `SettingsFragment`.
- `Review`:
  - Role: trend and windowed analysis.
  - Class: `ui/fragment/ReviewFragment`.

## 2) Layered Package Classification
- `com.example.skyeos.ui`
  - UI and input parsing (`fragment/*`, `FormParsers`, `ui/util/*`).
- `com.example.skyeos.domain`
  - `model/*`: business entities and summaries.
  - `repository/*`: domain-facing contracts.
  - `usecase/*`: orchestration per action.
- `com.example.skyeos.data`
  - `db/*`: SQLite schema and migrations.
  - `repository/*`: SQL-backed repository implementations.
  - `auth/*`: user-scoping context.
- `com.example.skyeos.ai`
  - parser engines and orchestration for AI/rule parse.
- `com.example.skyeos.cloud`
  - backup list/upload/download client and config.
- app composition
  - `AppGraph`, `MainActivity`, `SkyeOsApplication`, `AppLocaleManager`.

## 3) Redundancy And Cleanup Applied
- Removed duplicate fragment replacement in capture routing:
  - `MainActivity.openCaptureType` now uses deferred routing only.
- Restored language-switch behavior in settings:
  - `SettingsFragment.saveConfig` now recreates activity when language changed.
- Removed unused import:
  - `ProjectDetailFragment` removed `ProjectUseCases` import.
- Extracted shared UI formatting utility:
  - `ui/util/UiFormatters` now centralizes yuan/duration/hourly rendering.
- Removed dead navigation string:
  - legacy `nav_projects` string removed from locale files.

## 4) Identified Structural Redundancies (Next Refactor Targets)
- Oversized `CaptureFragment`:
  - Time/Income/Expense/Learning/Project forms and AI parsing all in one class.
  - Split target:
    - `capture/manual/*` by record type
    - `capture/ai/*` for parse-preview-commit flow
- Oversized `SettingsFragment`:
  - General settings + cloud sync + AI test + tag CRUD mixed in one fragment.
  - Split target:
    - `settings/general`
    - `settings/cloud`
    - `settings/ai`
    - `settings/tags`
- `AppGraph` monolith:
  - Many use cases wired in one constructor.
  - Split target:
    - `AppGraphCore`, `AppGraphCapture`, `AppGraphManagement`, `AppGraphReview`.

## 5) Proposed Feature Modules (Gradual, Package-First)
- `feature.today`
- `feature.capture.manual`
- `feature.capture.ai`
- `feature.management.projects`
- `feature.management.income`
- `feature.management.expense`
- `feature.management.time`
- `feature.management.tags`
- `feature.review`

## 6) Stable Boundaries To Enforce
- UI should call only use cases, not raw SQL.
- `Review` queries must use business-time fields (`started_at` / `occurred_on`), never `updated_at`.
- Cost computations should stay inside one policy path (shared by Today/Management/Review).
- Tag selection rules should be centralized in one selector policy (domain filter + level filter).

## 7) Immediate Next Step
- Build real `Time Management` list/edit/delete workspace and stop editing timeline only via scattered entry points.
