# QBit File Gateway

An SSH-key-only SFTP server that presents a QQQ application's permitted apps and tables as a
virtual folder tree. Application metadata and saved objects are read through the spec-defined
`/qqq/v1` UI API as the signed-in user, using `UiApiInvoker` on channel `sftp`.

Phases (a) through (c) implement authentication, listing/stat, account administration, uploads into
saved bulk-load profiles, background consumer delivery, readable sidecars/results/archive and
saved-view/report downloads. Endpoint routing/chroot, outbox delivery, preferences and the app
home follow in later phases. No native filesystem, host-key file, home directory or spool is
used by the gateway. See [the approved design](docs/DESIGN.md).

## Requirements

Java 21, Apache MINA SSHD 2.16.0, and the QQQ 4.1 invoker APIs. The host supplies the RDBMS
backend, qbit-object-storage 0.1.0-RC.2, and core saved bulk-load profile, saved view and saved
report metadata. The gateway's
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
| `file_gateway_upload_job` | Receiving, queued and completed uploads, retries, results and consumer references |
| `file_gateway_outbox_file` | Stable schema for later outbound delivery |
| `file_gateway_transfer_log` | Listings, uploads, object downloads and denied operations |
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
use the standard Review/Result flow: Unlock account, Disable account, Revoke key, End session and
Retry upload. End session is enforced by the owning listener's next revocation poll. Retry upload
requeues FAILED/REJECTED jobs with a completed stored object and resets their attempts; it
rechecks eligibility under an account lock when applying the action.

The `sftp` channel cannot access gateway, Authentik, audit, credential, API-key, secret, password,
permission, role, user-session or webhook administration surfaces. The `assistant` channel also
cannot access gateway tables.

## Listing and authentication

The root lists visible apps; apps contain child apps and readable tables. Table folders include:

- `upload/<profile-slug>/` when the user has insert permission, a visible profile and a bulk-insert process;
- `views/<slug>.csv` and `.xlsx` (or account-enabled JSON);
- `reports/<slug>.<format>` when the saved-report table, render process and its configured storage table are available;
- account-scoped `results/<jobId>-<file>.results.csv` and `archive/<yyyy-mm-dd>/<jobId>-<file>`.

Hidden paths return no-such-file. Slug collisions append record IDs. Saved-object queries are
paged. Metadata and saved objects are cached per connection for 60 and 30 seconds respectively.
Saved-view/report files report size zero (unknown); clients read until EOF. Stored artifacts
report their actual lengths and support ranged reads.

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

## Generated downloads

Opening `views/<slug>.<format>` queries the saved view again as the account user and starts
`POST /table/{table}/export` through `UiApiInvoker.invokeForStream`. The saved view's
`queryFilter` (including `orderBys`) and visible `queryColumns.columns` are preserved in order;
the UI selection column and pagination are excluded. Legacy explicit `fieldNames` are also
supported. CSV includes a header; CSV, XLSX and JSON are offered only when enabled in the
account's `formats` (default `csv,xlsx`).

Opening `reports/<slug>.<format>` selects that saved report through the `renderSavedReport`
record action, submits `reportFormat`, polls asynchronous status and streams the returned
`/download/{file}` storage reference. The process's `storageTableName` input default must name
an existing storage-capable table, backed by object storage in production. Without that table,
`reports/` is hidden. Reports requiring additional interactive values fail with the API's error.

Each generated handle retains at most `downloadReadAheadMb` MiB (default 16). Sequential reads
and reordered reads within this rolling window work; a seek outside it fails with a message
to download sequentially. The HTTP stream closes with the SFTP handle. Read requests are filled
until EOF so MINA does not mistake a short HTTP packet for the end of an unknown-size file.

`maxGeneratedFileMb` (default 1024) and `max_download_mb_per_day` are enforced as generated
bytes enter the window. The daily total queries DOWNLOAD transfer rows for this account since
UTC midnight, including failed and in-progress transfers; updates hold the account row lock so
concurrent generated downloads share the cap. A rejected chunk is not exposed. Re-reading cached
bytes does not charge them twice. One DOWNLOAD row per handle records the view/report ID, format,
bytes, duration and outcome: OK, LIMIT, ERROR or DENIED for an HTTP 403. API error text reaches
the SFTP client.

**Field security:** configure `QInstance.enforcePermissionsInCoreActions=true` when using this
private core build's `FieldSecurityLock` enforcement. Acceptance tests use a DENY lock and compare
the generated CSV byte-for-byte with a direct UI export by the same user: default columns omit
the denied field. Explicitly selecting a denied field fails as it does through the UI API.

**Core filesystem limitation:** the current private core's `RenderSavedReportExecuteStep`
creates and deletes a temporary file before publishing to storage. The gateway does not spool,
and the existing no-disk snapshot/cleanup guard passes, but that guard cannot detect transient
files. Fully disk-free saved-report generation remains blocked on replacing core's temporary
file with storage streaming. CSV/XLSX/JSON table exports use core's streaming exporters; the
50,000-row CSV acceptance test consumes the result with a fixed buffer.

## Uploads and object storage

Configure an object-storage bucket alias matching `config.fileGatewayBucketAlias` (default
`file-gateway`). Production always uses `ObjectStorage.asSystem("file-gateway", runId)` and only
the allow-listed `com.kingsrook.qbits.objectstorage.api` package. Tests inject a memory store.
Objects use `file-gateway/uploads/{accountId}/{yyyy}/{mm}/{dd}/{uuid}/{fileName}`;
results and quarantine use separate `file-gateway/results/` and `file-gateway/quarantine/` prefixes.
The original object backs the virtual archive; it is not copied after a successful import.

`uploadPartSizeMb` bounds the gateway pipe and `uploadReorderWindowMb` bounds pending writes
and gaps. The storage API streams unknown-length content and owns multipart aborts. Its actual
S3 part size is configured on the **object-storage connection** (`multipartPartSizeMb`); set it
to 8 for the design's default part size. RC.2 does not expose a per-put part-size override through
its API. Account file-size and UTC daily-upload quotas are reserved in the database while writing,
including concurrent uploads on other pods. Failed transfers retain the received-byte charge.

The host's `QJavalinMetaData.uploadedFileArchiveTableName` must name a storage-capable table
backed by object storage, not a filesystem backend. The bulk-insert UI API uses that table for
its process input. The test harness uses core memory storage. Configure the host HTTP server's
request limits for the permitted file sizes; these limits also apply to loopback UI API calls.

A normal close queues the job. Names ending in `.filepart`, `.tmp`, `.part` or `.partial` stay
RECEIVING after close until renamed within the same profile folder. Each put is a new job.
Dedupe skips content that already SUCCEEDED for the same account, table, profile and consumer
within `dedupeWindow`; its `.done` text identifies the earlier job.

The worker mints a fresh account-user session, requires `file_gateway.connect`, sends every
application request through the `sftp` UI API channel, refreshes credentials after five minutes,
and revokes them after delivery. It follows the bulk-insert process through upload, mapping,
review and result. Profiles must contain their value mappings; the worker fails instead of
interactively guessing a mapping. Results contain core summary lines and row examples where
core supplies them; a blank CSV field column means no field identifier was supplied by core.

**Process steps:** the mapping step sends its values as form fields only; the invoker encodes
them as multipart because the step operation declares a multipart body (QRun-IO/qqq#1170). The
file is uploaded once, at process init.

Workers are bounded by `uploadWorkerThreads`. Claims compare the expected status inside a
short account-row transaction (core's expected-version check alone is not a SQL conditional
update). Completion also checks the owning pod and claim timestamp. Retries back off by
1 minute, 5 minutes, 15 minutes and 1 hour, then fail on the fifth attempt. The configured
schedule also includes a 6-hour slot; the five-attempt cap takes precedence. Consumer-requested
delays can lengthen the scheduled wait. Stale RUNNING jobs requeue with a three-start cap;
shutdown releases this pod's claims. Delivery is at least once, so custom consumers must dedupe
on job ID and digest.

Optional `FileGatewayScannerInterface` implementations return CLEAN, INFECTED or ERROR.
INFECTED objects are copied into quarantine and removed from the original key; ERROR retries.
Consumers receive an `InboundFile` with fresh object streams and run with the user's `QContext`.
Unknown consumer names are rejected.

`<file>.processing` appears during receiving/queueing/running. Finished jobs expose `.done` or
`.error` text. Sidecars and results disappear after acknowledgement (`rm` on either artifact)
or seven days. Archive files remain until later retention processing. Core notifications go to
the account user (service account: `ownerUserId`) on FAILED/PARTIAL/REJECTED and on SUCCEEDED
when `notifyOnSuccess` is enabled. Without an alerts sink, sending is a no-op.

Existing phase-(a) databases need this additional column before adopting phase (b):

```sql
ALTER TABLE file_gateway_upload_job ADD COLUMN results_size_bytes BIGINT;
```

## Configuration

Every option has a getter and fluent `withX(...)` setter. Configuration for later phases remains
present without enabling those features.

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
`FileGatewayUnavailableException`. INBOX endpoints bind a consumer by `consumer_name`; the built-in
`bulk-load` consumer serves `upload/<profile>/` folders and BULK_LOAD endpoints. The outbox
provider is registered by the producer in every deployment (it needs only the database and object
storage), so other qbits can call `FileGatewayOutbox.put(...)` from web pods too.

## Endpoints and chroot

`file_gateway_alias` rows ("Endpoints") mount partner-friendly paths such as `/acme/orders-in`:

| Kind | Behaviour |
|---|---|
| `BULK_LOAD` | upload folder that bulk-loads with the endpoint's saved profile |
| `INBOX` | upload folder whose files go to the `consumer_name` consumer |
| `VIEW`, `REPORT` | one generated file (`file_name`, or `<slug>.<format>`) |
| `OUTBOX` | files put by `FileGatewayOutbox`; partners download and delete them |

Paths are absolute, `[a-z0-9._/-]`, unique, and never collide with app names. An endpoint is
listed only when it is active, bound to the account (or shared), and its target is reachable by
the user through `/qqq/v1`; endpoints grant nothing. `root_mode` FULL_TREE (default for users)
shows the app tree plus endpoints; ENDPOINTS_ONLY (default for service accounts) is the chroot.

## Outbox

`put` streams to `file-gateway/outbox/{aliasId}/{uuid}/{fileName}` with a SHA-256 and a
`file_gateway_outbox_file` row (READY). The first complete download marks it DOWNLOADED and calls
`onDownloaded` on the endpoint's consumer (at-least-once); `rm` marks it REMOVED; retention marks
old files EXPIRED and deletes the object.

## PGP (optional, per account)

`pgp_decrypt_uploads`: uploads named `*.pgp`/`*.gpg` are decrypted stream to stream into a new
object before the consumer runs, with the key and passphrase from the env vars named by
`pgpPrivateKeyEnvVarName`/`pgpPassphraseEnvVarName` (in memory only); a bad file is REJECTED.
`pgp_encrypt_downloads`: downloads are encrypted on the fly to the account's `pgp_public_key`
(validated on save) and listed with `.pgp` appended.

## App home, actions and retention

The `file_gateway` app home uses typed widgets only: a KPI row (transfers today, failed jobs in
24 hours, active sessions, MB in and out today, locked accounts), and full-width tables for recent
failures, top partners (7 days), active sessions and gateway servers (host key fingerprint, port,
pod, last seen, from `file_gateway_server` heartbeats). Record actions (standard Review/Result,
never in the nav): Unlock account, Disable account, Revoke key, End session, Retry upload. A daily
scheduled retention process applies the `*Retention` settings and aborts stale RECEIVING uploads.

## Integrations

`FileGatewayIntegrations` lists what a host wires in without this qbit depending on those modules:
`esbTableNames()` (accounts, endpoints, upload jobs), `quickSearchTables()` (accounts by username
and label, endpoints by name and path, upload jobs by file name) and `apiTableNames()` (all tables,
with the admin's rules).

## Kubernetes

`docs/k8s/gateway-deployment.yaml` and `docs/k8s/gateway-service.yaml`: the same image as the web
Deployment with `FILE_GATEWAY_SFTP_ENABLED=true`, the host key from a Secret, a read-only root
filesystem with an in-memory `/tmp`, 2 replicas and a PDB, and a LoadBalancer Service exposing
port 22 only (no passive range: v1 is SFTP only) with `externalTrafficPolicy: Local`.

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
