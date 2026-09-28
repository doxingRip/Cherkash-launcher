# Cherkash Launcher

## Offline accounts

Offline accounts are local profiles identified by a Minecraft nickname. They are intended for offline-mode/local or compatible servers and do not authenticate with Microsoft/Mojang.

The launcher must never present an offline profile as an authenticated Microsoft account.

## Windows build

Requirements:
- JDK 21
- Maven 3.9+
- Windows 10/11 x64

Build:

```powershell
mvn clean package
```

The resulting application is a Java application. To produce a standalone Windows `.exe`, package the assembled JAR with `jpackage` from JDK 21. A later CI workflow can automate this and attach the installer to GitHub Releases.

The launcher should download only official Minecraft metadata/assets/libraries and verify SHA-1 values supplied by the metadata before using cached files.
