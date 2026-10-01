param()
$ErrorActionPreference = 'Stop'
$pluginRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$source = Get-Content -LiteralPath (Join-Path $pluginRoot 'assets/logo-mark.svg') -Raw
$resources = Join-Path $pluginRoot 'src/main/resources'
foreach ($variant in @(
    @{ File = 'icons/volan.svg'; Size = 16; Color = '#000000' },
    @{ File = 'icons/volan_dark.svg'; Size = 16; Color = '#F2F2F2' },
    @{ File = 'META-INF/pluginIcon.svg'; Size = 40; Color = '#000000' },
    @{ File = 'META-INF/pluginIcon_dark.svg'; Size = 40; Color = '#F2F2F2' }
)) {
    $target = Join-Path $resources $variant.File
    New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($target)) | Out-Null
    $svg = $source.Replace('width="40" height="40"', ('width="' + $variant.Size + '" height="' + $variant.Size + '"')).Replace('#000000', $variant.Color)
    [IO.File]::WriteAllText($target, $svg, [Text.UTF8Encoding]::new($false))
}
