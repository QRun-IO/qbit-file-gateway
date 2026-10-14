# Changelog

All notable changes are documented here, following [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- File transfer Preferences with connect-permission visibility, read-only connection details,
  optional public hostname, self-service Add key/Revoke actions and editable key labels/expiry.
- First-key USER account creation with configured defaults and unique usernames; transactional
  ownership checks, friendly duplicate-key validation, core/HTTP tests and real SFTP acceptance.

- Phase (c) generated saved-view and saved-report downloads through the user-scoped UI API,
  CSV/XLSX/JSON account formats, a bounded rolling read window and HTTP stream cleanup.
- Generated-file and UTC daily-download quotas, persisted byte charges under account locks,
  per-file VIEW/REPORT transfer auditing and actionable SFTP API/limit errors.
- Real MINA download tests for filters/column order, POI XLSX validation, JSON, FieldSecurityLock
  DENY parity, report rendering, hidden tables/storage, large streaming exports and quotas.
- Phase (b) bounded streaming uploads through the object-storage system API, SHA-256, concurrent
  byte quotas, temporary-name promotion, dedupe and transfer auditing.
- Bounded upload workers with guarded claims, fresh user sessions, scanner/quarantine hooks,
  named consumers, retry scheduling, stale recovery and shutdown requeue.
- Built-in bulk-load consumer using only spec-defined UI API operations, saved mappings,
  inserted/error summaries, per-row examples, notifications and a Retry upload record action.
- Readable sidecars, results CSV and ranged archive downloads with account isolation and
  acknowledgement. Added `results_size_bytes` to the upload-job schema.
- Real MINA upload acceptance coverage, including permissions, auditing, limits, reordering,
  consumer retries, quarantine, worker concurrency and the unchanged no-disk guard.

- Phase (a) SSH-key-only SFTP runtime with in-memory host keys, explicit modern algorithms,
  account/key expiry, numeric CIDR allowlists, transactional account lockout/session limits,
  per-IP limits, user-session refresh and live revocation.
- Virtual listing/stat of permitted apps, tables, saved bulk-load profiles, views and reports
  through the strict UI API invoker, with per-session caches and paged saved-object queries.
- Seven gateway tables and record entities, H2/PostgreSQL DDL, enum sources, dependent endpoint
  pickers, joins, typed child lists, permissions and channel walls.
- SSH key/account validation and Review/Result actions to unlock/disable accounts, revoke keys
  and end sessions; connection and operation auditing, including denied mutations.
- Isolated consumer/outbox API contract and complete configuration for subsequent phases.
- Real SFTP/strict UI API acceptance tests, no-disk guard, schema round trips and adoption docs.

- Endpoints (aliases) with path validation and ENDPOINTS_ONLY chroot; BULK_LOAD, INBOX, VIEW,
  REPORT and OUTBOX kinds.
- The outbox provider (`FileGatewayOutbox.put`), outbox files, pickup tracking and `onDownloaded`.
- Optional per-account PGP decryption of uploads and encryption of downloads.
- The typed app home, `file_gateway_server` heartbeats, Unlock/Disable/Revoke/End session record
  actions, the daily retention process and `FileGatewayIntegrations`.
- Kubernetes example manifests in `docs/k8s/`.

### Compatibility

- Builds on QQQ `4.1.0-RC.7` (the first release with QRun-IO/qqq#1170). RDBMS remains a
  host-provided runtime dependency.
- Alert type `filegateway.lockout` uses the dotted syntax required by core validation.
- Object-storage RC.2 controls S3 part size through its connection configuration.
- Core #1170 sends multipart process values without re-uploading the original file.
- FieldSecurityLock requires core permission enforcement enabled. Saved-report rendering still
  uses a temporary file inside core; fully disk-free report generation needs a core change.
  The report folder is hidden without the render process's configured storage table.
