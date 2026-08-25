# Android UI implementation audit

## Goal

Bring the native Android client back to the approved `月白青玉` visual baseline without changing the SMS-forwarding, SMTP, rules, queue, or background-guardian behavior.

## Baseline findings

1. The API 27 override of `AppTheme` lost its `NoActionBar` parent. On API 35 the system action bar covered the custom brand header with a dark block.
2. The shell rendered one generic brand card above every page, while the approved design gives each root page a purpose-built branded header and gives settings subpages a glass back action.
3. Stock Android button and drawable choices produced visually unrelated icons, oversized cards, and a flat navigation pill.
4. The guardian readiness state was arranged as two text rows instead of one icon-led four-column status surface.
5. Email and rule pages exposed long native form stacks without channel tabs, compact connection parameters, or collapsible conditions.
6. History had no filter interaction or designed empty state. Background authorization lacked the four-step progress and visual settings path.
7. Page entry combined full-screen scaling with multiple child reveals, which increased software-renderer frame cost.

## Implementation decisions

- Treated the eight approved images as the sole source of truth for page order, geometry, density, surface balance and state.
- Kept the approved folded-paper goose as the single brand source for the launcher, square brand tile and free-flight page headers.
- Applied the same layered moon-white glass, cyan-jade ambient shadow and raised refractive selection treatment to cards, fields, buttons and navigation.
- Used one outline icon library rather than approximate code-drawn or text-symbol assets.
- Added a debug-only synthetic visual state so every comparison uses the same safe mailbox, rule, history and guardian state without changing release behavior.
- Preserved reduced-motion behavior and avoided real-time blur or layout animation on long forms.

## Evidence

The `implemented` directory contains the final API 35 emulator captures for all eight design source screens. The `comparisons` directory contains the source and implementation on the same normalized canvas. See the repository-root `design-qa.md` for viewport, state, accessibility, interaction, engineering checks and the final verdict.
