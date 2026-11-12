# MedAtlas Books Pack — local personal books (never upload to a public repo)
# Run in PowerShell 5.1+:
#   powershell -ExecutionPolicy Bypass -File .\prepare_medatlas_books.ps1
param(
  [string]$Source = "$env:USERPROFILE\Downloads\carti",
  [string]$Output = "$env:USERPROFILE\Downloads\MedAtlas-Books.zip"
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Source -PathType Container)) {
  throw "Folder not found: $Source. Supply -Source with your legitimate textbook folder."
}
Add-Type -AssemblyName System.IO.Compression
$files = @(Get-ChildItem -LiteralPath $Source -Recurse -File |
  Where-Object { $_.Extension.ToLowerInvariant() -in @('.pdf','.docx','.pptx') } |
  Sort-Object FullName)
if ($files.Count -eq 0) { throw "No PDF/DOCX/PPTX files found." }
if ($files.Count -gt 300) { throw "Too many files: $($files.Count). Maximum 300 in one package." }
$parent = Split-Path -Parent $Output
if (-not (Test-Path -LiteralPath $parent)) {
  New-Item -ItemType Directory -Path $parent -Force | Out-Null
}
if (Test-Path -LiteralPath $Output) {
  throw "Output already exists: $Output. Choose another filename to avoid overwriting."
}
$stream = [IO.File]::Open($Output, [IO.FileMode]::CreateNew)
$archive = $null
try {
  $archive = New-Object IO.Compression.ZipArchive(
    $stream, [IO.Compression.ZipArchiveMode]::Create, $false)
  $names = @{}
  $index = 0
  foreach ($file in $files) {
    $base = $file.Name
    if ($base.Contains('..')) { continue }
    $stem = [IO.Path]::GetFileNameWithoutExtension($base)
    $ext = $file.Extension
    $candidate = $base
    while ($names.ContainsKey($candidate.ToLowerInvariant())) {
      $candidate = $stem + "_" + $index + $ext
      $index++
    }
    $names[$candidate.ToLowerInvariant()] = $true
    $entry = $archive.CreateEntry(
      $candidate, [IO.Compression.CompressionLevel]::Fastest)
    $inputStream = [IO.File]::OpenRead($file.FullName)
    $outputStream = $entry.Open()
    try { $inputStream.CopyTo($outputStream) }
    finally { $outputStream.Dispose(); $inputStream.Dispose() }
    Write-Host ("Added: " + $candidate)
  }
}
finally {
  if ($archive -ne $null) { $archive.Dispose() } else { $stream.Dispose() }
}
$pack = Get-Item -LiteralPath $Output
Write-Host ("Pack contains " + $names.Count + " books.")
Write-Host ("File: " + $pack.FullName)
Write-Host ("Size: " + [math]::Round($pack.Length / 1GB, 2) + " GiB")
Write-Host "Transfer this ZIP directly to the tablet. MedAtlas -> Study -> Medical Books -> Import ZIP."
Write-Host "Never commit copyrighted personal ebooks or PDFs to a public GitHub repository."
