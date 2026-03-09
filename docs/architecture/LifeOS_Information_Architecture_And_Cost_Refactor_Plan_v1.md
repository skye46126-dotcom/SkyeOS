# LifeOS Information Architecture And Cost Refactor Plan v1

## 1. Objective

This plan consolidates the current refactor requirements for `SkyeOS` into a code-level execution document.

Primary goals:

1. Remove `Settings` from the bottom navigation and turn it into a low-frequency utility entry.
2. Introduce a dedicated `Cost Management` surface instead of mixing business data with system settings.
3. Fix the data model and UI gaps around:
   - learning timeline visibility
   - note visibility
   - monthly and long-term expenses
   - historical review and drill-down
   - hourly-rate comparison metrics

This document is intended to guide implementation in multiple slices while keeping the app usable after each slice.

## 2. Current Code Reality

### 2.1 Navigation and screen ownership

- Main screen switching is hardcoded in [`app/src/main/java/com/example/skyeos/MainActivity.java`](../../app/src/main/java/com/example/skyeos/MainActivity.java)
- Bottom navigation contains `Today / Capture / Projects / Review / Settings` in [`app/src/main/res/menu/bottom_nav_menu.xml`](../../app/src/main/res/menu/bottom_nav_menu.xml)
- `SettingsFragment` currently combines:
  - cloud sync
  - backup/restore
  - AI API configuration
  - cost baseline input
  - tag management

This makes the screen too long and mixes system configuration with business operations.

### 2.2 Expense model already exists in data layer

The app already has 3 expense layers in schema:

1. Daily unpredictable expenses
   - `expense`
2. Monthly baseline expenses
   - `expense_baseline_month`
3. Long-running structural expenses
   - `expense_recurring_rule`
   - `expense_capex`

Relevant files:

- [`app/src/main/assets/db/migrations/V004__expense_structure.sql`](../../app/src/main/assets/db/migrations/V004__expense_structure.sql)
- [`app/src/main/java/com/example/skyeos/data/repository/SQLiteLifeOsMetricsRepository.java`](../../app/src/main/java/com/example/skyeos/data/repository/SQLiteLifeOsMetricsRepository.java)
- [`app/src/main/java/com/example/skyeos/data/repository/SQLiteLifeOsReviewRepository.java`](../../app/src/main/java/com/example/skyeos/data/repository/SQLiteLifeOsReviewRepository.java)

Current limitation:

- only `expense` has a real capture UI
- `expense_baseline_month` is only editable through settings for the current month
- `expense_recurring_rule` and `expense_capex` have no management UI

### 2.3 Hourly-rate logic is incomplete in product surface

The codebase already computes pieces of the target metrics:

- ideal hourly rate:
  - stored on `users.ideal_hourly_rate_cents`
- actual hourly rate:
  - computed from current-window income and work minutes
- previous-year average hourly rate:
  - partially computed inside project detail benchmark logic

Current limitation:

- Today only shows `Actual vs Ideal`
- Review does not expose `Previous Year Average`
- there is no dedicated cost dashboard to compare the three rates

### 2.4 Learning records are not full timeline records

Current `learning_record` fields:

- `occurred_on`
- `duration_minutes`
- `application_level`
- optional `efficiency_score`
- optional `ai_assist_ratio`

Current limitation:

- no `started_at`
- no `ended_at`
- cannot participate in precise daily timeline and drill-down chronology

## 3. Product Decisions

### 3.1 Navigation ownership

Target bottom navigation:

1. `Today`
2. `Capture`
3. `Projects`
4. `Review`

`Settings` becomes a utility entry:

- launched from a small icon in the `Today` header
- no longer consumes a primary bottom-nav slot

`Cost Management` becomes a business screen:

- accessible from `Today`
- optionally reachable from `Review`
- not embedded into `Settings`

### 3.2 Settings scope after refactor

`Settings` should keep only low-frequency configuration:

1. Cloud backup and restore
2. AI parser configuration
3. Tag management
4. App-level preferences

`Settings` should stop owning:

1. monthly cost operations
2. long-term expense rules
3. hourly-rate business dashboard

### 3.3 Cost management scope

New `Cost Management` surface should own:

1. Ideal hourly rate
2. Previous-year average hourly rate
3. Current actual hourly rate
4. Monthly baseline expenses
5. Recurring structural expenses
6. Capex amortization expenses
7. Daily structural burn estimation

### 3.4 Review and history scope

Review must support:

1. current day/week/month
2. anchor-date selection
3. year-level browsing
4. custom range
5. drill-down into a single day

The user requirement of “30 years” should not be implemented as a giant list. The correct model is hierarchical browsing:

1. Year overview
2. Month overview
3. Day detail
4. Optional custom range

## 4. Required Code Changes

### Slice A: Information architecture refactor

Goal:

- remove `Settings` from bottom nav
- add utility entry in `Today`
- add `Cost Management` screen skeleton

Changes:

1. `bottom_nav_menu.xml`
   - remove `nav_settings`
2. `MainActivity`
   - add direct fragment launcher methods for `SettingsFragment` and `CostManagementFragment`
3. `fragment_today.xml`
   - add compact settings icon in header
   - add cost-management entry card
4. `TodayFragment`
   - wire click handlers for new entries
5. create:
   - `CostManagementFragment`
   - `fragment_cost_management.xml`

Acceptance:

- no primary settings tab remains
- settings still accessible
- cost management has a stable entry point

### Slice B: Cost domain productization

Goal:

- stop treating costs as a few raw inputs inside settings
- expose real monthly and long-term cost management

Changes:

1. Add repository methods and use cases for:
   - list baseline months
   - read/update arbitrary month baseline
   - CRUD recurring rules
   - CRUD capex items
2. Add cost management UI sections:
   - rate comparison
   - baseline month editor
   - recurring rule list/editor
   - capex list/editor
3. Move current cost inputs out of `SettingsFragment`

Acceptance:

- user can manage a month other than current month
- user can add long-running monthly expenses
- structural daily burn is inspectable

### Slice C: Hourly-rate comparison dashboard

Goal:

- expose the three-rate model explicitly

Definitions:

1. Ideal hourly rate
   - user-entered target
2. Previous-year average hourly rate
   - previous calendar year total income / previous calendar year work hours
3. Current actual hourly rate
   - current window total income / current window work hours

Changes:

1. Add read model for `RateComparisonSummary`
2. Compute:
   - current day
   - current week
   - current month
   - current year
   - previous full year benchmark
3. Show comparison on:
   - Today
   - Cost Management
   - Review header

Acceptance:

- all three rates are shown together
- previous-year benchmark is not buried inside project detail only

### Slice D: Learning timeline refactor

Goal:

- make learning records behave like real timeline entries

Schema changes:

1. migration:
   - add nullable `started_at`
   - add nullable `ended_at`
2. keep `duration_minutes` for compatibility and summary queries

UI changes:

1. learning form supports:
   - start + end
   - or duration-only fallback
2. Today and Review single-day drill-down display learning chronologically

Acceptance:

- learning can appear in daily timeline order
- old rows still work

### Slice E: Review anchor-date and drill-down

Goal:

- support long-term retrospective browsing

Changes:

1. add anchor-date picker in `ReviewFragment`
2. add `year` and `custom range` review modes
3. add day-detail screen or bottom sheet
4. reuse detail rows for:
   - time
   - learning
   - income
   - expense
   - notes

Acceptance:

- user can browse historical years
- user can tap a day and inspect all underlying records

### Slice F: Timeline note visibility

Goal:

- ensure notes are visible in compact and detail surfaces

Changes:

1. compact summary on Today:
   - top 3 notes with truncation
2. single-day detail:
   - full note text
3. Review/history rows:
   - append note consistently

Acceptance:

- notes are visible without opening settings or raw DB views

## 5. Implementation Order

Recommended order:

1. Slice A
2. Slice C
3. Slice B
4. Slice D
5. Slice E
6. Slice F polish

Reason:

- Slice A fixes the information architecture first
- Slice C gives visible product value quickly
- Slice B establishes the missing cost management surface
- Slice D and E depend on the new navigation and review structure

## 6. Risks

### 6.1 Existing data compatibility

Risk:

- new learning time fields must not break old rows

Mitigation:

- nullable migration
- preserve duration-only logic

### 6.2 Metrics inconsistency

Risk:

- some code uses `updated_at`, some uses `occurred_on`, some uses `started_at`

Mitigation:

- standardize each metric by business meaning:
  - financial windows use `occurred_on`
  - time windows use `started_at`
  - only audit/history editing views may rely on `updated_at`

### 6.3 Settings regressions

Risk:

- removing the settings tab can strand existing functions

Mitigation:

- move navigation first, not functionality removal first
- keep `SettingsFragment` intact during Slice A

## 7. Immediate Work In This Turn

This turn should start with Slice A groundwork:

1. write this plan document
2. remove settings from bottom nav
3. add Today header utility entry for settings
4. add `CostManagementFragment` skeleton
5. add Today entry for cost management

After that, the next step should be Slice C:

1. add rate comparison read model
2. show `ideal / previous-year average / current actual` together
