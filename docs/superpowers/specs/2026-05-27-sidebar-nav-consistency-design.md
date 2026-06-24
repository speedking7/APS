# Sidebar Nav Consistency Design

## Goal

Unify the left sidebar navigation on `12-equipment-catalog.html` and `13-part-master.html` so it matches the current shared navigation structure used by the other static APS pages.

## Scope

- Only update the sidebar nav markup in:
  - `aps-system/src/main/resources/static/12-equipment-catalog.html`
  - `aps-system/src/main/resources/static/13-part-master.html`
- Do not change page content, data behavior, top bar, or styles beyond what the existing sidebar markup already uses.

## Root Cause

These two pages still use an older sidebar variant. Their `基础数据` group is missing the `14-shared-mold-rules.html` entry, so the left navigation differs from pages such as:

- `03-bom-list.html`
- `11-equipment-load.html`
- `14-shared-mold-rules.html`

## Chosen Approach

Copy the current canonical `基础数据` group structure and ordering into pages 12 and 13, preserving the page-specific `active` state.

Canonical order:

1. `02-forecast-list.html`
2. `03-bom-list.html`
3. `13-part-master.html`
4. `12-equipment-catalog.html`
5. `14-shared-mold-rules.html`
6. `04-material-params.html`
7. `05-operating-days.html`
8. `06-inventory-count.html`

## Verification

- Add a small script-level check that asserts pages 12 and 13 contain the full canonical `基础数据` nav link set.
- Run it before the change to confirm failure.
- Run it after the change to confirm pass.
