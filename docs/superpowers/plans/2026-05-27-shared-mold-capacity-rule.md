# Shared Mold Capacity Rule Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the shared-mold product-pair rule to workforce and equipment analysis so that, for configured product pairs, only the larger self-product `planQty` is used in capacity calculations when the rows share the same month and resource context.

**Architecture:** Implement the product-pair rule in the backend service layer only, scoped to self-product rows (`itemCode == finishedProductCode`). Reuse a single shared helper for pair matching, expose explicit detail fields in the DTOs, and keep plan calculation / plan result untouched.

**Tech Stack:** Java, Spring Boot, JUnit 5, static HTML

---
