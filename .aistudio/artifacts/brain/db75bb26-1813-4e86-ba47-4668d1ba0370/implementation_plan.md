# Rivia — Personal AI Assistant Architecture & Implementation Plan

A native Android personal AI assistant crafted with Jetpack Compose, Material 3, and a dark-theme obsidian and mint aesthetic (`applicationId: observer.rivia.app`, `minSdk: 31`). Features typed chat, markdown rendering, thinking traces, custom ElevenLabs streaming read-aloud, and a hands-free Live voice mode powered by a unified foreground service.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following design, product, and technical specifications are locked in:

- **Original Prompt as Ground Truth Spec**: Every control and behavior from Sections 0 through 8 (including all controls in Section 4 Run Settings and Section 5 Voice) will be implemented in order, with each section verified and its added libraries and manifest permissions listed.
- **Confirmed Decision 1 (First Launch Experience)**: When Gemini or ElevenLabs API keys are missing, Rivia displays a non-intrusive setup banner in the chat header with a 1-tap shortcut to Settings.
- **Confirmed Decision 2 (Tactile Feedback)**: Subtle haptic pulses trigger when entering listening, thinking, and speaking states during hands-free Live voice mode, as well as upon tapping the animated orb to interrupt speech.
- **Security & Zero HTTP Leaks**:
  - Keys are encrypted via an AES-GCM 256 key in the Android Keystore before saving to Jetpack DataStore, excluded from backups, never logged, and redacted in all request previews and logs.
  - **No HTTP logging of headers or bodies**: `logging-interceptor` is completely dropped (or restricted to debug-only `BASIC` level with `redactHeader("x-goog-api-key")` and `redactHeader("xi-api-key")`).
- **Strict Independence from Firebase**: No Firebase, Firebase AI Logic, Google Sign-In, analytics, or ads.

---

## 1. Overview & Core Concept

**Rivia** is an intelligent, companion-grade Android assistant designed for deep focus, rapid reasoning, and fluid voice interactions:

- **Dual-Mode Intelligence**: Typed chat with streaming replies, collapsible thought summaries, and markdown/code blocks alongside an immersive, hands-free Live voice mode.
- **Custom Vocal Persona**: Integrated with ElevenLabs streaming voice synthesis, low-latency sentence-level pipelined playback, speakable markdown transformation, and emotional audio tags (`[softly]`, `[laughs]`) that are hidden on screen while sent to ElevenLabs (when "Send audio tags" is on).
- **Full Transparency & Control**: Complete per-chat and default Run Settings, model filtering, raw step persistence, and a 100-entry request log.

---

## 2. User Experience & Visual Design

### Visual Identity & Theme Tokens
- **Palette**:
  - Background: `#0A0E0D`
  - Surfaces: `#121917`
  - Raised Surfaces: `#18211E`
  - Borders: `#24302C`
  - Text: `#E4EBE8`
  - Muted Text: `#9AA8A2`
  - Accent Mint: `#5DCAA5` (single theme value)
  - Warning Amber: `#F2C14E`
  - Error: `#FF6B6B`
- **Typography**: `Chivo` for text, `Chivo Mono` for times, IDs, and numbers, configured via the downloadable Google Fonts provider (`androidx.compose.ui:ui-text-google-fonts`) with a system fallback (`FontFamily.SansSerif` / `FontFamily.Monospace`). No bundled `.ttf` files referenced.
- **Abstract Orb Avatar**: Mint radial glow, white core, thin ring. Pulses while Rivia speaks and breathes slowly while she listens.
- **Status Shapes**: `ok` = mint dot, `warning` = amber ring, `error` = red diamond.
- **Accessibility**: Tap targets at least `48dp`; content descriptions on all icon buttons.
- **Galaxy Z Fold6 Adaptive Layout**:
  - **Cover Screen (Compact width)**: Single pane with a navigation drawer.
  - **Unfolded (Medium or Expanded width)**: Chat list stays open beside the active chat. Respects window insets and keeps content off the hinge.

---

## 3. Key Technical Decisions & Refinements Applied

1. **History, Branching & Raw Step Rebuilds (Gemini Interactions API)**:
   - With `store: true`, send only the new message as `input`, plus `previous_interaction_id` set to the last interaction's ID. Because `previous_interaction_id` carries only inputs and outputs, `system_instruction`, `tools`, and `generation_config` are sent on **every turn**.
   - With `store: false`, or when the server no longer has the earlier interaction (e.g., expired past 55 days or 404/not found on `previous_interaction_id`), rebuild the entire conversation from the saved raw steps in Room (`{"type":"user_input","content":"..."}` for user turns, and Rivia's raw steps exactly as received, including `thought_signature`) and send as `input`.
   - **Edit and Regenerate**: Branch from the interaction **before** the replaced turn by setting `previous_interaction_id` to the interaction ID immediately preceding the replaced turn (never the latest one), and replacing subsequent turns.

2. **SSE Streaming, JSON `event_id`, & Resilient Reconnection**:
   - Parse SSE events from `POST https://generativelanguage.googleapis.com/v1beta/interactions?alt=sse`:
     - `interaction.created` (save interaction ID)
     - `step.start`
     - `step.delta` (`delta.type == "text"` appends reply text; `"thought_summary"` appends `delta.content.text` into Thinking; `"thought_signature"` is preserved in raw step)
     - `step.stop`
     - `interaction.status_update`
     - `interaction.completed` (save usage: `total_input_tokens`, `total_output_tokens`, `total_thought_tokens`)
     - `error`
     - Treat `event: done` or `data: [DONE]` as the end of the stream. Log and skip any unrecognized event type without throwing.
   - **Event ID Tracking & Drop Recovery**:
     - Read `event_id` directly from each event's JSON payload (while still accepting an SSE `id:` line if one appears) and save the latest one as `last_event_id`.
     - Retry `POST` with exponential backoff (on 429, 500, 503, and timeouts) **only if it failed before `interaction.created` arrived**.
     - Once `interaction.created` has arrived, **never re-POST**. If `last_event_id` is available, reconnect to the same interaction via `GET https://generativelanguage.googleapis.com/v1beta/interactions/{id}?alt=sse&stream=true&last_event_id=<last_event_id>`.
     - **Fallback Polling when no `event_id` arrived**: If the stream drops after `interaction.created` but before any `event_id` has arrived, poll `GET /v1beta/interactions/{id}` until it finishes (`completed`, `failed`, `cancelled`, or `incomplete`) and replace the partial reply with its `outputs`.
     - Configure OkHttp streaming read timeout to at least **5 minutes** for standard/priority requests, and **16+ minutes** for `flex` service tier requests.

3. **Strict Audio-Tag Filter & Speakable Text Transformation**:
   - **Audio-Tag Rule**: Only lowercase letters and spaces, **3 to 30 characters**, inside square brackets (`\[[a-z ]{3,30}\]`) count as an audio tag.
     - **Exclusions**: Never match inside inline code spans (`` `...` ``) or fenced code blocks (` ```...``` `), and never match when immediately followed by `(`, `[`, or `:` (`(?![(\[:])`), so markdown links `[text](url)`, reference links `[text][id]`, footnotes/labels `[note]:`, checkboxes `[x]`, and arrays `[1, 2]` stay completely intact.
   - **Speakable Text Transformation for Read-Aloud**:
     - Before sending text to ElevenLabs, convert markdown into clean speakable prose:
       - Replace fenced code blocks and markdown tables with a concise spoken note (e.g., `"The code is on screen."` / `"The table is on screen."`).
       - Replace markdown links `[label](url)` with just the spoken `label` text, and replace **bare URLs** (`https?://\S+`) with the word `"link"`.
       - Strip markdown formatting marks (`**`, `*`, `__`, `_`, `~~`, heading `#` prefixes, blockquote `>`, list bullets, inline backticks).
       - Preserve valid `[audio tags]` in the speakable text when `"Send audio tags"` is on, or strip them if `"Send audio tags"` is off.

4. **Shared Audio Player, Transient Audio Focus & Routing**:
   - **Shared Player without Service Overhead in Typed Chat**: `Listen` on a message and `auto read-aloud` in typed chat use the same shared ExoPlayer pipeline (`AudioPlayerManager`) directly in-process and **never** start `LiveVoiceService` or require `RECORD_AUDIO`.
   - **Manual Transient Audio Focus (`AUDIOFOCUS_GAIN_TRANSIENT`)**:
     - Set `handleAudioFocus = false` on ExoPlayer (`setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), /* handleAudioFocus = */ false)`) because ExoPlayer's automatic focus handling only supports `AUDIOFOCUS_GAIN` (permanent), which stops music apps permanently.
     - Instead, manually request `AudioManager.AUDIOFOCUS_GAIN_TRANSIENT` using an `AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)`:
       - **For `Listen` and `auto read-aloud`**: Request transient focus right before Rivia's first sentence begins playing, and call `abandonAudioFocusRequest` as soon as her last sentence finishes (or playback is stopped), allowing paused music apps to resume automatically.
       - **In `Live` mode**: Request transient focus when Live mode starts and hold it until Live mode ends (`End`), then abandon it so paused music resumes.
       - **On Audio Focus Loss** (e.g., incoming phone call or alarm): Immediately stop Rivia's playback; in `Live` mode, also pause listening until `AUDIOFOCUS_GAIN` returns.
   - **Audio Routing**: Use standard media audio attributes (`USAGE_MEDIA`, speech content type) with no Bluetooth SCO or `setCommunicationDevice` so earbuds get full-quality media audio (unless the user explicitly enables `"Use earbud mic in Live (call-quality audio)"`, off by default).
   - **ExoPlayer Low-Latency Buffering**: Configure `ExoPlayer` with a custom `DefaultLoadControl` setting `bufferForPlaybackMs = 250` (and `bufferForPlaybackAfterRebufferMs = 500`) so each sentence plays immediately upon arrival.
   - **Output Format Picker**: Filter/offer `mp3_` formats only (`mp3_44100_128` by default, plus `mp3_22050_32`, `mp3_44100_64`, `mp3_44100_96`, `mp3_44100_192`).

5. **Live Mode Loop, Mic Gating, Error Recovery & Unified Foreground Service**:
   - **Strict Mic Gating & Error Recovery**: Keep `SpeechRecognizer` closed while Rivia speaks. Reopen the mic when playback of all sentence chunks finishes, when the user taps the orb to interrupt, **and after an error occurs** (after showing the error in captions and playing the short error tone) so Live mode stays active and hands-free.
   - **Chat Persistence for Live Turns**: Save every Live turn in the active chat with the user's side labeled `"voice · m:ss"` (recording duration of that spoken turn).
   - **Keep Screen On**: Honor the `"Keep screen on in Live"` setting (`on` by default).
   - **Single Foreground Service**: Declare one service (`LiveVoiceService`) in `AndroidManifest.xml` with `android:foregroundServiceType="microphone|mediaPlayback"`. Request `RECORD_AUDIO` and `POST_NOTIFICATIONS` on the Live screen before starting it; if `RECORD_AUDIO` is denied, do not start the service and explain why on screen.

---

## 4. Technical Architecture & Component Specifications

### System Component Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                              UI LAYER                                  │
│  ┌───────────────────────┐  ┌────────────────┐  ┌───────────────────┐  │
│  │   ChatScreen (M3)     │  │  LiveScreen    │  │  SettingsScreen   │  │
│  │  - Adaptive Fold6     │  │  - Orb Canvas  │  │  - Keystore Keys  │  │
│  │  - Markdown & Tables  │  │  - Edge Glow   │  │  - Voice & Models │  │
│  │  - RunSettings Sheet  │  │  - Captions    │  │  - Plug-ins & Log │  │
│  └───────────┬───────────┘  └───────┬────────┘  └─────────┬─────────┘  │
└──────────────┼──────────────────────┼─────────────────────┼────────────┘
               ▼                      ▼                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                           VIEWMODEL & ENTRY                            │
│         ChatViewModel              │         RiviaEntryPoint           │
│  - SSE Post / GET Reconnect / Poll │   - askRivia(text, channel, reqId)│
│  - Branching Edit / Regenerate     │   - speakLine(text)               │
│  - Dictation SpeechRecognizer      │   - PluginRegistry Tool Dispatch  │
└──────────────┬─────────────────────┴──────────────────────┬────────────┘
               ▼                                            ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        AUDIO & SERVICE LAYER                           │
│  ┌─────────────────────────────────┐  ┌─────────────────────────────┐  │
│  │ AudioPlayerManager (Shared)     │  │ LiveVoiceService (FGS)      │  │
│  │ - Media3 ExoPlayer (250ms buf)  │◄─┤ - microphone|mediaPlayback  │  │
│  │ - handleAudioFocus = false      │  │ - Holds Transient Focus     │  │
│  │ - AudioFocusRequest (TRANSIENT) │  │ - Pauses on Focus Loss      │  │
│  │ - Bare URL -> "link" Converter  │  │ - Mic Gated during playback │  │
│  └───────────┬─────────────────────┘  └─────────────┬───────────────┘  │
└──────────────┼──────────────────────────────────────┼──────────────────┘
               ▼                                      ▼
┌────────────────────────────────────────────────────────────────────────┐
│                           NETWORK LAYER                                │
│  ┌───────────────────────────────┐    ┌─────────────────────────────┐  │
│  │ GeminiInteractionsClient      │    │ ElevenLabsClient            │  │
│  │ - POST /v1beta/interactions   │    │ - GET /v2/voices?personal   │  │
│  │ - JSON event_id & GET Fallback│    │ - GET /v1/models & settings │  │
│  │ - Background Poll & Model List│    │ - POST Stream (mp3_ only)   │  │
│  └───────────────┬───────────────┘    └──────────────┬──────────────┘  │
└──────────────────┼───────────────────────────────────┼─────────────────┘
                   ▼                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                           STORAGE LAYER                                │
│  ┌───────────────────────────────────┐  ┌───────────────────────────┐  │
│  │ Room Database                     │  │ Security & Preferences    │  │
│  │ - Chats, Messages, RawSteps       │  │ - Android Keystore (AES)  │  │
│  │ - Per-Chat & Default RunSettings  │  │ - DataStore (No Backup)   │  │
│  │ - Cached Models & 100 RequestLogs │  │ - Zero Key Logging        │  │
│  └───────────────────────────────────┘  └───────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Complete Control Specification: Section 4 (Run Settings) & Section 5 (Voice)

#### Section 4: Run Settings (Per-Chat via Gear Icon + "Save as default for new chats" & "Reset to defaults")
- **Models**: Model picker for typed chat + separate model picker for Live mode (populated from cached/refreshed `GET /v1beta/models?pageSize=1000` filtered to `generateContent` and excluding `tts`, `image`, `banana`, `embedding`, `live`, `transcribe`, `lyria`, `omni`, `robotics`).
- **System Instructions & Channel Styles**:
  - Multi-line `System instructions` (default: `"You are Rivia, Jordan's teammate. You never make up facts; when you don't know something, say so."`).
  - `Style for typed chat` appended on typed chat requests (default: `"Markdown is fine. Add ElevenLabs audio tags in square brackets right before the words they affect, only where the emotion changes and at most one per clause, so replies sound right when read aloud."`).
  - `Style for Live mode` appended on Live requests (default: `"Your reply is spoken by ElevenLabs. Answer in one or two short sentences. Plain text only: no markdown, lists, emoji or URLs. Add audio tags in square brackets, such as [curious], [excited], [thoughtful], [softly], [sighs], [laughs] or [pause], right before the words they affect. Add a new tag only where the emotion changes, at most one per clause; never put contrasting tags on the same words, and use no accent or sound-effect tags. Use square brackets only for tags."`).
- **Thinking Level & Thought Summaries**:
  - First choice: `"Model default"`, followed only by values allowed by the selected model:
    - `gemini-4-argon` and `gemini-3.8-flash`: `low`, `medium`, `high`
    - `gemini-3.6-flash`, `gemini-3.5-flash-lite`, `gemini-3-flash-preview`: `minimal`, `low`, `medium`, `high`
    - `gemini-3.1-pro-preview`: `low`, `medium`, `high`
    - Any other model: `minimal`, `low`, `medium`, `high`
  - Thought summaries (`generation_config.thinking_summaries`): `auto` or `none`.
- **Output & Sampling Controls**:
  - `Max output tokens`: up to the model's `outputTokenLimit`, labeled `"includes thinking"`.
  - `Stop sequences`: editable chip list.
  - `Seed`: integer input.
  - `Temperature`, `top-p`, `top-k`: active only for models older than Gemini 3.6 (with range up to `maxTemperature`). For Gemini 3.6+ and Gemini 4, display `"Ignored by this model"`.
- **Tools & Tool Choice**:
  - Typed tool objects: Google Search (`{"type":"google_search"}` with web or image sub-options), URL context (`{"type":"url_context"}`), Code execution (`{"type":"code_execution"}`), Google Maps (`{"type":"google_maps"}` with latitude & longitude fields), File search (`{"type":"file_search"}` with store names).
  - Tool choice (`generation_config.tool_choice`): `auto`, `any`, `none`, or `validated`.
- **Structured Output**:
  - `Off`, or `JSON` via `response_format: {"type":"text","mime_type":"application/json","schema":{...}}`.
  - Schema editor validates JSON syntax, warns if `minLength`, `maxLength`, or `uniqueItems` appear (noting they are ignored), and warns that schema + tools works only on Gemini 3 models.
- **Safety Settings**:
  - Individual selectors for `hate_speech`, `dangerous_content`, `harassment`, `sexually_explicit`, and `jailbreak`: `Model default`, `off`, `block_only_high`, `block_medium_and_above`, `block_low_and_above`.
  - Method selector: `severity` or `probability`.
- **Service Tier**:
  - `standard`, `flex` (half price, 1–15 min, may be dropped), or `priority` (costs more, may fall back to standard).
- **Storage & Labels**:
  - `store`: on or off (turning off disables server-side history and `background`).
  - `background`: on or off (when on, polls `GET /v1beta/interactions/{id}` until `completed`, `failed`, `cancelled`, or `incomplete` and displays live progress).
  - `labels`: editable key-value pairs.
- **Advanced**:
  - `"Extra JSON"` box merged into the top-level request body for any unlisted field.
  - `"Preview request"` modal showing the exact outgoing JSON with API keys redacted.
- **Editable Mode Chip Presets**:
  - `Fast` = `gemini-3.5-flash-lite` at `minimal` thinking
  - `Balanced` = `gemini-3.8-flash` at `medium` thinking
  - `Deep` = `gemini-4-argon` at `high` thinking
  - All three presets are user-editable.

#### Section 5: Voice Settings (`Settings > Voice`)
- **Voice Picker**: Populated from `GET https://api.elevenlabs.io/v2/voices?voice_type=personal` (`xi-api-key` header), showing name, category, and audio preview button; plus manual Voice ID paste field.
- **Model Pickers**: Separate pickers for Read-Aloud (default `eleven_v4`) and Live mode (default `eleven_v4_turbo`), populated from `GET https://api.elevenlabs.io/v1/models` filtered by `can_do_text_to_speech == true`.
- **Voice Settings**: Initialized from `GET https://api.elevenlabs.io/v1/voices/{voice_id}/settings`:
  - `stability`, `similarity_boost`, `use_speaker_boost`.
  - `style` and `speed` sliders shown only for models other than `eleven_v4` and `eleven_v4_turbo` (hidden for `eleven_v4` and `eleven_v4_turbo`).
  - `clone_strength` (0 to 1) shown for professional voice clones on `eleven_v4`.
- **Normalization, Format & Tags**:
  - `apply_text_normalization`: `auto`, `on`, or `off`.
  - `output_format`: `mp3_` formats only (`mp3_44100_128` by default, `mp3_22050_32`, `mp3_44100_64`, `mp3_44100_96`, `mp3_44100_192`).
  - `"Send audio tags"`: on by default (keeps square-bracket `[audio tags]` for ElevenLabs, hides on screen; off strips them from both).
  - `"Test"` button: speaks a sample line using the current voice configuration.
- **Streaming Playback Pipeline & Transient Audio Focus**:
  - Converts markdown to speakable text (replacing code blocks/tables with a spoken note, reading link labels instead of URLs, replacing bare URLs with `"link"`, stripping markdown formatting marks).
  - Splits replies at sentence boundaries, requests chunks sequentially from `POST /v1/text-to-speech/{voice_id}/stream?output_format=...` passing `{text, model_id, voice_settings, previous_text, apply_text_normalization}`, and plays immediately via `ExoPlayer` (`bufferForPlaybackMs = 250`, `handleAudioFocus = false`).
  - Requests `AUDIOFOCUS_GAIN_TRANSIENT` via `AudioFocusRequest` before playback starts and abandons it when the last sentence finishes so paused music resumes; stops playback if audio focus is lost.
  - If a model is rejected by the API, displays the exact error message and falls back to `eleven_flash_v2_5`.
- **Speech-to-Text & Live Settings**:
  - Speech-to-text language (default `en-US`).
  - `"Prefer on-device recognition"` toggle.
  - `"Keep screen on in Live"` toggle (on by default).
  - `"Use earbud mic in Live (call-quality audio)"` toggle (off by default).

---

## 5. Ordered Build Sequence

We will build and verify the application in strict section order, reporting added libraries and manifest permissions after each section:
1. **Section 0 & 1 — Ground Rules & Look**: `observer.rivia.app`, `minSdk 31`, Keystore AES + DataStore (no backup), downloadable Google Fonts (`Chivo` & `Chivo Mono`), `#0A0E0D` dark theme, `RiviaOrb`, status shapes, and Fold6 adaptive scaffold.
2. **Section 2 — Chat & Room Persistence**: Top bar, mode chip, markdown renderer (bold, lists, tables, code blocks with copy, links), strict `[audio tag]` filter, collapsible Thinking row, message actions (Listen, Copy, Share, Regenerate, Edit, Details with `r-xxxx` request ID), SpeechRecognizer dictation composer, drawer with search/rename/delete, and Room database.
3. **Section 3 — Gemini Interactions REST Client**: OkHttp SSE streaming, JSON `event_id` tracking, raw step saving, `previous_interaction_id` vs. raw step rebuild, branching Edit/Regenerate, pre-creation retry vs. `GET ?stream=true&last_event_id=` reconnect vs. `GET` polling fallback, background polling, and filtered model list cache.
4. **Section 4 — Run Settings**: Per-chat and default Run Settings sheet/screen with all controls, model-specific thinking levels and temperature gating, tools, JSON schema validator, safety settings, service tiers, Extra JSON merger, redacted JSON preview, and editable mode presets.
5. **Section 5 — Voice (ElevenLabs & ExoPlayer)**: Personal voices & models fetchers, conditional voice settings (`eleven_v4` rules), `mp3_` format picker, speakable markdown converter (including bare URL -> `"link"`), sentence splitter with `previous_text`, shared low-latency `ExoPlayer` (`bufferForPlaybackMs = 250`, `handleAudioFocus = false`) with manual `AUDIOFOCUS_GAIN_TRANSIENT` management, fallback to `eleven_flash_v2_5`, and STT preferences.
6. **Section 6 — Live Mode**: Full-screen orb with edge mint glow, live captions, tap-to-interrupt, strict mic gating while speaking + reopen after error, transient audio focus from start to End (pausing listening/stopping playback on focus loss), `"voice · m:ss"` chat turn saving, `"Keep screen on in Live"`, `LiveVoiceService` (`microphone|mediaPlayback`) with runtime `RECORD_AUDIO` & `POST_NOTIFICATIONS` checks, ongoing notification (Mute, End), and error tone/caption handling.
7. **Section 7 — Plug-ins**: `Plugin` interface, `PluginRegistry`, `"Tasker: coming soon"` in Settings, and internal entry points for asking Rivia and speaking a line.
8. **Section 8 — Settings, Request Log & Import/Export**: Key management with Test buttons, 100-entry Request Log viewer, and full JSON chat export/import.
