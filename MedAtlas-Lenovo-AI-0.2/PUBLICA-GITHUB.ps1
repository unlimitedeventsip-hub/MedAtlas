# Ruleaza acest fisier in PowerShell din directorul proiectului. Publica sursele PRIVATE, apoi Actions compileaza APK-ul.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$repoName = 'MedAtlas-Nadia'
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Instaleaza Git: https://git-scm.com/download/win' }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'Instaleaza GitHub CLI: winget install --id GitHub.cli. Apoi reporneste PowerShell.' }
$null = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) { gh auth login --web }
if (-not (Test-Path .git)) { git init -b main }
git add .
$changed = git status --porcelain
if ($changed) {
  git -c user.name='MedAtlas' -c user.email='medatlas@users.noreply.github.com' commit -m 'MedAtlas 0.2 - app and secure AI backend'
}
$remote = git remote get-url origin 2>$null
if (-not $remote) {
    gh repo create $repoName --private --source . --remote origin --push
} else {
    git push -u origin main
}
if ($LASTEXITCODE -ne 0) { throw 'Publicarea a esuat; verifica autentificarea GitHub.' }
Write-Host ''
Write-Host 'Sursele sunt publicate. GitHub Actions construieste APK-ul automat.' -ForegroundColor Green
Write-Host 'Vezi Actions in repository-ul MedAtlas-Nadia.'
$repo = gh repo view --json nameWithOwner -q .nameWithOwner
Write-Host "https://github.com/$repo/actions"
