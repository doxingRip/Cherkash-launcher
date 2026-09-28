# Build

## Requirements

- Windows 10/11 x64
- JDK 21
- Maven 3.9+

## Local build

```powershell
mvn -DskipTests clean package
```

## Windows EXE

```powershell
./packaging/windows/CherkashLauncher.ps1
```

The script uses JDK `jpackage` and produces a Windows application under `target/windows`.

## Offline profiles

Offline profiles use a local Minecraft nickname and a deterministic UUID. They do not authenticate against Microsoft and are not equivalent to an authenticated Minecraft account. Offline mode is intended for local/offline-compatible servers.

The launcher must not claim that an offline profile owns an authenticated account.
