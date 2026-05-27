# Workforce And Equipment Detail Modal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add calculation-detail modals to the workforce and equipment analysis pages, backed by explicit server-side intermediate fields so the UI shows the true formulas and values used by the services.

**Architecture:** Extend `WorkforceDetailRow` and `EquipmentLoadRow` with the exact inputs and intermediate values needed by the UI, verify the service outputs with unit tests and controller tests, then add row-click modals to `10-workforce-report.html` and `11-equipment-load.html` using those fields directly.

**Tech Stack:** Java, Spring Boot, JUnit 5, static HTML, browser JavaScript

---
