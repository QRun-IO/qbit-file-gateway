# QBit File Gateway

An SSH-key-only SFTP server that presents a QQQ application's permitted apps and tables as a
virtual folder tree. Application metadata and saved objects are read through the spec-defined
`/qqq/v1` UI API as the signed-in user, using `UiApiInvoker` on channel `sftp`.

Phase (a) implements authentication, listing/stat, account administration and audit. Uploads,
downloads, endpoint routing/chroot, outbox delivery, preferences and the app home follow in later
phases. File reads and every mutation are denied and audited. No native filesystem, host-key
file, home directory or spool is used by the gateway. See [the approved design](docs/DESIGN.md).

## Requirements

Java 21, Apache MINA SSHD 2.16.0, and the QQQ 4.1 invoker APIs. The host supplies the RDBMS
backend and core saved bulk-load profile, saved view and saved report metadata. The gateway's
RDBMS module dependency is test-scoped. Maven 3.9.11 is pinned by the wrapper.

Until the invoker core PR merges, local builds use the private `4.1.0-FG-SNAPSHOT` artifacts.
The POM default remains `4.1.0-SNAPSHOT` for CI, to be replaced by 4.1.0-RC.6 when released.

## Install

```xml
<dependency>
   <groupId>com.kingsrook.qbits</groupId>
   <artifactId>qbit-file-gateway</artifactId>
   <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Adopting

Apply [docs/ddl/file_gateway.sql](docs/ddl/file_gateway.sql) to the host's database. It supports
H2 and PostgreSQL and stores UTC timestamps without a zone, matching QQQ's RDBMS binding.
The gateway does not create or migrate tables at runtime.

Register metadata while building the QInstance:

```java
FileGatewayQBitConfig config = new FileGatewayQBitConfig()
   .withBackendName("rdbms")
   .withSftpEnabled(Boolean.parseBoolean(System.getenv("FILE_GATEWAY_SFTP_ENABLED")))
   .withHostKeyEnvVarName("FILE_GATEWAY_SSH_HOST_KEY");

new FileGatewayQBitProducer().withQBitConfig(config).produce(qInstance);
```

After starting the host's `QApplicationJavalinServer`, start SFTP only in the gateway deployment:

```java
FileGatewayRuntime gateway = null;
if(config.getSftpEnabled())
{
   gateway = FileGatewayRuntime.start(qInstance, server);
}
// Call gateway.stop() during application shutdown when gateway is non-null.
```

Supply an unencrypted OpenSSH private host key as the value of the named environment variable.
It is parsed in memory; a missing key fails startup. `allowGeneratedHostKey` is for development
only and generates an ephemeral key with a warning. Each deployment uses the same host application
and QInstance; expose its SFTP listener separately from the UI HTTP port.

Create an ACTIVE account with a QQQ `userId`, then add an Ed25519, ECDSA, or RSA public key of at
least 3072 bits. Public-key validation runs on insert and update and computes its unique SHA-256
fingerprint, type and size. The host authentication module must assign the user's permissions
when `createAutomatedSessionForUser` is called, including `file_gateway.connect`.

## Tables and permissions

All seven tables are registered, with camelCase QQQ fields mapped to snake_case database columns.
`backendName` selects the host backend; physical table names match the QQQ table names.

| Table | Purpose |
|---|---|
| `file_gateway_account` | User/service login, limits, CIDRs, expiry and lockout state |
| `file_gateway_ssh_key` | Account child keys and computed fingerprints |
| `file_gateway_alias` | Endpoint definitions; routing is a later phase |
| `file_gateway_upload_job` | Stable schema for the later upload worker |
| `file_gateway_outbox_file` | Stable schema for later outbound delivery |
| `file_gateway_transfer_log` | Listings and denied operations |
| `file_gateway_connection_log` | Accept, auth outcome, heartbeat and disconnect |

One File Gateway app lists Accounts, Endpoints, Upload Jobs, Transfers and Connections. Keys,
outbox files and related jobs/logs appear in typed child lists. Endpoint saved-object pickers
are filtered by the selected table. There is no HTML widget or navigation process.

| Permission | Grants |
|---|---|
| `file_gateway.connect` | SFTP login, checked again when the user session is refreshed |
| `file_gateway.view` | App and read access to gateway tables |
| `file_gateway.admin` | Read access and management of accounts, keys, endpoints and record actions |

System-written job/outbox/audit tables are read-only to people, including admins. Record actions
use the standard Review/Result flow: Unlock account, Disable account, Revoke key and End session.
End session is enforced by the owning listener's next revocation poll. Retry upload is phase (b).

The `sftp` channel cannot access gateway, Authentik, audit, credential, API-key, secret, password,
permission, role, user-session or webhook administration surfaces. The `assistant` channel also
cannot access gateway tables.

## Listing and authentication

The root lists visible apps; apps contain child apps and readable tables. Table folders include:

- `upload/<profile-slug>/` when a visible profile and the table's bulk-insert process exist;
- `views/<slug>.csv` and `.xlsx` (or account-enabled JSON);
- `reports/<slug>.<format>` when the saved-report table and render process are available;
- empty `results/` and `archive/` folders reserved for later phases.

Hidden paths return no-such-file. Slug collisions append record IDs. Saved-object queries are
paged. Metadata and saved objects are cached per connection for 60 and 30 seconds respectively.
Generated files report size zero and reads return "not yet supported" in phase (a).

Only public-key auth is advertised. Password, keyboard-interactive, GSSAPI, host-based auth,
shell, exec and forwarding are disabled. The algorithm lists exclude CBC, SHA-1 MAC/KEX and
`ssh-rsa` signatures. Idle timeout is 15 minutes; authentication timeout is 30 seconds.

Account failure counters, operation-rate windows and session reservations are serialized with a database row lock across
listeners. Per-IP failure and connection windows are held in each pod. Unlocking an account also
clears an IP lock caused solely by that account on its next attempt; mixed-user IP locks expire
independently. User sessions are re-minted after five minutes on the next operation, expire after
ten minutes, and are revoked on close. The 60-second poll closes disabled/expired/revoked sessions
and heartbeats connection rows; three-minute-old heartbeats no longer count as active sessions.

Lockouts raise core `QAlerts` signals. **Core compatibility:** the alert type is
`filegateway.lockout`, because the core validator rejects the design's underscore-only
`file_gateway_lockout`. Without an alert sink the core logging fallback is used.

## Configuration

Every option has a getter and fluent `withX(...)` setter. Later-phase options are present in the
contract but do not enable transfers in phase (a).

| Options | Defaults |
|---|---|
| `backendName`, `sftpEnabled`, `sftpPort` | `rdbms`, `false`, `2222` |
| `hostKeyEnvVarName`, `allowGeneratedHostKey` | `FILE_GATEWAY_SSH_HOST_KEY`, `false` |
| `lockoutThreshold`, `lockoutWindow`, `lockoutDuration` | 5, 15 minutes, 30 minutes |
| `maxConnectionsPerIpPerMinute` | 30 |
| `defaultMaxConcurrentSessions`, `defaultMaxOperationsPerMinute` | 4, 600 |
| `defaultMaxFileSizeMb` | 100 |
| `defaultMaxUploadMbPerDay`, `defaultMaxDownloadMbPerDay` | null (unlimited) |
| `fileGatewayBucketAlias` | `file-gateway` |
| `uploadPartSizeMb`, `uploadReorderWindowMb`, `downloadReadAheadMb`, `maxGeneratedFileMb` | 8, 64, 16, 1024 |
| `uploadWorkerThreads`, `dedupeWindow`, `jobStaleAfter` | 4, 7 days, 30 minutes |
| `temporaryUploadSuffixes` | `.filepart`, `.tmp`, `.part`, `.partial` |
| `scanner`, `pgpPrivateKeyEnvVarName`, `pgpPassphraseEnvVarName` | null |
| `logRetention`, `archiveRetention`, `outboxRetention`, `resultsRetention`, `receivingStaleAfter` | 180 days, 30 days, 30 days, 90 days, 1 hour |
| `tableMetaDataCustomizer` | null |

## Consumer and outbox API

The isolated `com.kingsrook.qbits.filegateway.api` package contains `FileGatewayConsumer`,
`InboundFile` (including remote address and a fresh stream supplier), `FileAcceptance`,
`OutboxFileRef`, and the consumer metadata registry. `FileGatewayOutbox` delegates to a registered
`FileGatewayOutboxProvider`; without one it reports unavailable and throws
`FileGatewayUnavailableException`. Phase (a) supplies the contract only; the later outbox runtime
will install the provider.

## Build and acceptance tests

```bash
./mvnw -o -Dqqq.snapshot.version=4.1.0-FG-SNAPSHOT verify
```

PowerShell:

```powershell
./mvnw.cmd -o '-Dqqq.snapshot.version=4.1.0-FG-SNAPSHOT' verify
```

The tests use H2 from the adopter DDL, a strict UI API server, generated in-memory SSH keys and
real MINA clients. They exercise permissions, auth failures, lockout, competing listeners,
revocation/refresh, pagination/caching, audit, key validation, contingent pickers, API isolation,
metadata registration and record serialization. Checkstyle and JaCoCo gates require 70%
instruction coverage and 90% class coverage.

Surefire isolates `user.home` under `target/fg-home` and `java.io.tmpdir` under `target/fg-tmp`.
The disk guard creates those directories before tests, compares working-tree and home snapshots,
and requires an empty temp directory after each protocol test. Build output under `target/`
is excluded from the working-tree snapshot.

## License

[Apache License 2.0](LICENSE). Copyright Kingsrook, LLC.
