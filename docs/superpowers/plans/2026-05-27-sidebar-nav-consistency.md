# Sidebar Nav Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make pages 12 and 13 use the same left sidebar navigation structure as the rest of the APS static pages.

**Architecture:** Keep the fix local to the static HTML shell. Add one tiny regression check for the canonical base-data nav entries, then update the two page sidebars to match the current shared structure with page-specific active states.

**Tech Stack:** Static HTML, PowerShell verification script

---

### Task 1: Add Sidebar Consistency Verification

**Files:**
- Create: `scripts/check-static-sidebar-nav.ps1`

- [ ] **Step 1: Write the failing verification script**

Create a script that checks `12-equipment-catalog.html` and `13-part-master.html` for the canonical `基础数据` nav links, including `14-shared-mold-rules.html`.

- [ ] **Step 2: Run script to verify it fails**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\check-static-sidebar-nav.ps1`
Expected: failure showing the missing `14-shared-mold-rules.html` entry in pages 12 and 13.

### Task 2: Align Static Sidebar Markup

**Files:**
- Modify: `aps-system/src/main/resources/static/12-equipment-catalog.html`
- Modify: `aps-system/src/main/resources/static/13-part-master.html`

- [ ] **Step 1: Update page 12 sidebar**

Insert the missing `共模规则` nav item in the `基础数据` group and keep the canonical order with `12-equipment-catalog.html` marked active.

- [ ] **Step 2: Update page 13 sidebar**

Insert the missing `共模规则` nav item in the `基础数据` group and keep the canonical order with `13-part-master.html` marked active.

- [ ] **Step 3: Run verification again**

Run: `powershell -ExecutionPolicy Bypass -File .\scripts\check-static-sidebar-nav.ps1`
Expected: pass for both files.

- [ ] **Step 4: Optional live spot-check**

Run: `Invoke-WebRequest -UseBasicParsing http://localhost:8081/12-equipment-catalog.html`
Run: `Invoke-WebRequest -UseBasicParsing http://localhost:8081/13-part-master.html`
Expected: HTTP 200 for both pages.
