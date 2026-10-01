# Volan logo

`logo-original.png` is the user's original logo, copied without changes.
`logo-mark.svg` is its compact vector rendition for IntelliJ icons. It retains
the shuttlecock's five silhouettes, diagonal orientation and rounded edges.

The resource variants are generated with `scripts/write-icons.ps1`:

- `icons/volan.svg`: 16×16 file and completion icon, black mark.
- `icons/volan_dark.svg`: 16×16 icon, light mark for dark backgrounds.
- `META-INF/pluginIcon.svg`: 40×40 plugin logo.
- `META-INF/pluginIcon_dark.svg`: 40×40 plugin logo for dark themes.

All variants use transparent vector canvases with padding. The original PNG
stays in this project for reference; it is not a runtime dependency.
