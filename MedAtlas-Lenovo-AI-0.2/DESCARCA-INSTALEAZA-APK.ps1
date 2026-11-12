# Cere un build GitHub Actions finalizat cu succes. Descarca APK-ul si il instaleaza cu ADB pe Lenovo.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'Instaleaza GitHub CLI: winget install --id GitHub.cli' }
$repo = gh repo view --json nameWithOwner -q .nameWithOwner
$run = gh run list --repo $repo --workflow android.yml --json databaseId,conclusion -q '[.[] | select(.conclusion=="success")][0].databaseId'
if (-not $run) { throw 'Nu exista inca un build reusit. Deschide GitHub > Actions > Build MedAtlas APK si verifica erorile.' }
$destination = Join-Path $PSScriptRoot 'APK-GitHub'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
gh run download $run --repo $repo --name MedAtlas-Lenovo-debug-apk --dir $destination
if ($LASTEXITCODE -ne 0) { throw 'Nu am putut descarca artefactul.' }
$apk = Join-Path $destination 'app-debug.apk'
if (-not (Test-Path $apk)) { throw 'Nu a fost gasit app-debug.apk in artefactul descarcat.' }
Write-Host "APK descarcat: $apk" -ForegroundColor Green
$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    $tools = Join-Path $env:USERPROFILE 'Downloads\platform-tools-latest-windows'
    $found = Get-ChildItem -Path $tools -Filter adb.exe -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $found) { Write-Host 'Nu gasesc adb.exe. APK-ul este deja descarcat; il poti copia si instala manual pe tableta.'; exit 0 }
    $adbPath = $found.FullName
} else { $adbPath = $adb.Source }
& $adbPath devices
Write-Host 'Asigura-te ca Lenovo este conectata prin USB/Wi-Fi si ca debugging este autorizat.'
& $adbPath install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Instalarea a esuat; verifica autorizarea ADB si versiunea Android.' }
Write-Host 'MedAtlas instalat. Deschide aplicatia pe Lenovo.' -ForegroundColor Green
