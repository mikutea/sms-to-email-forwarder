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

- Kept the approved visual tokens and folded-paper goose asset as the single brand source.
- Limited glass treatment to navigation, selected capsules, back actions, tabs, chips, and auxiliary buttons. Content remains high-contrast soft-neumorphic paper.
- Used an icon library rather than approximate code-drawn or text-symbol assets.
- Kept Android-sized touch targets and scrollable long forms as intentional platform adaptations.
- Preserved reduced-motion behavior and removed the most expensive full-screen scale and multi-layer reveal animations.

## Evidence

The `implemented` directory contains the final API 35 emulator captures for all eight design source screens. See the repository-root `design-qa.md` for viewport, state, comparison, accessibility, interaction, and performance results.
