# QBit File Gateway: design

Status: **Approved** (owner, 2026-10-13) · drafted 2026-10-13 · Author: James Maes (drafted with Claude)

Targets:
- **`QRun-IO/qbit-file-gateway`** (Apache-2.0): the SFTP server, the virtual filesystem, accounts
  and keys, uploads, downloads, endpoints (aliases), the consumer and outbox API, audit tables and
  the app home.
- **QQQ core 4.1.x** (`qqq-middleware-javalin`): three small additions to `UiApiInvoker` (§3). The
  gateway needs them because it acts as a user who signed in with an SSH key, not through an HTTP
  request, and because it moves files rather than JSON.
- **`QRun-IO/qbit-authentik`**: permissions for automated sessions (§3.4).
- **kof22** (`KofTwentyTwo/Website-Backend`, `KofTwentyTwo/cluster-gitops`): adoption, the gateway
  Deployment and its LoadBalancer IP (§11).

> **Owner decisions (2026-10-12): all recommendations accepted.** Every open question in this spec
> is decided as recommended ("they are approved. we will adjust as needed once we can view them
> working").
>
> **Owner decisions (2026-10-13):**
> - **A separate gateway deployment.** It runs the same application code and QInstance as its own
>   Kubernetes Deployment, with its own LoadBalancer IP and port, and scales independently.
> - **Data only through `/qqq/v1`.**
>   - The gateway reaches application data ONLY through the UI API, as the signed-in user, via
>     `UiApiInvoker` on the client channel `sftp`.
>   - It never calls core actions on application data.
>   - It writes its own `file_gateway_*` tables directly.
> - **Auth is SSH public keys per user.**
>   - Users add keys on the core Preferences page, in a "File transfer" section.
>   - Service accounts (Authentik service users) also get keys, plus IP allowlists, quotas and
>     expiry.
> - **Scope change: v1 is SFTP ONLY, with SSH-key auth.**
>   - FTPS is dropped from v1, and there is no password login of any kind (no password and no
>     keyboard-interactive). Plain FTP is never allowed.
>   - The auth and protocol layers stay pluggable (§6.5).
>   - "FTPS (future)" is in §15. It would use revocable app passwords and/or client certificates
>     plus a passive port range, and is added only if a partner needs it.
>   - The former phase (d), FTPS, is removed. The k8s example has one SFTP port and no passive
>     range, and the tests use only the MINA SFTP client.
> - **No files or filesystem: just the database and S3.** The owner's words: "there are no files
>   or filesystem - just the database and s3".
>   - The SFTP tree is purely virtual, built from metadata and data rows.
>   - Uploads stream directly to object storage (qbit-object-storage), with database rows.
>   - Downloads are generated or streamed from export, report or object storage.
>   - There is NO local disk persistence of any kind: no spool directories and no home
>     directories.
>   - Temporary buffering is in memory (bounded). Where a library insists on a file, the gateway
>     uses a bounded area under the JVM temp directory and deletes it in a `finally` block.
>     Streaming is preferred everywhere.
>   - The pod is stateless and replaceable at any time. The host key and configuration come from
>     secrets and database rows.
>   - A test asserts that nothing is written outside the JVM temp area, and that the temp area is
>     empty after each transfer (§12).
> - **The consumer and outbox API (coordinator, with qbit-edi).**
>   - A small allow-listed API package, `com.kingsrook.qbits.filegateway.api` (guardrail G1).
>   - It defines a consumer SPI for inbound files and an outbox for partners that pull (§9).
>   - Bulk load is the default consumer.
>   - Consumers are bound to endpoint folders by data (`consumer_name` on the endpoint row).
>   - The shape was agreed with the qbit-edi agent on 2026-10-13.

## Owner request (2026-10-13, verbatim)

> the sftp/ftps idea is that the server would run a sftp/ftps server itself, and you would log in
> via your account or a service account etc. and you could cd into apps, tables, etc. and from
> there you can upload files, that would go into a bulk upload profile into that table, and you can
> download that table via getting a saved view etc. same with the ability to 'get' a saved report
> etc. allowing for complex data flows without any coding, just setting up bulk upload profiles and
> views and reports etc.

## Mandatory standards that shape this design
- **Data.** It lives in standard, visible QQQ tables (`file_gateway_*`) with full record screens,
  secured by permissions. Permissions use the full name (`file_gateway.*`).
- **Processes.**
  - Processes are table or record actions with plain labels, never in the nav.
  - Record actions use the standard streamed-ETL Review/Result summary.
- **Navigation.** One app home owns the data. The nav lists only the main tables; SSH keys and
  outbox files are child lists.
- **API and search.** The API matches the admin. The main tables publish ESB table events and are
  in Quick Search.
- **Notifications and alerts.** Notifications go through core `QAlerts.notify` (the bell) and
  alerts through `QAlerts.raise`. There is never a dependency on qbit-alerts classes.
- **Storage** goes through qbit-object-storage (`com.kingsrook.qbits.objectstorage.api`).
- **Display.** Pages are readable at all widths, with no double scrollbars, in light and dark
  mode. Typed widgets only, never HTML widgets (G5).

## 1. Architecture

```
 partner / user SFTP client
        │  ssh (publickey only), port 22 → 2222
        ▼
 ┌─────────── gateway Deployment (same image, FILE_GATEWAY_SFTP_ENABLED=true, stateless) ───────┐
 │ Apache MINA SSHD ──► GatewayPublickeyAuthenticator ──► file_gateway_account / _ssh_key        │
 │      │                       (lockout, CIDR allowlist, expiry, limits)                         │
 │      ▼                                                                                         │
 │ SFTP subsystem ──► GatewayFileSystem (NIO provider over the VirtualTree; no disk)             │
 │      │                    │ list / stat / open / write / rename / remove                      │
 │      │                    ▼                                                                    │
 │      │            GatewayUiApiClient ── UiApiInvoker(channel "sftp", invokedBy "file-gateway")│
 │      │                    │   credentials = in-process session minted for the account's user  │
 │      │                    ▼   (loopback HTTP to this pod's own QApplicationJavalinServer)     │
 │      │            /qqq/v1: metaData, table query, export, process init/step, download          │
 │      ▼                                                                                         │
 │ uploads ──stream──► object storage (sha256, dedupe) ──► UploadJobWorker ──► consumer          │
 │                                                          (bulk-load | edi | …)                │
 │ Audit writer ──► file_gateway_connection_log / _transfer_log (system session, own tables)     │
 └───────────────────────────────────────────────────────────────────────────────────────────────┘
 main web Deployment: same code, FILE_GATEWAY_SFTP_ENABLED=false (admin tables, app home, prefs)
```

- **The gateway pod runs the normal application,** including its `QApplicationJavalinServer`. The
  invoker calls the pod's own `/qqq/v1` on `127.0.0.1`. The Service exposes only the SFTP port;
  HTTP serves only loopback calls and kubelet probes.
- **Everything the user sees or does is a `/qqq/v1` call as that user.** So the following all
  apply exactly as in the UI:
  - permissions;
  - secured-data masking (enforced in core under the UI API);
  - audits (`audit.clientChannel = sftp`);
  - record locks;
  - table automations.
- **The gateway's own bookkeeping is written server-side** under a system session, to its own
  `file_gateway_*` tables. That covers connection and transfer logs, upload jobs, lockout counters
  and outbox rows. These tables are not application data, and the SFTP user has no permission on
  them.
- **State lives only in the database and object storage.**
  - A pod can be killed at any moment.
  - Open sessions drop; the client reconnects to another pod.
  - An upload whose stream did not complete stays `RECEIVING`, and the retention job aborts it
    after 1 hour, deleting its partial object.
  - Claimed jobs are requeued (§4).

## 2. The virtual filesystem

The tree is generated from metadata and data, and filtered by the user's permissions. Nothing
exists on any disk.

```
/                                   apps the user can see (GET /metaData), as folders
/<app>/                             child apps and the app's tables (by table name)
/<app>/<table>/
    upload/                         one folder per saved bulk-load profile the user can see
        <profile-slug>/             write a file here to bulk-load it with that profile
            <file>.processing       sidecar while queued or running (from the job row)
            <file>.done | .error    sidecar when finished (text summary, from the job row)
    views/<view-slug>.<csv|xlsx|json>       saved views of this table (generated on read)
    reports/<report-slug>.<csv|xlsx|json>   saved reports on this table (generated on read)
    results/<jobId>-<file>.results.csv      per-row results of this account's uploads (object storage)
    archive/<yyyy-mm-dd>/<jobId>-<file>     the original uploaded files (object storage, retention §8)
/<endpoint path>/                   endpoints (aliases) bound to this account, §2.3
```

### 2.1 Names
- **Apps and tables** use their QQQ `name`, which is stable and has no spaces.
- **Saved profiles, views and reports** use a slug of their label: lowercase, `[a-z0-9-]`, at
  most 80 characters.
  - When labels collide within a folder, both get `-<id>` appended.
  - Slugs are computed per listing, so a rename shows on the next listing.
- **Formats** are `csv`, `xlsx` and `json`, the formats of `/table/{t}/export`. Each view and
  report is listed once per format enabled for the account (`formats`; default csv and xlsx).

### 2.2 Visibility rules (all from the user's own UI API calls)
| Node | Shown when |
|---|---|
| app | the app is in the user's `GET /metaData` app tree |
| table | the table is in the app and in `metaData.tables` (read permission) |
| `upload/` | the table has the bulk-insert process (`<table>.bulkInsert`) in metaData, and at least one saved bulk-load profile is visible (`POST /table/savedBulkLoadProfile/query` filtered by `tableName`) |
| `views/` | the user can query `savedView` |
| `reports/` | the user can query `savedReport`, has the `renderSavedReport` process, and that process's `storageTableName` input default names an existing storage-capable table |
| `results/`, `archive/` | always (they hold only this account's jobs) |

- **Caching.** Metadata responses are cached in memory per SFTP session for 60 seconds. Saved-object
  lists are cached for 30 seconds.
- **Empty folders.** A folder is listed even when empty, once its parent's rule passes, so
  partners can see where to put files.
- **Hidden paths.** A path the user cannot see answers "no such file", never "permission denied".

### 2.3 Endpoints (aliases) and chroot
A `file_gateway_alias` row gives a partner-friendly path, such as `/acme/orders-in`, that maps to
one target:

| `kind` | Target | Folder behaviour |
|---|---|---|
| `BULK_LOAD` | table + saved bulk-load profile | an upload folder (§4) |
| `INBOX` | `consumer_name` | an upload folder whose files go to a consumer (§9) |
| `VIEW` | saved view + format + file name | one generated file |
| `REPORT` | saved report + format + file name | one generated file |
| `OUTBOX` | outbox files for this endpoint | files put by `FileGatewayOutbox` (§9); partners download and delete them |

- **Endpoints grant nothing.**
  - Every endpoint operation is the same `/qqq/v1` call as the user, through the full path.
  - An endpoint whose target the user cannot reach is not listed.
  - INBOX and OUTBOX endpoints need the account to be bound to them: either `account_id` matches,
    or `account_id` is null (shared).
- **Root mode.** The account's `root_mode` sets what `/` shows:
  - `FULL_TREE` (the default for users) shows the app tree plus the endpoints.
  - `ENDPOINTS_ONLY` (the default for service accounts) shows only the account's endpoints. This
    is the chroot: `/` lists the endpoints' top-level folders.

### 2.4 Operations
| SFTP operation | Allowed on | Behaviour |
|---|---|---|
| `ls`, `stat` | everything visible | Generated files report size 0 and the current time. Object-storage files report their stored size. |
| `get` | views, reports, results, archive, outbox files | Generated or streamed on read (§5). |
| `put` | `upload/<profile>/` and BULK_LOAD/INBOX endpoints | Streams to object storage, then queues a job (§4). |
| `rename` | inside one upload folder | Supports clients that upload `x.filepart` or `x.tmp`, then rename. A job is queued on close only for a non-temporary name, or on the rename to one. |
| `rm` | sidecars, `results/*` and outbox files | On a sidecar or result it acknowledges the job, which hides its sidecar and results. On an outbox file it marks the file removed. |
| `rm` (other paths), `mkdir`, `rmdir`, `setstat`, symlinks | none | Denied. |
| overwrite | none | A second put of the same name is a new job, subject to dedupe. |

Temporary suffixes are `.filepart`, `.tmp`, `.part` and `.partial`, configurable as
`temporaryUploadSuffixes`.

## 3. Core dependencies

### 3.1 In-process sessions for `UiApiInvoker` (qqq-middleware-javalin)
The invoker forwards the caller's HTTP credentials, but an SFTP user has none. The solution is an
in-process session token; there is never a service session.

- **Registering.** `UiApiInProcessSessions.register(QSession session, Duration ttl)` returns a
  random 256-bit token, held only in this JVM's memory. `revoke(token)` and expiry remove it.
- **Sending.** `UiApiCredentials.ofInProcessSession(token)` puts the token in the
  `X-QQQ-In-Process-Session` header on the loopback request.
- **Accepting.** `ExecutorSessionUtils.setupSession` (and the legacy
  `QJavalinImplementation.setupSession`) accept the header only when both are true:
  - the request's remote address is a loopback address;
  - the token is registered and unexpired in this JVM.

  Otherwise the request gets a 401, with no fallback to other credentials.
- **Per-request copy.** The registered session is copied per request. The client channel is still
  set from `X-QQQ-Client`, which the invoker fixes (§6.4).
- **Minting.**
  - The gateway builds the session with the application's authentication module:
    `createAutomatedSessionForUser(qInstance, account.userId)`. Scheduled saved reports already
    use this. Under OAuth2 it calls the customizer's `customizeAutomatedSessionForUser`.
  - A session is minted at SFTP login and re-minted every 5 minutes, so permission changes and
    revocations apply quickly.
  - It is revoked at disconnect, with a TTL of 10 minutes.

### 3.2 Binary responses
- `UiApiInvoker.invokeForStream(UiApiRequest)` returns `UiApiStreamResponse`: `AutoCloseable`,
  with the status, headers and an `InputStream` (`HttpResponse.BodyHandlers.ofInputStream`).
- It is used for `POST /table/{t}/export`, `GET /download/{file}` and `GET /reports/{name}`.
- Bodies are never buffered whole.

### 3.3 Multipart uploads
- `UiApiRequest.withMultipartFile(fieldName, fileName, contentType, Supplier<InputStream>)`, plus
  form fields.
- It is sent as streamed `multipart/form-data`.
- It is used for `POST /processes/{p}/init`, the bulk-load upload step
  (`ProcessSpecUtilsV1.addUploadedFiles`).

### Shipping
§3.1–3.3 go in one core PR to `QRun-IO/qqq` `develop`, for 4.1.0-RC.6. Until then the qbit builds
on `4.1.0-SNAPSHOT`, with the pom marked `STAND-IN(removeBy=4.1.0-RC.6)`.

### 3.4 Permissions for automated sessions (qbit-authentik)
`AuthentikGroupPermissionMapper` builds permissions from token claims, and an automated session
has no token.

- **The change.** It implements `customizeAutomatedSessionForUser`:
  - it looks the user up by id through the Authentik API backend (the cached group graph);
  - it applies the same group, ancestor, fallback and bootstrap rules;
  - if Authentik cannot be read, it grants only the static fallbacks.
- **Service users** are Authentik users with `type=service_account`.
- **Who needs it.** It is a qbit-authentik PR. The gateway works without it under MOCK auth and in
  tests, but kof22 needs it before going live.

### 3.5 Preferences (QRun-IO/qqq#1162)
The gateway registers a "File transfer" section with the provider `FileTransferPreferencesProvider`.

- **Scalar fields:** the SFTP username (read-only), "Add a public key" (TEXT) and "Key label".
- **The `keys` list:** label, fingerprint (read-only), last used (read-only), expires and revoked.
- **Behaviour.** The first key creates the user's `USER` account. Revoking a key sets `revoked_at`.
- **Until #1162 is in the baseline,** admins add keys on the account record.

## 4. Upload
1. **Receive.** The SFTP write streams straight to object storage:
   - The key is `file-gateway/uploads/{accountId}/{yyyy}/{mm}/{dd}/{uuid}/{fileName}`, in the bucket
     alias `fileGatewayBucketAlias` (default `file-gateway`).
   - Writes go out as multipart-upload parts of 8 MiB, buffered in memory.
   - SHA-256 and the size are computed in flight. `max_file_size_mb` and the daily quota are
     enforced while writing; an overrun aborts the write with a failure status.
   - Clients write SFTP blocks at increasing offsets. Out-of-order blocks are held in a bounded
     in-memory window (64 MiB per upload); a gap larger than the window fails the write.
   - A `file_gateway_upload_job` row is created in `RECEIVING`.
2. **Close.**
   - The multipart upload is completed.
   - The job becomes `DUPLICATE` (with `duplicate_of_job_id`) when the account has
     `dedupe_uploads` and a job with the same account, target and sha256 succeeded within
     `dedupeWindow` (7 days). Nothing runs, and the `.done` sidecar says so.
   - Otherwise the job becomes `QUEUED`.
3. **Scan (optional).** `FileGatewayScannerInterface` (a `QCodeReference` in the config) reads the
   object as a stream and returns CLEAN, INFECTED or ERROR.
   - INFECTED: the job becomes `REJECTED`, and the object is copied to `file-gateway/quarantine/`
     and deleted from the upload key.
   - ERROR: the scan is retried.
4. **PGP (optional, phase e).** When the account has `pgp_decrypt_uploads` and the name ends in
   `.pgp` or `.gpg`:
   - the object is decrypted stream to stream into a new object, using the key named by
     `pgpPrivateKeyEnvVarName`;
   - the plaintext's sha256 is recorded.
5. **Run.** The worker hands the job to the endpoint's consumer (§9). For `upload/<profile>/`
   folders that is `bulk-load`, which runs the table's bulk-insert process as the user, all through
   `/qqq/v1`:
   - `POST /processes/{table}.bulkInsert/init`, multipart, streaming the object (§3.3);
   - `POST /processes/{p}/{uuid}/step/receiveFileMapping` with `savedBulkLoadProfileId`, which
     applies the saved mapping;
   - if value mapping is requested, the job **fails** with "this profile needs value mappings
     saved in it"; the gateway never guesses;
   - continue through review, execute and result, polling `/status` for async steps;
   - read the summary lines and per-row errors from the result.
6. **Finish.**
   - The job ends `SUCCEEDED`, `PARTIAL` (some rows failed) or `FAILED`, recording counts, the
     summary and `process_uuid`.
   - A results CSV (row, field, message, plus the summary lines) is written to object storage,
     straight from memory.
   - The sidecar becomes `.done` or `.error`.
   - The original appears in `archive/`, as the same object.
7. **Notify.** `QAlerts.notify` goes to the account's user, or to `owner_user_id` for service
   accounts:
   - always on FAILED, PARTIAL and REJECTED;
   - on SUCCEEDED only when `notify_on_success` is set.

   The notification links to the upload-job record.
8. **Worker.**
   - Bounded pool per gateway pod: `uploadWorkerThreads`, default 4.
   - A worker claims a job by updating QUEUED to RUNNING, setting `claimed_by` (the pod) and
     `claimed_at`.
   - A job stuck RUNNING longer than `jobStaleAfter` (30 minutes), for example after a killed pod,
     is requeued, up to 3 attempts.
   - The record action "Retry upload" requeues a FAILED or REJECTED job from its stored object.

## 5. Download
Everything is streamed. Nothing is written to disk.

The gateway itself streams without disk. Core's `RenderSavedReportExecuteStep` stages the
rendered report in a JVM temp file and deletes it in try-with-resources before the process step
returns. That is the bounded temp area the owner rule allows, so the `emptyDir` temp mount must be
sized for the largest report. The no-disk test asserts the temp area is empty after each transfer.

- **Saved view** (`views/<slug>.<fmt>`):
  - `POST /table/savedView/query` by table name, as the user;
  - the filter, visible columns and order are parsed from `viewJson`;
  - `POST /table/{table}/export` with `format`, `filter` and `fieldNames`, through
    `invokeForStream`.
- **Saved report** (`reports/<slug>.<fmt>`):
  - `POST /processes/renderSavedReport/init` with `recordIds=<id>` and `reportFormat`;
  - `GET /download/{file}?storageTableName&storageReference` from the process output, streamed.
- **Generating on open.**
  - The export starts when the client opens the file, and SFTP reads are served from the stream
    in order.
  - Clients pipeline reads at increasing offsets. A bounded in-memory read-ahead window (16 MiB per
    open file) absorbs reordering.
  - A read before the window (a backward seek) re-opens the stream for objects in storage. For
    generated files it fails, telling the client to download sequentially.
  - Every common client (OpenSSH `sftp`, WinSCP, FileZilla, lftp, MINA) reads sequentially.
- **Limits:** the account's `max_download_mb_per_day` and `maxGeneratedFileMb` (default 1024),
  counted as bytes flow.
- **Masking.** Secured-data masking applies because core enforces it under the UI API: export and
  report bytes carry exactly what the user may see.
- **Results, archive and outbox** files stream from object storage with ranged reads, so seeks
  work.
- **PGP (phase e).** When the account has `pgp_encrypt_downloads`, the stream is encrypted on the
  fly to the account's `pgp_public_key`, and `.pgp` is appended to the listed names.

## 6. Protocol and security
### 6.1 SFTP server
- **Server.** Apache MINA SSHD (`sshd-core`, `sshd-sftp`), SFTP subsystem only.
  - Shell, exec, port forwarding, agent forwarding and X11 are disabled.
  - No `VirtualFileSystemFactory` or native filesystem is ever used. The SFTP subsystem's
    filesystem is the gateway's NIO provider over the virtual tree.
- **Auth: public key only.** `setPasswordAuthenticator(null)`; keyboard-interactive, GSSAPI and
  host-based auth are disabled.
- **Algorithms offered:**
  - KEX: curve25519, ecdh-nistp256/384/521, and DH group16/18 with sha512;
  - ciphers: chacha20-poly1305, aes-gcm and aes-ctr;
  - MACs: hmac-sha2-256/512 (etm).
- **Never offered:** CBC, SHA-1 MACs or KEX, or `ssh-rsa` (SHA-1) signatures.
- **User keys:** Ed25519, ECDSA, and RSA of at least 3072 bits (rsa-sha2-256/512).
- **Host key.**
  - The OpenSSH private key comes from the env var named by `hostKeyEnvVarName` (default
    `FILE_GATEWAY_SSH_HOST_KEY`), a Kubernetes Secret. It is parsed in memory and never written to
    disk.
  - When it is missing, startup fails. The exception is `allowGeneratedHostKey` (tests and dev
    only, in memory, with a warning).
  - The host key fingerprint is shown on the app home.
- **Timeouts:** 15 minutes idle; auth within 30 seconds.

### 6.2 Accounts and authentication
- **Login.** The login name is `file_gateway_account.username`.
  - The offered key must match, by SHA-256 fingerprint, an unrevoked and unexpired
    `file_gateway_ssh_key` of that account.
  - The account must be `ACTIVE` and unexpired.
  - The client IP must be inside `allowed_cidrs` (empty means any; service accounts should set it).
- **Lockout.**
  - 5 failed auths within 15 minutes, per username or per IP, lock that username or IP for 30
    minutes. All three numbers are configurable.
  - A lock sets `locked_until` and raises an alert (`filegateway.lockout`, `QAlerts.raise`; core alert-type names use dots, not the table prefix).
  - The record action "Unlock account" clears it.
  - Lockout state lives in the database, so it holds across pods.
  - A per-IP limit on new connections (30 per minute) applies before auth.
- **Per-account limits:**
  - `max_concurrent_sessions` (default 4);
  - `max_operations_per_minute` (default 600);
  - `max_file_size_mb` (default 100);
  - `max_upload_mb_per_day` and `max_download_mb_per_day` (default unlimited).

  An exceeded limit returns an SFTP failure and logs outcome `LIMIT`.
- **Revocation.** Every 60 seconds each pod re-reads the accounts that have live sessions. A
  disabled or expired account, or a revoked key, ends its sessions.

### 6.3 Who can do what
| Permission | Grants |
|---|---|
| `file_gateway.connect` | Sign in over SFTP at all. Checked at login through the minted session's permissions. |
| `file_gateway.view` | The app, its home and read access to all `file_gateway_*` tables. |
| `file_gateway.admin` | Insert, edit and delete on accounts, keys and endpoints; the record actions; managing other users' keys. |

- Table permissions use the table names (`file_gateway_account.read`, and so on) under the app
  permission.
- Self-service goes through Preferences only (§3.5).

### 6.4 Client channel and walls
- **Registration.** `ClientChannelsMetaData.ofOrWithNew(qInstance).withChannel("sftp", "SFTP file
  gateway")`.
- **Headers.** Every invoker call sends `X-QQQ-Client: sftp/<sessionId>` and
  `X-QQQ-Invoked-By: file-gateway`.
- **The `sftp` wall.** The channel is denied:
  - `file_gateway_*`;
  - the security surfaces of the assistant's default wall: `authentik_*`, `audit*`,
    `*api_key*`, `*apikey*`, `*credential*`, `*secret*`, `*password*`, `*permission*`, the role
    tables, `*user_session*` and `webhooks_*`.
- **The `assistant` channel** is denied `file_gateway_*`.

### 6.5 Pluggable layers
- `FileGatewayAuthenticatorInterface` turns a protocol credential into an account. v1 ships only
  the SSH-key authenticator.
- `FileGatewayProtocolServer` (start, stop, active sessions) has one v1 implementation:
  `SftpProtocolServer`.
- The virtual tree, UI API client, job pipeline and audit writer are protocol-neutral, so FTPS
  (§15) would add only a server and an authenticator.

## 7. Audit
- **Connections.** Every connection writes `file_gateway_connection_log`. The row is opened at TCP
  accept and updated at auth and at disconnect. It records the outcome, IP, key fingerprint, SSH
  client version, byte and operation counts, and channel `sftp`.
- **Operations.** Every list, upload, download, rename and remove writes
  `file_gateway_transfer_log`. It records the operation, path, object kind and id, bytes, sha256,
  duration, outcome and message, with channel `sftp/<sessionId>`.
  - A directory read (`opendir`) is logged once per read; `stat` is not logged.
  - Denied attempts are logged with outcome `DENIED`.
- **Data changes.** Changes made by bulk loads carry `audit.clientChannel = sftp/<sessionId>` from
  core. Secured-data views are logged in that qbit's own access log.

## 8. Tables
All tables are RDBMS tables in the app's backend, lowercase snake_case, with backend names equal to
the table names.

| Table | Purpose | Key fields |
|---|---|---|
| `file_gateway_account` (main) | an SFTP login | `username` (unique, `[a-z0-9._-]{3,64}`), `label`, `account_type` (USER, SERVICE), `user_id` (the QQQ user the session acts as), `owner_user_id`, `status` (ACTIVE, DISABLED, LOCKED), `root_mode` (FULL_TREE, ENDPOINTS_ONLY), `allowed_cidrs`, `expires_at`, `formats`, the limits (§6.2), `dedupe_uploads`, `notify_on_success`, `pgp_public_key`, `pgp_decrypt_uploads`, `pgp_encrypt_downloads`, `failed_login_count`, `locked_until`, `last_login_at`, `last_login_ip` |
| `file_gateway_ssh_key` (child of account) | public keys | `account_id`, `label`, `public_key` (OpenSSH text), `fingerprint` (unique `SHA256:...`), `key_type`, `key_bits`, `expires_at`, `revoked_at`, `last_used_at`, `last_used_ip` |
| `file_gateway_alias` (main, "Endpoints") | partner paths | `name` (unique slug), `path` (unique), `kind` (BULK_LOAD, INBOX, VIEW, REPORT, OUTBOX), `account_id` (null = shared), `table_name`, `saved_bulk_load_profile_id`, `saved_view_id`, `saved_report_id`, `format`, `file_name`, `consumer_name`, `is_active`, `description` |
| `file_gateway_upload_job` (main) | one uploaded file | `account_id`, `alias_id`, `connection_log_id`, `remote_address`, `table_name`, `saved_bulk_load_profile_id`, `consumer_name`, `consumer_reference`, `path`, `file_name`, `size_bytes`, `sha256`, `storage_key`, `status` (RECEIVING, QUEUED, RUNNING, SUCCEEDED, PARTIAL, FAILED, REJECTED, DUPLICATE, ABORTED), `duplicate_of_job_id`, `process_uuid`, `inserted_count`, `error_count`, `result_summary`, `results_storage_key`, `error_message`, `attempts`, `next_attempt_at`, `claimed_by`, `claimed_at`, `queued_at`, `started_at`, `finished_at`, `acknowledged_at` |
| `file_gateway_outbox_file` (child of endpoint) | files waiting for pickup | `alias_id`, `file_name`, `size_bytes`, `sha256`, `storage_key`, `status` (READY, DOWNLOADED, REMOVED, EXPIRED), `first_downloaded_at`, `download_count`, `removed_at`, `expires_at`, `created_by_consumer` |
| `file_gateway_transfer_log` (main, "Transfers") | every operation | `account_id`, `connection_log_id`, `operation` (LIST, UPLOAD, DOWNLOAD, RENAME, REMOVE), `path`, `object_kind` (APP, TABLE, PROFILE, VIEW, REPORT, RESULT, ARCHIVE, ENDPOINT, OUTBOX), `object_id`, `format`, `bytes`, `sha256`, `duration_ms`, `outcome` (OK, DENIED, NOT_FOUND, LIMIT, ERROR), `message`, `client_channel`, `upload_job_id` |
| `file_gateway_connection_log` (main, "Connections") | every connection | `account_id`, `username`, `remote_ip`, `client_version`, `key_fingerprint`, `outcome` (CONNECTED, AUTH_FAILED, LOCKED_OUT, IP_DENIED, EXPIRED, DISABLED, LIMIT), `session_id`, `pod_name`, `connected_at`, `last_seen_at`, `disconnected_at`, `disconnect_reason`, `bytes_in`, `bytes_out`, `operation_count`, `client_channel` |

- **Child lists:**
  - an account shows its keys, upload jobs and connections;
  - an endpoint shows its upload jobs and outbox files;
  - an upload job shows its transfers.
- **Quick search and events.** Accounts (username, label), endpoints (name, path) and upload jobs
  (file name) are in Quick Search and publish ESB table events. All tables are in the QQQ API.
- **Retention.** A daily scheduled job, not in the nav, applies these defaults:
  - transfer and connection logs: 180 days;
  - archive objects: 30 days;
  - outbox files: 30 days;
  - results: 90 days;
  - `RECEIVING` jobs older than 1 hour become `ABORTED`, and their objects are deleted.
- **Active sessions** are connection rows with no `disconnected_at` and a `last_seen_at` within the
  last 3 minutes. Pods update `last_seen_at` every minute.
- **Record actions** (standard streamed ETL, Review then Result):
  - "Unlock account";
  - "Disable account";
  - "Revoke key";
  - "Retry upload" (FAILED, REJECTED);
  - "End session" (on active connections; the owning pod closes it within 60 seconds).
- **Key validation on insert.** The key is parsed and checked against the §6.1 minimums. The
  fingerprint, type and bits are computed server-side. Keys are classified CONFIDENTIAL.

### App home (`file_gateway`)
Typed widgets only, with real data:
- **KPI row:** transfers today, failed jobs in the last 24 hours, active sessions, MB in and out
  today, and locked accounts.
- **Recent failures:** a table of upload jobs that were FAILED, PARTIAL or REJECTED in the last 7
  days, linked to their records.
- **Top partners (7 days):** a table of accounts by transfers and MB.
- **Active sessions:** a table of username, IP, connected since, operations and pod.
- **Server:** the host key fingerprint, the port, and the gateway pods seen in the last 3 minutes.

## 9. Consumer and outbox API (`com.kingsrook.qbits.filegateway.api`)
- The package is published in the `qbit-file-gateway` artifact and allow-listed under G1 for other
  qbits, like object-storage's `api`.
- It holds interfaces and records only, with no runtime and no tables.
- Consumers (qbit-edi) depend on it with `<optional>true</optional>`.
- The shape was agreed with the qbit-edi agent on 2026-10-13.

```java
public interface FileGatewayConsumer
{
   String getName();                                        // lowercase [a-z0-9-]+, e.g. "edi"
   FileAcceptance accept(InboundFile file) throws Exception;
   default void onDownloaded(OutboxFileRef ref) throws Exception { }
}

public record InboundFile(Integer uploadJobId, String endpointName, String username, String userId,
                          String remoteAddress, String path, String fileName, long sizeBytes, String sha256,
                          Instant receivedAt, String storageBucketAlias, String storageKey,
                          Supplier<InputStream> streamSupplier)
{
   public InputStream openStream() { return (streamSupplier.get()); }      // fresh stream from object storage each call
}

public final class FileAcceptance   // ACCEPTED | REJECTED | RETRY_LATER
{
   public static FileAcceptance accepted(String consumerReference);
   public static FileAcceptance rejected(String reason);
   public static FileAcceptance retryLater(String reason, Duration retryAfter);
}

public record OutboxFileRef(Integer outboxFileId, String endpointName, String path, String fileName,
                            long sizeBytes, String sha256, Instant createdAt) { }

public final class FileGatewayOutbox
{
   public static boolean isAvailable();
   public static OutboxFileRef put(String endpointName, String fileName, InputStream content, long sizeBytes)
      throws FileGatewayUnavailableException, QException;
}

FileGatewayConsumersMetaData.ofOrWithNew(qInstance).withConsumer(new QCodeReference(EdiFileGatewayConsumer.class));
```

- **Binding is by data.**
  - An INBOX endpoint row names its `consumer_name`.
  - `upload/<profile>/` folders and BULK_LOAD endpoints use the built-in `bulk-load` consumer.
- **`accept` delivery.**
  - It runs after close, the scan and PGP, at-least-once, on a gateway worker.
  - QContext holds the account user's session, with channel `sftp`.
  - Consumers keep `accept` short: store the file and return.
  - RETRY_LATER, or an exception, retries after 1m, 5m, 15m, 1h and 6h. After 5 attempts the job is
    FAILED and the user is notified.
  - An unknown consumer gives REJECTED.
  - Gateway dedupe (§4) runs first; consumers dedupe on (uploadJobId, sha256).
- **Outbox.**
  - `put` streams the content to object storage, adds a `file_gateway_outbox_file` row, and the
    file lists in the OUTBOX endpoint's folder.
  - The first complete download calls `onDownloaded` on the endpoint's `consumer_name`, if one is
    set, at-least-once.
  - When the partner removes the file, it is marked REMOVED.
  - `put` to a missing or inactive endpoint throws `QException`.
  - The facade dispatches to the implementation that the producer registers. Without the gateway
    installed, it throws `FileGatewayUnavailableException`.

## 10. Configuration (`FileGatewayQBitConfig`)
- **SFTP server:**
  - `sftpEnabled` (default false; true only in the gateway deployment, from
    `FILE_GATEWAY_SFTP_ENABLED`);
  - `sftpPort` (2222);
  - `hostKeyEnvVarName` (`FILE_GATEWAY_SSH_HOST_KEY`);
  - `allowGeneratedHostKey` (false).
- **Storage and buffers:**
  - `fileGatewayBucketAlias` (`file-gateway`);
  - `uploadPartSizeMb` (8);
  - `uploadReorderWindowMb` (64);
  - `downloadReadAheadMb` (16);
  - `maxGeneratedFileMb` (1024).
- **Upload jobs:**
  - `uploadWorkerThreads` (4);
  - `dedupeWindow` (7 days);
  - `jobStaleAfter` (30 minutes);
  - `temporaryUploadSuffixes`.
- **Lockout and limits.** The lockout and per-IP limits (§6.2), plus the defaults for new
  accounts.
- **Optional:**
  - `scanner` (a `QCodeReference`);
  - `pgpPrivateKeyEnvVarName` and `pgpPassphraseEnvVarName`;
  - retention (§8);
  - `tableMetaDataCustomizer`.

## 11. Kubernetes
The gateway is a separate, stateless Deployment of the same image. An example is in `docs/k8s/`:
`gateway-deployment.yaml` and `gateway-service.yaml`.

- **Deployment.**
  - `FILE_GATEWAY_SFTP_ENABLED=true`, plus the app's usual environment.
  - `FILE_GATEWAY_SSH_HOST_KEY` from a Secret; in kof22, through ESO from Infisical.
  - 2 replicas, with a PodDisruptionBudget of `minAvailable: 1`.
  - `readOnlyRootFilesystem: true`. The only writable mount is a small in-memory `emptyDir`
    (`medium: Memory`, `sizeLimit: 512Mi`, counted in the memory limit) for the JVM temp directory, sized for the largest saved report core stages there. Nothing is persisted.
  - Requests of 250m CPU and 768Mi memory; a 2Gi memory limit. The in-memory buffers are bounded
    by the config.
- **Ports and probes.**
  - Container ports: 2222 (sftp) and the app's HTTP port, which serves loopback and probes only.
  - Readiness: `tcpSocket: 2222` and the app's HTTP health endpoint.
  - Liveness: HTTP health.
  - Startup: allows 2 minutes for the QInstance.
- **Service.** `type: LoadBalancer`, with these settings:
  - the annotation `lbipam.cilium.io/ips: 10.100.22.65` in kof22 (owner, 2026-10-13; .60, .61 and .64 are taken);
  - **port 22 → 2222 only (no passive range in v1)**;
  - `externalTrafficPolicy: Local`, so the pod sees the real client IP for allowlists and
    lockouts.
- **Shutdown.** On SIGTERM a pod stops accepting connections and lets sessions drain for up to 60
  seconds. Then it marks its own connection rows ended and requeues its claimed jobs.
- **UniFi.** The WAN 22 (or 2222) port forward to the LB IP is an owner step, in Praetor
  `unifi/` tofu.

## 12. Tests (acceptance criteria)
All tests run in the qbit's build.

- **Harness:**
  - an embedded `QApplicationJavalinServer` with `withStrictUiApiRoutes(true)` (G2) on a random
    port;
  - an H2 RDBMS backend and a memory object-storage bucket;
  - the embedded SFTP server on a random port, with an in-memory generated host key;
  - the MINA SSHD client (`SftpClient`), with real keys generated per test.
- **Listing per permission.**
  - Two users with different permissions see different apps, tables, and `upload/`, `views/` and
    `reports/` folders.
  - A hidden table answers "no such file".
  - An `ENDPOINTS_ONLY` account sees only its endpoints.
- **Upload to bulk load, end to end.**
  - Putting a CSV into `upload/<profile>/` creates a job, runs the saved profile as the user and
    inserts the rows.
  - It writes `.done` and the results file, and archives the original.
  - A bad row gives `PARTIAL` with per-row errors.
  - The same file again gives `DUPLICATE`.
  - A `.filepart` upload followed by a rename queues once.
  - `QAlerts.notify` is called.
- **Downloading a view with masking.**
  - `views/<slug>.csv` returns the view's filter and columns.
  - A field the user may not read comes back masked or absent, exactly as the `/qqq/v1` export
    returns it.
- **Getting a report.** `reports/<slug>.csv` runs `renderSavedReport` as the user and streams the
  result.
- **Auth failures.**
  - These are refused, each with a connection-log outcome: an unknown key, a revoked key, an
    expired key, an expired or disabled account, and an IP outside the allowlist.
  - Password and keyboard-interactive are never offered.
- **Lockout.** 5 bad attempts lock the account and raise the alert. "Unlock account" clears the
  lock.
- **Audit.**
  - Connect, list, upload and download each write their rows, with channel `sftp/...`.
  - Core audits of inserted rows carry `sftp`.
- **Limits.** The file-size cap aborts an upload. The session cap refuses the fifth session.
- **No disk (owner rule).** Every transfer test runs under a filesystem guard:
  - `java.io.tmpdir` points at a fresh per-test directory;
  - a snapshot check lists the working directory, `user.home` and the temp
    directory before and after each test;
  - the test asserts that nothing was created outside the temp area, and that the temp area is
    empty after each transfer completes.
- **Consumer API.**
  - A test consumer bound to an INBOX endpoint receives an `InboundFile` with the correct sha256,
    `remoteAddress` and stream.
  - RETRY_LATER retries.
  - An outbox `put` lists in the OUTBOX folder, and the download calls `onDownloaded`.
- **Stateless restart.** Stop the server mid-session and start a new one on the same database and
  bucket. Jobs requeue, lockouts persist, and the client reconnects and sees the same tree.
- **Contract.** The invoker is only ever called with spec'd operations, asserted through
  `UiApiInvoker.findOperation`. The qbit registers no `/qqq/v1` extension, so G3 does not apply.
- **Screenshots.** The app home and the account, upload-job and endpoint records, in light and
  dark, at 390 and 1440 px.

## 13. Guardrails (ARCHITECTURE-REVIEW-2026-10-13)
- **G1.** The only qbit dependency is `qbit-object-storage` (its `api` package). There is no
  `Class.forName` of another qbit. The gateway's own `api` package is the published contract.
- **G2.** Server tests run in strict mode.
- **G3.** No extension is registered: the gateway is a UI API client.
- **G4.** Stand-ins are marked `STAND-IN(core=..., removeBy=...)`: the 4.1.0-SNAPSHOT baseline
  until RC.6, and the preferences section until #1162.
- **G5.** No HTML widgets.
- **G7.** Pin 4.1.0-RC.6 as soon as it is cut. Until then, develop builds on the SNAPSHOT.

## 14. Phases
- **(a)** The SFTP server, the virtual filesystem, and auth:
  - keys, accounts, lockout, CIDR, expiry and limits;
  - listing;
  - connection and transfer logs;
  - the tables;
  - the `api` package (types only);
  - the core PR (§3.1–3.3).
- **(b)** Upload to bulk load:
  - streaming to object storage, sha256 and dedupe;
  - the job worker, sidecars, results and archive;
  - notifications;
  - the consumer pipeline and the scan hook.
- **(c)** Downloads of saved views and saved reports, with read-ahead and download limits.
- **(d)** *Removed* (FTPS; see §15).
- **(e)** The rest, then the drafts:
  - endpoints (aliases) and chroot;
  - the outbox;
  - PGP;
  - the Preferences section;
  - the app home;
  - retention;
  - then the kof22 adoption (draft) and the cluster-gitops draft.

## 15. FTPS (future, not in v1)
FTPS is added only if a partner needs it. It would be a second `FileGatewayProtocolServer`, using
Apache FtpServer, over the same virtual tree, UI API client, jobs and audit:
- **TLS.** Explicit (AUTH TLS, port 21) and/or implicit (990), TLS 1.2 or later, and never plain
  FTP.
- **Authentication.** Either or both of:
  - revocable per-account **app passwords**: hashed, shown once, with an expiry;
  - **client certificates**: the certificate's fingerprint pinned on the account.
- **Networking.** A **passive port range** (for example 30000–30049) on the LoadBalancer, with
  `externalTrafficPolicy: Local` and the passive address set to the LB IP.
- **Tests.** Protocol tests with the Commons Net `FTPSClient`.

## 16. Owner steps
- The LoadBalancer IP is 10.100.22.65 (decided 2026-10-13); reserve it in the cluster-gitops PR.
- Create the host key secret (`FILE_GATEWAY_SSH_HOST_KEY`) in Infisical.
- Add the UniFi port forward.
- Grant `file_gateway.connect` to the roles that may use SFTP, and `file_gateway.admin` to admins.
- Merge the qbit-authentik automated-session PR (§3.4) before go-live.
