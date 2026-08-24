# Design QA

- Source visual truth: `docs/design/liquid-neumorphism-v2/01-onboarding-background-permission.png` through `08-settings-hub.png`
- App icon truth: `docs/design/liquid-neumorphism-v2/00-app-icon.png`
- Implementation screenshot: unavailable; no Android device or emulator is attached to the build host
- Intended CSS-independent viewport: mobile portrait, approximately 390 × 844 dp
- Source pixels: page designs are 852/853 × 1844/1846 px; app icon is 1254 × 1254 px
- Implementation pixels: unavailable
- Density normalization: pending a device screenshot with its reported density and viewport
- State: default light theme; synthetic or empty local data

**Findings**

- [P1] Native rendered comparison is not yet available
  Location: all implemented Android screens.
  Evidence: the source design files are available, but `adb devices -l` reports no attached target and no emulator is installed on the build host.
  Impact: typography wrapping, real device insets, manufacturer font metrics, glass opacity, shadow softness and animation frame pacing cannot be visually certified from compilation alone.
  Fix: install the generated APK on a supported device, capture the eight page states, record display size/density, and compare the source and implementation at normalized dimensions.

**Required fidelity surfaces**

- Fonts and typography: code uses serif display headings and system sans-serif body text; rendered wrapping and optical weight remain unverified.
- Spacing and layout rhythm: dp spacing, 48 dp touch targets and fixed bottom navigation are implemented; device-rendered vertical rhythm remains unverified.
- Colors and visual tokens: moon-white, ink, jade, pale-jade and restrained cinnabar tokens are implemented; panel translucency and contrast remain to be visually sampled.
- Image quality and asset fidelity: the generated app icon is packaged directly and reused in the page header; launcher masking and physical-device sharpness remain unverified.
- Copy and content: all core routes and Chinese labels are implemented; truncation on narrow or enlarged-font devices remains unverified.

**Interaction checks completed without device rendering**

- JVM unit tests pass, including primary/subpage navigation ownership.
- Android Lint passes with no errors; one advisory launcher-shape warning remains for the intentionally unified full-tile icon.
- Debug and Release APK builds pass.
- No browser console applies to this native Android build.

**Implementation checklist**

- Capture 守护、记录、规则、设置、邮箱通道、系统守护、后台授权引导与维护诊断 on a real device.
- Record page transition, bottom-navigation selection, button press and backup-channel expand/collapse animations.
- Compare full views and focused header/navigation/form regions against the source designs.
- Fix all P0/P1/P2 visual or motion differences, then repeat the comparison.

**Follow-up polish**

- Evaluate adaptive launcher masking on at least one Android launcher and one HarmonyOS launcher.
- Check enlarged font, reduced animation and low-power states.

final result: blocked
