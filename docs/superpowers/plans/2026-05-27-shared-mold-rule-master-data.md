# Shared Mold Rule Master Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace hardcoded shared-mold product pairs with maintainable master data that supports CRUD, import/export, and is consumed by workforce/equipment analysis.

**Architecture:** Add a `SharedMoldRule` entity, repository, service, controller, and static maintenance page aligned with the existing master-data modules. Update the analysis services to read enabled rules from the repository instead of static maps. Simplify the equipment analysis modal by making the detail list collapsible within a fixed-height area.

**Tech Stack:** Java, Spring Boot, JPA, Apache POI, static HTML

---
