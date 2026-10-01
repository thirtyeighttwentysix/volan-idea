#requires -Version 7.0
param([Parameter(Mandatory = $true)][string]$OrmPath)
$ErrorActionPreference = 'Stop'
$pluginRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$ormRoot = (Resolve-Path -LiteralPath $OrmPath).Path
$vendorRoot = [IO.Path]::GetFullPath((Join-Path $pluginRoot 'vendor'))
$modules = @('core', 'schema', 'ir')
$inputs = @{}
foreach ($module in $modules) {
    $source = Join-Path $ormRoot "volan-$module\src\main\kotlin"
    if (-not (Test-Path -LiteralPath $source -PathType Container)) { throw "Missing source: $source" }
    $inputs[$module] = (Resolve-Path -LiteralPath $source).Path
}
$revision = & git -C $ormRoot rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Cannot determine ORM revision.' }
$dirty = & git -C $ormRoot status --porcelain -- volan-core/src/main volan-schema/src/main volan-ir/src/main
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect ORM source state.' }
if ($dirty) { throw 'Commit ORM language-engine changes before taking a reproducible snapshot.' }

foreach ($module in $modules) {
    $targetRoot = [IO.Path]::GetFullPath((Join-Path $vendorRoot $module))
    if (-not $targetRoot.StartsWith($vendorRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Target escapes vendor directory: $targetRoot"
    }
    New-Item -ItemType Directory -Force -Path $targetRoot | Out-Null
    $expected = @{}
    foreach ($file in Get-ChildItem -LiteralPath $inputs[$module] -Recurse -File) {
        $relative = [IO.Path]::GetRelativePath($inputs[$module], $file.FullName)
        $destination = [IO.Path]::GetFullPath((Join-Path $targetRoot $relative))
        if (-not $destination.StartsWith($targetRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Destination escapes source snapshot: $destination"
        }
        New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($destination)) | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $destination -Force
        $expected[$destination] = $true
    }
    foreach ($file in Get-ChildItem -LiteralPath $targetRoot -Recurse -File) {
        if (-not $expected.ContainsKey($file.FullName)) { Remove-Item -LiteralPath $file.FullName }
    }
}
$snapshotDate = Get-Date -Format 'yyyy-MM-dd'
$notice = @"
# Volan language engine snapshot

The core, schema and ir directories are unmodified Kotlin sources from
https://github.com/thirtyeighttwentysix/volan, commit
$revision, copied on $snapshotDate.
Licensed under Apache-2.0 (see ../LICENSE).
Update using ../scripts/sync-engine.ps1, then run the plugin tests.
The IDE does not connect to databases or evaluate environment variables.
"@
Set-Content -LiteralPath (Join-Path $vendorRoot 'NOTICE.md') -Value $notice -Encoding utf8
Write-Output "Updated Volan engine snapshot to $revision"
