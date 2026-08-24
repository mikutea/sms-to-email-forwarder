# Design QA

## Scope and evidence

- Visual source of truth: `docs/design/liquid-neumorphism-v2/01-onboarding-background-permission.png` through `08-settings-hub.png`
- Android implementation captures: `docs/design/liquid-neumorphism-v2/implemented/01-onboarding-background-permission-android.png` through `08-settings-hub-android.png`
- Test target: isolated `codex-project` Android API 35 emulator, 1080 × 2400 px at 420 dpi
- State: light theme, empty synthetic local data, no real phone number, SMS body, mailbox credential, or SMTP authorization code
- Comparison method: every source screen and corresponding Android capture were inspected together at the same portrait state in the final QA run

## Audit result

The original beta.2 implementation did not match the approved visual baseline. It exposed an Android action bar above the app, used stock control-heavy layouts, split readiness into an oversized 2 × 2 block, used unrelated system icons, and did not implement the agreed glass navigation, compact grouped rows, or secondary-page hierarchy.

The implementation now uses the approved moon-white, ink, jade, pale-jade, and restrained cinnabar tokens; the folded-paper goose is shared by launcher and page branding; all eight pages use one responsive surface and spacing system; and the fixed navigation follows `记录 / 规则 / 守护 / 设置` with a continuous glass bar and refractive selected capsule.

No P0, P1, or P2 visual mismatch remains in the tested default state.

## Verified fixes

- [fixed P0] Removed the Android 27+ action-bar theme regression and applied system-bar insets on API 35 edge-to-edge windows.
- [fixed P1] Rebuilt 守护、记录、规则、设置 and all four settings subpages against the approved layouts instead of restyling the previous native forms.
- [fixed P1] Replaced unrelated Android system drawables with one consistent Material icon library and reused the approved goose asset for all brand surfaces.
- [fixed P1] Added the continuous glass bottom bar, selected liquid capsule, translucent auxiliary controls, soft-neumorphic grouped cards, and jade gradient primary actions.
- [fixed P2] Added functional history filters, expandable rule groups, main/backup channel switching, background-authorization steps, status progress, and real navigation ownership for settings subpages.
- [fixed P2] Added API 35 status/navigation insets, 48 dp-class interactive targets, meaningful accessibility descriptions, 130% font-scale coverage, and reduced-motion fallback.
- [fixed P2] Simplified page animation from simultaneous full-screen scale and child reveals to a single 300 ms fade/translation, retaining 140 ms press and 220 ms selected-state motion.

## Interaction and accessibility checks

- Primary navigation, settings rows, subpage return, history filters, SMTP channel tabs, rule expand/collapse, guardian actions, and onboarding actions were exercised on the emulator.
- Animations-off mode was tested with all three Android animation scales set to zero; navigation remained functional and the process stayed alive.
- A 130% system font scale was tested on the guardian page; essential labels, state cells, primary action, and fixed navigation remained readable and operable.
- Android Lint and JVM unit tests pass.
- The emulator uses SwiftShader software rendering; a repeated eight-route navigation sample reported 10.49% modern janky frames after removing the costly full-screen scale and multi-layer reveal. This is retained as a conservative virtual-device measurement, not a physical-device performance claim.

## Intentional native adaptations

- System status and gesture bars remain visible and use the moon-white surface; the design images omit those operating-system regions.
- Input rows keep accessible Android touch heights, so long SMTP and rule forms scroll more than the compact visual mock.
- Glass uses a high-opacity translucent gradient, white refraction edge, and shadow fallback instead of full-screen live blur. This preserves text contrast and avoids unstable background blur on low-power or reduced-transparency devices.
- Empty history shows a designed empty state rather than fabricated SMS records.

final result: passed
