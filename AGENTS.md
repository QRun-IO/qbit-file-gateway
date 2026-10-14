# AGENTS.md

Guidance for coding agents working in this repository.

## Overview

QBit File Gateway runs an SFTP server (Apache MINA SSHD) that exposes a QQQ application as a
folder tree: apps, tables, `upload/<bulk-load-profile>/`, `views/`, `reports/`, `results/` and
`archive/`. Users and service accounts sign in with SSH public keys. All data access goes through
the `/qqq/v1` UI API with `UiApiInvoker` on the client channel `sftp`, as the signed-in user; never
through core actions directly. It runs as its own Kubernetes Deployment from the same application
code and QInstance. Design: `docs/DESIGN.md`.

## Build commands

```bash
./mvnw -o -Dqqq.snapshot.version=4.1.0-FG-SNAPSHOT compile
./mvnw -o -Dqqq.snapshot.version=4.1.0-FG-SNAPSHOT test
./mvnw -o -Dqqq.snapshot.version=4.1.0-FG-SNAPSHOT verify  # Checkstyle; JaCoCo: 70% instructions, 90% classes
```

Use the Maven 3.9.11 wrapper (Java 21); Maven 3.10 cannot read the parent POM. On Windows use `./mvnw.cmd` and quote the `-Dqqq.snapshot.version=4.1.0-FG-SNAPSHOT` argument in PowerShell.
Keep the POM default at `4.1.0-SNAPSHOT` for CI; the private version prevents local artifact replacement.

## Architecture

Package: `com.kingsrook.qbits.filegateway`. Entry point: `FileGatewayQBitProducer implements
QBitMetaDataProducer<FileGatewayQBitConfig>`.

- `metadata/`: seven stable tables, enum/picker sources, joins, typed child widgets, key/account
  validators and streamed Review/Result admin actions. Producer adds metadata to the QInstance.
- `model/`: QRecordEntity mappings; DDL in `docs/ddl/file_gateway.sql` uses UTC zone-less timestamps.
- `permissions/`: view/admin/connect constants and the custom permission checker; system-written
  logs/jobs/outbox tables remain read-only to people.
- `FileGatewayRuntime`: host-owned startup after the UI API server, in-memory host-key parsing,
  injectable environment and clock/test hooks. Production polling interval is 60 seconds.
- `sftp/`: public-key-only MINA listener, per-account transactional authentication, per-pod IP
  windows, token refresh/revocation and system-session bookkeeping restricted to owned tables.
- `vfs/`: custom NIO filesystem; per-session UI API metadata/saved-object caches. Every API call
  checks its operation spec and sends user credentials plus `sftp/<sessionId>` client detail.
- `api/`: isolated consumer/outbox contract. Never import gateway implementation packages here.

Phase (a) supports listing/stat and denies reads/mutations with audit. Upload/download workers,
endpoint routing/chroot, outbox delivery, Quick Search/ESB, preferences, retention and the app
home belong to later phases. The core-valid alert type is `filegateway.lockout`; the design's
underscore-only name is rejected by core validation.

Tests run a strict UI API server, H2 from the adopter DDL and real MINA clients. NoDiskExtension
snapshots the working tree (excluding target) and isolated test home, and asserts the test temp
directory is empty. The host supplies RDBMS; `qqq-backend-module-rdbms` must remain test-scoped.

## Invariants and naming

- Data access only through `/qqq/v1` (`UiApiInvoker`, channel `sftp`) as the signed-in user. The
  gateway writes only its own `file_gateway_*` tables directly.
- SFTP only, SSH-key auth only. No password login of any kind; plain FTP is never allowed.
- Tables use lowercase snake_case with the `file_gateway_` prefix; backend names equal table
  names. Permissions use the full `file_gateway` prefix. Processes are table or record actions,
  never navigation entries. One app home.
- No secrets in code or config: host keys come from env-var names.

## Code style

3-space indent, next-line braces, `return (x);`, flower-box Javadoc on classes and methods, fluent `withX()` setters. Follow `checkstyle/` and `CODE_STYLE.md`.

## Dependencies

Parent: `com.kingsrook:qbit-build-parent:2.0.0`. QQQ BOM: `4.1.0-SNAPSHOT` until 4.1.0-RC.6.
Only allow-listed qbit API artifacts (`qbit-object-storage` `api` package) may be depended on.
Notifications go through core `QAlerts.notify`, never qbit-alerts classes.

## Release flow

CircleCI uses `kingsrook/qqq-orb@0.6.8`: develop publishes SNAPSHOTs, release/* publishes RCs, and only `vX.Y.Z` GA tags publish releases. RC automation uses workflow_dispatch or a merged develop PR labelled `release:rc`. Feature branches start from develop and return by PR. Never push `hotfix/*` or `release/*` branches by hand; never publish manually.
