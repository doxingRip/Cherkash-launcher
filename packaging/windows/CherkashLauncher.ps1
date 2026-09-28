param(
    [string]$Version = "1.0.0"
)

$ErrorActionPreference = "Stop"

mvn -DskipTests clean package

$jar = Get-ChildItem "target\*.jar" | Where-Object { $_.Name -notmatch "sources|original" } | Select-Object -First 1
if (-not $jar) { throw "Launcher JAR was not produced" }

$inputDir = "target\app-input"
$outDir = "target\windows"
Remove-Item $inputDir -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $outDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item $inputDir -ItemType Directory -Force | Out-Null
New-Item $outDir -ItemType Directory -Force | Out-Null

# jpackage does not automatically include Maven dependencies. Copy every runtime
# dependency next to the launcher JAR so JavaFX and Gson are available at runtime.
mvn -DskipTests dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=$inputDir
Copy-Item $jar.FullName "$inputDir\CherkashLauncher.jar"

# Use classpath packaging rather than module-path packaging. This makes the
# generated Windows application self-contained and lets JavaFX load its native
# Windows libraries from the copied JavaFX artifacts.
jpackage `
  --type exe `
  --name CherkashLauncher `
  --app-version $Version `
  --input $inputDir `
  --main-jar CherkashLauncher.jar `
  --main-class com.nullpoint.launcher.LauncherApp `
  --dest $outDir `
  --win-menu `
  --win-shortcut `
  --win-dir-chooser `
  --win-per-user-install `
  --vendor "Cherkash" `
  --description "Cherkash Minecraft Launcher" `
  --win-console

$exe = Get-ChildItem "$outDir\*.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $exe) { throw "jpackage completed but no EXE was produced" }
Write-Host "Windows installer created: $($exe.FullName)"
