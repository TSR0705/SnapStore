# 🗄️ SQLite WAL Forensic Storage & Timeline Database

FileX commits all detected incidents and evidence into a highly optimized SQLite database designed for deep timeline reconstruction and rapid operator queries.

---

## 1. Database Configuration & Pragmas

SQLite is configured with high-concurrency pragmas on launch to guarantee durability and speed:
*   **Write-Ahead Logging (`journal_mode=WAL`):** Enables parallel readers (JavaFX UI thread queries) and writers (incident persistence thread updates) without lock contention.
*   **Foreign Keys (`foreign_keys=ON`):** Enforces relational integrity across timelines and evidence maps.
*   **Synchronous State (`synchronous=NORMAL`):** Provides a great balance of transactional durability and disk I/O performance.

---

## 2. Dynamic Migrations Engine

Database schema updates are handled via an internal Java-based migration runner (`MigrationManager`).
Applied migrations are recorded sequentially inside the `schema_version` index table:

1.  `V001_InitialSchema`: Establishes primary system log formats.
2.  `V002_CreateIndexes`: Builds query acceleration indexes.
3.  `V005_CreateIncidentTables`: Provisions tables for incidents, timeline logs, and evidence links.
4.  `V007_FixEvidenceCascadeDelete`: Sets up cascading deletes on timeline items to keep data clean when purging incidents.
5.  `V008_AddTimelineUniqueConstraint`: Enforces integrity constraints on forensic timelines.

---

## 3. Database Schema Reference

### Table: `incidents`
Contains top-level alerts registered by the `AlertEngine`.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `incident_id` | TEXT | PRIMARY KEY | Unique GUID |
| `title` | TEXT | NOT NULL | Human-readable alert name |
| `description` | TEXT | - | Summary of threat |
| `severity` | TEXT | NOT NULL | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `rule_name` | TEXT | - | Heuristic rule identifier |
| `status` | TEXT | NOT NULL | `ACTIVE`, `RESOLVED`, `FALSE_POSITIVE` |
| `created_at` | TEXT | NOT NULL | ISO 8601 Timestamp |

### Table: `incident_evidence`
Tracks specific file assets flagged as dangerous evidence.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `evidence_id` | TEXT | PRIMARY KEY | Unique GUID |
| `incident_id` | TEXT | FOREIGN KEY REFERENCES `incidents` | Linked incident (Cascades) |
| `file_path` | TEXT | NOT NULL | Physical file path |
| `action_type` | TEXT | - | `CREATE`, `MODIFY`, `DELETE` |
| `occurred_at` | TEXT | NOT NULL | ISO 8601 Timestamp |

### Table: `forensic_timeline`
Maintains chronologically ordered timeline logs of the threat.

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `timeline_id` | TEXT | PRIMARY KEY | Unique GUID |
| `incident_id` | TEXT | FOREIGN KEY REFERENCES `incidents` | Linked incident (Cascades) |
| `event_type` | TEXT | - | Event stage identifier |
| `description` | TEXT | NOT NULL | Descriptive logging snippet |
| `occurred_at` | TEXT | NOT NULL | ISO 8601 Timestamp |
