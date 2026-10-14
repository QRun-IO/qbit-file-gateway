# QBit File Gateway

An SFTP server for QQQ applications. Users and service accounts sign in with their SSH keys and see
the application as a folder tree of apps and tables. Uploading a file into a table's
`upload/<profile>/` folder runs a saved bulk-load profile as that user; getting a file from
`views/` or `reports/` exports a saved view or runs a saved report. The gateway reaches data only
through the `/qqq/v1` UI API with the signed-in user's own session (client channel `sftp`), so
permissions, masking and audits apply exactly as in the UI. Design: [docs/DESIGN.md](docs/DESIGN.md).

This repository currently holds the scaffold; the gateway is built in phases described in the design.

## Requirements

Java 21 and QQQ 4.1.0-SNAPSHOT (UI API invoker and client channels, until 4.1.0-RC.6). The Maven
wrapper pins Maven 3.9.11.

## Install

```xml
<dependency>
   <groupId>com.kingsrook.qbits</groupId>
   <artifactId>qbit-file-gateway</artifactId>
   <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Tables and permissions

Planned tables (none are produced yet), all prefixed `file_gateway_`: accounts, SSH keys, aliases,
upload jobs, the transfer log and the connection log. Permissions use the full `file_gateway`
prefix.

## Configuration

`FileGatewayQBitConfig` holds the optional `tableMetaDataCustomizer`, `sftpEnabled` (only the
gateway deployment runs the server) and `sftpPort` (default 2222).

## Adopting in another app

```java
new FileGatewayQBitProducer()
   .withQBitConfig(new FileGatewayQBitConfig())
   .produce(qInstance);
```

## Build

```bash
./mvnw verify   # Checkstyle; JaCoCo 70% instructions, 90% classes
```

On Windows, use `./mvnw.cmd`.

## License

[Apache License 2.0](LICENSE). Copyright Kingsrook, LLC.
