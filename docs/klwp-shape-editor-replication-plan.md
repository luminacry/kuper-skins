# KLWP-Style Shape Add/Edit Replication Plan

## Objective

Build an editor workflow that supports:

- Add shape layers quickly from UI.
- Select and edit shape bounds/transform in-canvas.
- Edit shape style (fill, stroke, radius, opacity).
- Keep runtime preview and wallpaper runtime in sync.

## Current Baseline in This Project

- Document model already supports `ShapeLayerDocument` with `RECTANGLE` and `CIRCLE`.
- Runtime pipeline exists:
  - `DemoWallpaperDocumentFactory` -> `DocumentToRuntimeSceneCompiler` -> `RuntimeSceneRenderer`.
- Editor screen exists with:
  - Layer list, visibility toggles, transform sliders (x/y/opacity).
  - Runtime preview (`EditorRuntimePreviewView`) plus transform overrides.

This is enough to implement a real shape editor incrementally.

## Scope Cut

### MVP (recommended first delivery)

- Add shape: rectangle + circle.
- Edit selected shape:
  - position (drag),
  - size (corner handles),
  - fill color,
  - stroke color + width,
  - corner radius (rectangle only),
  - visibility and delete.
- Undo/redo for all shape operations.

### V1 (after MVP)

- Add line and rounded capsule preset.
- Align tools (center, distribute horizontally/vertically).
- Snap to grid and guidelines.

### V2

- Path-based freeform shapes.
- Boolean operations (union/subtract/intersect).
- Group edit and multi-select transform.

## Technical Design

## 1) Data Model Extensions

Update `WallpaperDocument` family to support editable metadata:

- Extend `ShapeKind` with future-safe values:
  - `ROUNDED_RECT`, `ELLIPSE`, `LINE`, `PATH` (MVP uses first two only).
- Expand `ShapeStyleDocument`:
  - `strokeCap`, `strokeJoin`, `dashPattern`, `blendMode` (optional defaults).
- Add optional editor-only metadata:
  - `locked`, `zIndex`, `name`, `isGuide`.

Keep runtime-safe defaults so old documents still render.

## 2) Editor State + Command Stack

Add new module (example package: `com.example.klwpdemo.editor`):

- `EditorSessionState`
  - selected layer id, tool mode, current zoom/pan, snapping flags.
- `EditCommand` interface
  - `apply(document)`, `revert(document)`.
- Command implementations:
  - `AddShapeCommand`
  - `DeleteLayerCommand`
  - `UpdateShapeBoundsCommand`
  - `UpdateShapeStyleCommand`
  - `ReorderLayerCommand`

Use immutable `WallpaperDocument` snapshots plus command history:

- `undoStack`, `redoStack`
- max history size (for example 100).

## 3) Runtime Integration

`DemoWallpaperRuntime` currently rebuilds from factory each frame.
To support persistent edits:

- Introduce `DocumentRepository` (single source of truth document).
- Runtime should render repository document + transient interaction state.
- `DemoWallpaperDocumentFactory` becomes starter template only.

This prevents edited shapes from being overwritten on each frame.

## 4) Canvas Interaction Layer

Upgrade `EditorRuntimePreviewView`:

- Hit test scene nodes (shape bounds first, then z-order).
- Show selection overlay:
  - bounding box,
  - 8 resize handles,
  - rotation handle (optional in MVP).
- Gesture mapping:
  - drag inside bounds -> move,
  - drag handle -> resize,
  - two-finger (V1) -> scale/rotate.

Keep touch interaction in preview view; persist result through commands.

## 5) UI Flow (Material 3)

Use existing M3 style:

- FAB `+` opens bottom sheet:
  - Rectangle, Circle (MVP),
  - future items disabled with "coming soon".
- Property panel tabs:
  - `Transform`, `Style`, `Arrange`.
- Layer row actions:
  - visibility toggle,
  - lock,
  - duplicate,
  - delete.

## 6) Persistence and Export

Add JSON serialization for `WallpaperDocument`:

- Save draft in app storage.
- Load last session on launch.
- Export/import JSON for preset sharing.

## Delivery Plan

## Phase 1 (2-3 days)

- Introduce `DocumentRepository`.
- Keep current rendering behavior but read from repository.
- Add basic add/delete shape commands.

## Phase 2 (3-4 days)

- Implement selection + drag/resize overlay.
- Wire transform + style panel to command stack.
- Add undo/redo UI actions.

## Phase 3 (2-3 days)

- Add JSON save/load.
- Add validation and migration for older documents.
- Polish snapping and edge cases.

## Acceptance Criteria

- Add rectangle/circle from `+` in under 2 taps.
- Any change is undoable/redoable.
- No frame drops below 55 FPS on preview during drag.
- Save/reload preserves layer order, styles, transforms.
- Runtime preview and exported wallpaper show same visual output.

## Risks and Mitigations

- Runtime-generated document conflicts with edits:
  - Mitigation: repository-based document authority.
- Touch gesture conflicts with wallpaper offset behavior:
  - Mitigation: explicit editor mode switch and gesture routing.
- State complexity from layered updates:
  - Mitigation: command pattern + immutable snapshots.
