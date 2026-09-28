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
Copy-Item $jar.FullName "$inputDir\CherkashLauncher.jar"

# app-image is useful for diagnostics, but the user-facing artifact is a real Windows installer.
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

if (-not (Get-ChildItem "$outDir\*.exe" -ErrorAction SilentlyContinue)) {
    throw "jpackage completed but no EXE was produced"
}

Write-Host "Windows installer created in $outDir"
