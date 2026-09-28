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
New-Item $inputDir -ItemType Directory | Out-Null
Copy-Item $jar.FullName "$inputDir\CherkashLauncher.jar"

jpackage `
  --type app-image `
  --name CherkashLauncher `
  --app-version $Version `
  --input $inputDir `
  --main-jar CherkashLauncher.jar `
  --main-class com.nullpoint.launcher.LauncherApp `
  --dest $outDir `
  --win-console

Write-Host "Standalone application created in $outDir\CherkashLauncher"
Write-Host "For an installer use --type exe instead of --type app-image."
