# CLAUDE.md

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
./mvnw compile
./mvnw test
./mvnw verify  # Checkstyle; JaCoCo: 70% instructions, 90% classes
```

Use the Maven 3.9.11 wrapper (Java 21); Maven 3.10 cannot read the parent POM. On Windows use `./mvnw.cmd`.

## Architecture

Package: `com.kingsrook.qbits.filegateway`. Entry point: `FileGatewayQBitProducer implements
QBitMetaDataProducer<FileGatewayQBitConfig>`.

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
