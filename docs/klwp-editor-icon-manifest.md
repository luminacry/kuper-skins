# KLWP Editor Icon Manifest (M3)

This document maps extracted design icons to Android drawable resources used by the editor UI.

## Source

- Design boards: `KLWP Dev Spec / Icon Samples` and `KLWP Dev Spec / Icon Manifest`
- Extraction baseline: 44 family-qualified glyphs, 43 unique names

## Core Drawable Mapping

| Usage | Drawable | Family | Size |
| --- | --- | --- | --- |
| Status network | `@drawable/ic_editor_network_cell` | Material Symbols Rounded | 18dp |
| Status wifi | `@drawable/ic_editor_wifi` | Material Symbols Rounded | 18dp |
| Status battery | `@drawable/ic_editor_battery_5_bar` | Material Symbols Rounded | 18dp |
| Top bar menu | `@drawable/ic_editor_menu` | Lucide | 20dp |
| Save | `@drawable/ic_editor_save` | Lucide | 18dp |
| History | `@drawable/ic_editor_history` | Lucide | 18dp |
| Add object | `@drawable/ic_editor_plus` | Lucide | 18dp |
| Tool: fit stage | `@drawable/ic_editor_maximize_2` | Lucide | 18dp |
| Tool: lock | `@drawable/ic_editor_lock` | Lucide | 18dp |
| Tool: layers | `@drawable/ic_editor_layers` | Lucide | 18dp |
| Tool: properties | `@drawable/ic_editor_sliders_horizontal` | Lucide | 18dp |
| Layer row grip | `@drawable/ic_editor_grip` | Lucide | 16dp |
| Layer visible | `@drawable/ic_editor_eye` | Lucide | 16-18dp |
| Layer hidden | `@drawable/ic_editor_eye_off` | Lucide | 16-18dp |
| Property panel close | `@drawable/ic_editor_close` | Lucide | 14-18dp |

## Color and State Rules

- Default icon tint: `@color/editor_text_primary` in top controls.
- Secondary icon tint: `@color/editor_text_secondary` in list rows and passive indicators.
- Primary action icon (plus): force white tint when used on `@drawable/bg_editor_icon_button_primary`.
- Keep icon sizes in the extracted ladder: 14 / 16 / 18 / 20 / 22.

## Naming Rules

- Use `ic_editor_*` for all editor icon resources.
- Keep one drawable per semantic icon token.
- Avoid Android built-in icon drawables for editor UI consistency.

## Cleanup Notes

- Removed deprecated aliases: `ic_editor_expand.xml`, `ic_editor_sliders.xml`.
- Canonical replacements:
  - `ic_editor_maximize_2.xml` for expand/fit-stage action.
  - `ic_editor_sliders_horizontal.xml` for adjust/properties action.
