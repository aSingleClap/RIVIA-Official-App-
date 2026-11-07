# UI Pass: Color Scheme Only — Implementation Plan

This pass updates **colors only** across the entire application to implement the true-black neutral hierarchy, the new Rivia white (`#F5F5F5`) mode accent, strict accent-usage discipline, standardized switch colors, and WCAG AA (4.5:1) text contrast compliance, without changing layout, navigation, components, strings, behavior, or any data/request code.

---

## 1. Color Token Architecture (`Color.kt` & `Theme.kt`)

### Neutral Palette (All Modes)
| Role | Token Name | Legacy Alias (Preserved) | Hex Value |
| :--- | :--- | :--- | :--- |
| **Background** | `RiviaBackground` | `RiviaBackground` | `#000000` (true black) |
| **Surface 1** (drawer, sheets, dialogs) | `RiviaSurface1` | `RiviaSurface` | `#0E0E0E` |
| **Surface 2** (cards, inputs, secondary buttons, chips) | `RiviaSurface2` | `RiviaRaisedSurface` | `#161616` |
| **Surface 3** (my message bubbles, selected rows) | `RiviaSurface3` | — | `#1C1C1C` |
| **Borders & Dividers** | `RiviaBorder` | `RiviaBorder` | `#262626` |
| **Text Primary** | `RiviaTextPrimary` | `RiviaText` | `#EDEDED` |
| **Text Secondary** | `RiviaTextSecondary` | `RiviaMutedText` | `#A3A3A3` |
| **Text Tertiary** (timestamps, hints, placeholders) | `RiviaTextTertiary` | — | `#8F8F8F` |
| **Icon Default** | `RiviaIconDefault` | — | `#A3A3A3` |
| **Icon Active** | `RiviaIconActive` | — | `#EDEDED` |

### Mode Accents & Status Colors
| Role | Token Name | Hex Value | Notes |
| :--- | :--- | :--- | :--- |
| **Rivia Mode Accent** | `RiviaModeWhite` (aliased as `RiviaModeMint`) | `#F5F5F5` | Replaces `#5DCAA5` everywhere; uses `#000000` (`RiviaOnAccent`) for text/icons on top |
| **Dev Mode Accent** | `RiviaModeViolet` | `#A78BFA` | Unchanged |
| **Labs Mode Accent** | `RiviaModeBlue` | `#60A5FA` | Unchanged |
| **On-Accent Content** | `RiviaOnAccent` | `#000000` | Text and icons drawn on top of mode accent fills |
| **Reactive Mode Accent** | `RiviaAccent` (aliased as `RiviaMint`) | Reactive (`#F5F5F5` / `#A78BFA` / `#60A5FA`) | Updated by `RiviaTheme(appUiMode)` |
| **Error Status** | `RiviaErrorRed` | `#FF6B6B` | Fixed in all modes |
| **Warning Status** | `RiviaWarningAmber` | `#F2C14E` | Fixed in all modes |

---

## 2. Strict Accent & Surface Mapping Rules

### Where the Mode Accent Is Allowed (All Modes)
1. **The one primary action on a screen**:
   - `ChatScreen` drawer: **New chat** button (`new_chat_button`) — container = mode accent, content = `#000000`.
   - `SettingsScreen` (Voice tab): **Test Voice (Speak one line)** button (`test_voice_speak_button`) — container = mode accent, content = `#000000`.
   - `TestsScreen`: **Run All 7 Checks** button (`run_all_step_tests_button`) — container = mode accent, content = `#000000`.
   - `ComingSoonScreen`: **Back to Chat** button (`coming_soon_return_button`) — container = mode accent, content = `#000000`.
   - `RunSettingsScreen`: **Save as default** primary button — container = mode accent, content = `#000000`.
2. **Selected or On states**:
   - **Switches On**: track = mode accent, thumb = `#000000` (Off: track = `#262626`, thumb = `#8F8F8F`).
   - **Selected tab indicator**: `TabRowDefaults.SecondaryIndicator(color = RiviaMint)` in `SettingsScreen` and `LayersScreen`.
   - **Selected chip**: `OptionChip` / `FilterChip` when `selected == true` uses container = mode accent, text/icon = `#000000`; when unselected uses `RiviaSurface2` (`#161616`) and `RiviaTextPrimary` (`#EDEDED`).
   - **Focused input border**: `focusedBorderColor = RiviaMint` on `OutlinedTextField`s (`riviaTextFieldColors()` and drawer search).
3. **The Rivia Orb and Live Edge Glow**:
   - `RiviaOrb` in `RiviaComponents.kt` and the speaking edge glow in `LiveModeScreen.kt` use the mode accent.

### Where Neutrals Replace Previous Accent Usage
- **Surfaces**:
  - Screen backgrounds: `#000000` (`RiviaBackground`).
  - Navigation drawer, bottom sheets (`DevLabsModeSheet`, `SettingHelpBottomSheet`), and `AlertDialog`s: **Surface 1 (`#0E0E0E`)**.
  - Cards (`SettingsCardSection`, layer cards, test cards, log cards), text inputs, secondary buttons, and unselected chips: **Surface 2 (`#161616`)**.
  - User message bubbles (`UserMessageBubble`) and selected drawer chat rows: **Surface 3 (`#1C1C1C`)** with `#EDEDED` text (replacing the old `RiviaMint.copy(alpha = 0.14f)` fill).
- **Section and Card Titles**:
  - `SettingsCardSection` title, `TestsScreen` section headers, `SettingsHelpSheet` topic titles, `LayersScreen` card headers, and `RunSettingsScreen` headers use **Text Primary (`#EDEDED`)** instead of `RiviaMint`.
- **Secondary Buttons**:
  - All secondary buttons (`Export JSON`, `Import JSON`, `Load Voices`, `Test (List Models)`, `Test (List Voices)`, `Lab & Logs`, `Play WAV`, step-test secondary buttons, drawer navigation buttons `Pipeline`, `Layers`, `Settings`, `Tests`, `Exit to Rivia`) use **Surface 2 (`#161616`) fill**, **Text Primary (`#EDEDED`)**, default/active neutral icons (`#A3A3A3` / `#EDEDED`), and **no accent outline**.
- **Message Composer Bar Buttons (`ChatComposerBar`)**:
  - The **Mic** (`dictate_mic_button`), **Send** (`send_message_button`), and **Live** (`open_live_mode_button`) buttons use **Surface 2 (`#161616`) fill** and **Text Primary (`#EDEDED`) icons** (`#A3A3A3` when Send is disabled; `#EDEDED` when active).
- **Info Icons**:
  - `InfoHelpIconButton` (`SettingsHelpSheet.kt`), reply details `Info` icon, and top-bar Chat info icon use **Text Tertiary (`#8F8F8F`)**.
- **Default vs. Active Icons**:
  - Icons default to `#A3A3A3` (`RiviaIconDefault`) and switch to `#EDEDED` (`RiviaIconActive`) when active (e.g., auto read-aloud enabled, active dictation mic, active speaker icon).

---

## 3. Switches (`riviaSwitchColors()`)
- **On (`checked`)**:
  - `checkedTrackColor = RiviaMint` (mode accent: `#F5F5F5` in Rivia, `#A78BFA` in Dev, `#60A5FA` in Labs)
  - `checkedThumbColor = Color(0xFF000000)`
- **Off (`unchecked`)**:
  - `uncheckedTrackColor = Color(0xFF262626)` (`RiviaBorder`)
  - `uncheckedThumbColor = Color(0xFF8F8F8F)` (`RiviaTextTertiary`)
  - `uncheckedBorderColor = Color.Transparent`

---

## 4. WCAG AA (4.5:1) Contrast Audit & Fixes

### Contrast Ratios of Specified Palette Pairs (All Pass >= 4.5:1)
| Foreground Text | Surface | Contrast Ratio | Status |
| :--- | :--- | :--- | :--- |
| `#EDEDED` (Text Primary) | `#000000` / `#0E0E0E` / `#161616` / `#1C1C1C` | **17.94:1** / **16.49:1** / **15.47:1** / **14.57:1** | PASS |
| `#A3A3A3` (Text Secondary) | `#000000` / `#0E0E0E` / `#161616` / `#1C1C1C` | **8.33:1** / **7.65:1** / **7.18:1** / **6.76:1** | PASS |
| `#8F8F8F` (Text Tertiary) | `#000000` / `#0E0E0E` / `#161616` / `#1C1C1C` | **6.49:1** / **5.97:1** / **5.60:1** / **5.28:1** | PASS |
| `#000000` (On-Accent Text) | `#F5F5F5` (Rivia) / `#A78BFA` (Dev) / `#60A5FA` (Labs) | **19.26:1** / **7.72:1** / **8.26:1** | PASS |
| `#FF6B6B` (Error Red) | `#000000` / `#0E0E0E` / `#161616` / `#1C1C1C` | **7.57:1** / **6.96:1** / **6.52:1** / **6.15:1** | PASS |
| `#F2C14E` (Warning Amber) | `#000000` / `#0E0E0E` / `#161616` / `#1C1C1C` | **12.51:1** / **11.50:1** / **10.79:1** / **10.17:1** | PASS |

### Existing Pairs That Would Fail (< 4.5:1) & How We Fix Them
1. **Alpha-dimmed tertiary/muted text (`RiviaMutedText.copy(alpha = 0.70f..0.75f)`) on Surface 1 (`#0E0E0E`) and Surface 2 (`#161616`)**:
   - *Where*: `"Dev & Labs"` drawer label (`ChatScreen.kt:1115`), assistant reply `requestId` label (`ChatScreen.kt:2207`), and disabled button labels.
   - *Why it fails*: Multiplying `#8F8F8F` or `#A3A3A3` by `0.70f` alpha over `#161616` lowers the effective luminance to `~#696969` (`3.29:1` against `#161616`, failing WCAG AA 4.5:1).
   - *Fix*: Remove `.copy(alpha = ...)` from text colors and use opaque `RiviaTextTertiary` (`#8F8F8F`, **5.60:1** on `#161616` and **5.97:1** on `#0E0E0E`).
2. **Primary button / selected chip text on Rivia White (`#F5F5F5`) when default M3 `onPrimary` is not `#000000`**:
   - *Where*: `LayersScreen.kt` primary `Button`s and selected `FilterChip`s that relied on `MaterialTheme.colorScheme.onPrimary` or `RiviaText`.
   - *Why it fails*: `#EDEDED` on `#F5F5F5` is **1.07:1**.
   - *Fix*: Set `onPrimary = Color(0xFF000000)` and `onSecondary = Color(0xFF000000)` in `Theme.kt` (`RiviaOnAccent`), and explicitly use `contentColor = RiviaOnAccent` (`#000000`) on all primary accent buttons and selected chips (**19.26:1**).
3. **Unselected `Tab` text in `LayersScreen.kt` (`SecondaryTabRow`)**:
   - *Where*: `LayersScreen.kt` tabs for `Edit` / `View file` did not specify unselected text color, allowing M3 default alpha dimming.
   - *Fix*: Explicitly color selected tab text `#EDEDED` (`RiviaTextPrimary`), unselected tab text `#A3A3A3` (`RiviaTextSecondary`, **7.18:1** on `#161616`), and the selected tab indicator with the mode accent (`RiviaMint`).

---

## 5. Exact Files to Touch

1. `app/src/main/java/com/example/ui/theme/Color.kt`
   - Define `RiviaBackground = Color(0xFF000000)`, `RiviaSurface1` / `RiviaSurface = Color(0xFF0E0E0E)`, `RiviaSurface2` / `RiviaRaisedSurface = Color(0xFF161616)`, `RiviaSurface3 = Color(0xFF1C1C1C)`, `RiviaBorder = Color(0xFF262626)`.
   - Define `RiviaTextPrimary` / `RiviaText = Color(0xFFEDEDED)`, `RiviaTextSecondary` / `RiviaMutedText = Color(0xFFA3A3A3)`, `RiviaTextTertiary = Color(0xFF8F8F8F)`, `RiviaIconDefault = Color(0xFFA3A3A3)`, `RiviaIconActive = Color(0xFFEDEDED)`.
   - Define `RiviaModeWhite = Color(0xFFF5F5F5)` (aliased as `RiviaModeMint`), `RiviaModeViolet = Color(0xFFA78BFA)`, `RiviaModeBlue = Color(0xFF60A5FA)`, `RiviaOnAccent = Color(0xFF000000)`.
   - Keep `RiviaErrorRed = Color(0xFFFF6B6B)` and `RiviaWarningAmber = Color(0xFFF2C14E)`.
2. `app/src/main/java/com/example/ui/theme/Theme.kt`
   - Map Material 3 `darkColorScheme` tokens to the new true-black neutral palette and `onPrimary = RiviaOnAccent` (`#000000`).
3. `app/src/main/java/com/example/ui/screens/ChatScreen.kt`
   - Update drawer, sheets, and dialogs to Surface 1 (`#0E0E0E`); cards, inputs, and secondary buttons to Surface 2 (`#161616`) with no accent outlines; user message bubbles and selected chat rows to Surface 3 (`#1C1C1C`); composer bar Mic, Send, and Live buttons to Surface 2 (`#161616`) with `#EDEDED` icons; default icons to `#A3A3A3` and active icons to `#EDEDED`; timestamps/placeholders/hints/info icons to `#8F8F8F`.
4. `app/src/main/java/com/example/ui/screens/SettingsScreen.kt`
   - Use mode accent only on the selected tab indicator (`TabRowDefaults.SecondaryIndicator`), switches (on state), selected `OptionChip`s, focused input borders, and the primary `Test Voice` button.
   - Change card titles to `#EDEDED`, secondary buttons (`Export JSON`, `Import JSON`, `Load Voices`, `Test`, etc.) to Surface 2 (`#161616`) fill with `#EDEDED` text and no accent border, and timestamps/placeholders to `#8F8F8F`.
5. `app/src/main/java/com/example/ui/screens/RunSettingsScreen.kt`
   - Update `SettingsCardSection` title to `RiviaTextPrimary` (`#EDEDED`), `OptionChip` to mode accent when selected (`#000000` text) and Surface 2 (`#161616`) when unselected (`#EDEDED` text), and `riviaSwitchColors()` to On (`track = RiviaMint`, `thumb = #000000`) and Off (`track = #262626`, `thumb = #8F8F8F`).
   - Update secondary buttons in `RunSettingsScreen` to Surface 2 (`#161616`) fill with `#EDEDED` text and no accent border.
6. `app/src/main/java/com/example/ui/screens/LayersScreen.kt`
   - Update card headers/titles to `#EDEDED`, secondary buttons to Surface 2 (`#161616`) fill without accent borders, `SecondaryTabRow` selected indicator to mode accent, switches to `riviaSwitchColors()`, and selected chips to mode accent with `#000000` text.
7. `app/src/main/java/com/example/ui/screens/TestsScreen.kt`
   - Keep `Run All 7 Checks` as the primary accent button (`#000000` text); convert card titles, section headers, and secondary buttons (`Lab & Logs`, `Play WAV`, card secondary buttons) to neutral Surface 2 (`#161616`) and `#EDEDED` / `#A3A3A3` / `#8F8F8F` text.
8. `app/src/main/java/com/example/ui/screens/LiveModeScreen.kt`
   - Keep the Rivia Orb and Live speaking edge glow in mode accent; style caption cards and controls with the neutral palette (`#0E0E0E`, `#161616`, `#262626`, `#EDEDED`, `#A3A3A3`, `#8F8F8F`) while preserving `#FF6B6B` and `#F2C14E` status colors.
9. `app/src/main/java/com/example/ui/screens/ComingSoonScreen.kt`
   - Keep `Back to Chat` as the primary accent button (`#000000` text); remove accent borders/fills from the card, icon box, and badges in favor of Surface 1/2 (`#0E0E0E` / `#161616`), `#262626` borders, and neutral text/icons.
10. `app/src/main/java/com/example/ui/components/SettingsHelpSheet.kt`
    - Update `InfoHelpIconButton` tint to `RiviaTextTertiary` (`#8F8F8F`), `GuideHeaderButton` to Surface 2 (`#161616`) with `#EDEDED` text and `#A3A3A3` icon, and `SettingHelpBottomSheet` container to Surface 1 (`#0E0E0E`) with Surface 2 (`#161616`) cards and `#EDEDED` titles.
11. `app/src/main/java/com/example/ui/components/MarkdownRenderer.kt`
    - Replace `RiviaMint` on bullets, numbered list markers, code copy icon, and inline code text with neutral tokens (`#A3A3A3` / `#EDEDED` on `#161616` Surface 2).
12. `app/src/main/java/com/example/ui/components/RiviaComponents.kt`
    - Keep `RiviaOrb` in mode accent (`RiviaMint`) with `#EDEDED` core, and keep `StatusShape` error (`#FF6B6B`) and warning (`#F2C14E`) fixed across all modes.
13. `app/src/test/java/com/example/Pass1LayersTest.kt`
    - Update `statusColors_renderRedAndAmberAcrossAllThreeModeThemes` so Rivia mode's expected accent is `#F5F5F5` (`Color(0xFFF5F5F5)`) while `#FF6B6B` and `#F2C14E` status assertions remain unchanged.
    - Add `modeAccents_renderSwitchPrimaryButtonAndTabIndicatorAcrossAllThreeModes` rendering a `Switch` (checked), a primary `Button`, and a selected tab indicator under `Rivia` (`#F5F5F5`), `Dev` (`#A78BFA`), and `Labs` (`#60A5FA`) and asserting the mode's accent color in each mode.
