# Cancer Treatment Schedule Schema

## Scope

The cancer treatment schedule service runs as a separate MediPlat-linked system on port `8083`. Its tables use the `ct_` prefix so they can live in the shared local database without colliding with CSM or MediPlat tables.

This first schema pass is non-destructive. It only creates tables when they do not exist and does not drop, rename, or mutate existing data.

## External Schema

| Consumer | View | Primary Use |
| --- | --- | --- |
| Treatment schedule screen | Daily treatment schedule by date, ward, treatment type, and status | Real-time schedule board and status update |
| Treatment calendar | Treatment counts and entries across dates | Date-level planning and review |
| Patient management | Active patient list and treatment notes | Patient lookup while building schedules |
| Dashboard | Daily status, treatment, and ward aggregates | Operational monitoring |
| Settings | Treatment types, options, statuses, and time slots | Hospital-specific schedule configuration |

## Conceptual Schema

| Entity | Responsibility | Relationships |
| --- | --- | --- |
| `ct_patient` | Canonical patient snapshot for the cancer treatment service | One patient can have many schedules |
| `ct_treatment_type` | Configurable treatment name and room mapping | One type can be used by many schedules |
| `ct_treatment_option` | Configurable option/code shown in schedule cells | One option can be used by many schedules |
| `ct_treatment_status` | Configurable schedule status vocabulary | Referenced by `ct_treatment_schedule.status_code` |
| `ct_time_slot` | Configurable daily appointment time slots | Used to render schedule columns/rows |
| `ct_treatment_schedule` | Daily schedule entry and operational status | References patient, type, and option where available |

## Internal Schema

| Table | Key Access Path | Indexes |
| --- | --- | --- |
| `ct_patient` | `inst_code`, `active_yn`, patient name/chart search | `(inst_code, active_yn)`, `(inst_code, patient_name)`, `(inst_code, chart_no)` |
| `ct_treatment_type` | Active type list ordered by hospital display order | unique `(inst_code, treatment_name)`, `(inst_code, active_yn, display_order)` |
| `ct_treatment_option` | Active option list ordered by hospital display order | unique `(inst_code, option_code)`, `(inst_code, active_yn, display_order)` |
| `ct_treatment_status` | Active status list ordered by hospital display order | unique `(inst_code, status_code)`, `(inst_code, active_yn, display_order)` |
| `ct_time_slot` | Active time slots ordered by hospital display order | unique `(inst_code, start_time)`, `(inst_code, active_yn, display_order)` |
| `ct_treatment_schedule` | Daily schedule board and SSE change lookup | `(inst_code, treatment_date, start_time)`, `(inst_code, status_code, treatment_date)`, `(patient_id, treatment_date)` |

## Data Standards

| Field | Format | Rule |
| --- | --- | --- |
| `inst_code` | `VARCHAR(50)` | MediPlat institution code. Required on all tenant-scoped tables. |
| `active_yn` | `CHAR(1)` | `Y` or `N`. Deactivate records instead of deleting user-facing configuration. |
| `status_code` | `VARCHAR(30)` | Stable code such as `RESERVED`, `COMPLETED`, `CANCELED`; UI labels come from `ct_treatment_status.status_name`. |
| `treatment_date` | `DATE` | Hospital-local treatment date. |
| `start_time` | `TIME` | Hospital-local appointment start time. |
| `patient_name_snapshot` | `VARCHAR(100)` | Required so imported or historical schedule rows remain readable even if patient linkage is missing or later edited. |

## Integrity

Entity integrity is enforced through surrogate primary keys. Domain integrity is enforced through required tenant/date/time/status/name columns and `active_yn` flags. Referential integrity is applied from schedules to patients, treatment types, and treatment options, while `patient_id` stays nullable to support staged imports from Google Sheets where a patient row may not be matched yet.

`ct_treatment_schedule.status_code` intentionally starts without a foreign key to `ct_treatment_status.status_code` because MySQL cannot reference a non-unique subset when `inst_code` is also part of the status key. The service layer should validate status codes by `inst_code` before write operations.

## Transaction Boundaries

Schedule create/update/status-change operations should be a single transaction that writes `ct_treatment_schedule`, updates audit fields, and emits the SSE event only after the database write succeeds. Settings updates should be separate transactions per configuration entity. Bulk imports from Google Sheets should use batched transactions by date or sheet chunk so a bad row does not roll back an entire operational day.

Default isolation can remain the database default `READ COMMITTED` or MySQL `REPEATABLE READ` for normal reads. If duplicate schedule slots must be prevented later, add a unique key over `(inst_code, treatment_date, start_time, treatment_type_id)` after confirming the hospital’s real scheduling rules.

## Capacity And Recovery

The initial workload is OLTP and small: daily board reads, status writes, and SSE fan-out. The current indexes target the daily board first because it is the highest-frequency screen. A year of schedules should remain queryable in the primary table; archival can be considered after real row counts are measured.

Backups should follow the database used by the deployed service. For production, keep daily full backups plus point-in-time recovery if MySQL binlogs are enabled. Recovery testing should include restoring `ct_` tables with MediPlat SSO linkage still intact.
