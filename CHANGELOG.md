# Changelog

All notable changes are documented here, following [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

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

### Compatibility

- Local invoker integration builds use `4.1.0-FG-SNAPSHOT`; CI retains the POM's
  `4.1.0-SNAPSHOT` default. RDBMS remains a host-provided runtime dependency.
- Alert type `filegateway.lockout` uses the dotted syntax required by core validation.
- Uploads, downloads, endpoint routing/chroot, outbox delivery and app home remain later phases.
