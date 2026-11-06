# Rivia / Dev / Labs Follow-Up — Pre-Build Answers & Approved Plan

## Part 0: Answers to Your 3 Pre-Build Questions

### 1. Emotion in the Prompt (`assembleSystemInstruction` & Rivia Chats Today)
* **Does the emotion reading change `system_instruction` or anything else in the Gemini request for Rivia chats today?**
  **No — Pass 2 is strictly log-only.** Nothing from the emotion model or baseline is ever added to `system_instruction` or the Gemini request body.
* **Note on my earlier snippet:** In my previous answer, I mistakenly wrote `currentEmotion` in an illustrative snippet. In the actual codebase, `LayerConfigManager.assembleSystemInstruction` **does not have a `currentEmotion` parameter at all**.
* **Exact Code Path Today:**
  1. **`LayerConfigManager.assembleSystemInstruction` (`app/src/main/java/com/example/model/LayerConfigManager.kt`, lines 596–603):**
     ```kotlin
     fun assembleSystemInstruction(
       channel: String, // "text" or "voice"
       persona: PersonaLayerConfig,
       style: StyleLayerConfig,
       hardRules: HardRulesLayerConfig,
       spokenMaxSentences: Int,
       pendingH7Notice: String? = null,
     ): String
     ```
     It joins only `persona.text`, `style.text` / `style.voice`, and active `hardRules` (plus `pendingH7Notice` if present).
  2. **Rivia Live Voice Turn (`RiviaViewModel.executeLiveVoiceTurnInChat`, `app/src/main/java/com/example/viewmodel/RiviaViewModel.kt`, lines 3025–3118):**
     * Lines 3026–3104 read the emotion barrier result (`barrierRes.prediction`) and baseline (`queryAndComputeEmotionBaseline`), build `payloadJson` via `TurnPayloadBuilder.buildPayloadJson(...)`, and insert a local row into Room (`dao.insertTurnDispatchLog(...)`).
     * Separately, `handleLiveMicCaptureAndEmotionLogging` (lines 2912–2960) writes to `voice_emotion_logs`.
     * Lines 3106–3117 then call `executeAssistantTurn(chat, settings, RiviaChannel.LIVE_VOICE, priorMessages, userText, prevInteractionId, reqId, ...)` passing only `userText` (the plain speech transcript).
  3. **Rivia Request Construction (`RiviaViewModel.executeAssistantTurn`, lines 2258–2281):**
     ```kotlin
     val customBodyBuilder: ((JSONArray, String?, Boolean) -> JSONObject)? =
       if (isRivia && bundle != null) {
         { inputSteps, prevId, stream ->
           val sysInstruction =
             LayerConfigManager.assembleSystemInstruction(
               channel = layerChannel,
               persona = bundle.persona,
               style = bundle.style,
               hardRules = bundle.hardRules,
               spokenMaxSentences = _spokenMaxSentences.value,
               pendingH7Notice = pendingH7NoticeToSend,
             )
           geminiClient.buildRiviaRequestBodyJson(
             engine = bundle.engine,
             profileName = normalizedProfile,
             assembledSystemInstruction = sysInstruction,
             inputSteps = inputSteps,
             previousInteractionId = prevId,
             stream = stream,
           )
         }
       } else {
         null
       }
     ```
     And for Labs (`RiviaViewModel.kt`, lines 2286–2300), `LayerConfigManager.assembleSystemInstruction` is called with those exact same 6 parameters (`channel`, `persona`, `style`, `hardRules`, `spokenMaxSentences`, `pendingH7Notice = null`). Emotion readings never enter the prompt or request payload anywhere.

---

### 2. H9 Text: (a) Shown in Chat vs. (b) Spoken in Live
* **(a) Exact text shown in chat (`LayerConfigManager.formatH9EmptyReplyReason`, `app/src/main/java/com/example/model/LayerConfigManager.kt`, lines 892–968; saved to `assistantMsg.errorMessage` in `RiviaViewModel.kt`, line 2514; rendered in `AssistantMessageBubble` in `ChatScreen.kt`, lines 2060–2079):**
  When a turn completes with a blank reply (`isReplyEmpty == true`, `RiviaViewModel.kt`, lines 2465–2482), `formatH9EmptyReplyReason` produces one of these exact strings:
  1. **Safety block:**
     `"No reply: blocked for safety (<category>)"` (where `<category>` is the safety block reason/category, or `"unspecified"` if blank/`"SAFETY"`).
  2. **Output tokens ran out with thinking tokens (`thoughtTokens > 0`):**
     `"No reply: max output tokens used by thinking (<thoughtTokens> of <max>)"`
  3. **Output tokens ran out without thinking tokens (`thoughtTokens == 0`):**
     `"No reply: max output tokens reached"`
  4. **Any other empty completion:**
     * `"No reply: empty completion (<extraReason>)"` if the API returned a non-`"stop"` / non-`"completed"` finish reason or status, or
     * `"No reply: empty completion"` if none was given.
  *(If the turn fails with an HTTP/network/stream error instead of an empty completion, `assistantMsg.errorMessage` stores the redacted API error string `redactedErr` at lines 2706/2787; in Live mode, `LiveUiState.errorCaption` displays that same string, appending `" · Paused after 3 errors in a row. Tap the orb to resume."` on the 3rd consecutive error at `LiveSessionManager.kt`, lines 1423–1428.)*
  *(In `ChatScreen.kt`, we will render `"No reply:"` H9 notices using `StatusWarning` `#F2C14E` and general errors using `StatusError` `#FF6B6B`.)*

* **(b) Exact text spoken in Live (`LiveSessionManager.kt`, line 92, lines 1390–1397, and lines 1432–1469):**
  * **Fixed template constant (`LiveSessionManager.kt`, line 92):**
    ```kotlin
    const val H9_SPOKEN_FALLBACK_LINE = "No reply came back. It's in the log."
    ```
  * **How it is triggered and spoken:**
    * In `RiviaViewModel.executeAssistantTurn` (lines 2483–2485, 2549, 2707–2709, 2762, 2788–2790, 2846), `h9Active` is `isRivia && (bundle?.hardRules?.rules?.any { it.id == "H9" && it.active } ?: false)`, and `onErrorCallback?.invoke(h9NoticeOrError, h9Active)` is called.
    * In `LiveSessionManager.processUserSpokenTurn` (lines 1390–1397):
      ```kotlin
      /* onTurnError = */ { apiErr, speakH9Fallback ->
        turnHadError = true
        player.stopPlayback()
        handleNonSilenceError(
          errorMessage = apiErr,
          speakFixedLineInVoice = if (speakH9Fallback) H9_SPOKEN_FALLBACK_LINE else null,
        )
      }
      ```
    * In `LiveSessionManager.handleNonSilenceError` (lines 1432–1458), when `speakFixedLineInVoice != null`, it calls `player.speakMarkdownMessage(messageId = null, rawMarkdown = "No reply came back. It's in the log.", isLiveChannel = true, ...)` (falling back to `player.playShortErrorTone()` if TTS itself fails). Model output is **never** passed to TTS on an H9 no-reply turn.

---

### 3. What It Would Take to Add a Per-Lab-Chat Toggle `"Use Labs layers"` (Files & Risks Only — No Code)
To add a per-Lab-chat toggle `"Use Labs layers"` that, when **ON**, builds the Lab chat's request via `LayerConfigManager.buildRiviaRequestBodyJson` from the `labs:*` layers (and when **OFF**, keeps today's raw `RunSettings` behavior):

#### Files That Would Be Touched
1. **`app/src/main/java/com/example/model/RunSettings.kt`**
   * Add `val useLabsLayers: Boolean = false` to `RunSettings` (with JSON serialization in `toJsonString()` / `fromJsonString()`, avoiding any Room schema change since `runSettingsJson` is already stored per chat in `ChatEntity`).
2. **`app/src/main/java/com/example/viewmodel/RiviaViewModel.kt`**
   * In `sendUserMessage`, `editUserMessageAndResend`, `regenerateLastReply`, `executeLiveVoiceTurnInChat`, and `executeAssistantTurn`:
     * Introduce `val useLayersPipeline = isRivia || (chat.isLabChat && settings.useLabsLayers)`.
     * Load/snapshot the appropriate layers bundle for the chat (`labs:*` drafts + `layer_settings` `id = 2` when `chat.isLabChat`, vs. unprefixed Rivia drafts + `id = 1` when `!chat.isLabChat`).
     * When `useLayersPipeline` is `true`, select the model/tier from `bundle.engine.profiles[normalizedProfile]`, construct `customBodyBuilder` using `LayerConfigManager.assembleSystemInstruction` + `geminiClient.buildRiviaRequestBodyJson`, and evaluate `H7` / `H9` / `H11` against the `labs:*` bundle while keeping `chatKind = "Lab"`.
3. **`app/src/main/java/com/example/ui/screens/ChatScreen.kt`**
   * Add the `"Use Labs layers"` switch to `RunSettingsDrawer` (and/or the Lab chat top bar).
   * When `"Use Labs layers"` is enabled on a Lab chat, display the profile selector (`Fast`, `Balanced`, `Deep`, `Live (typed)`) so the user can choose which `labs:engine` profile (`chat.riviaProfile`) to test, and visually indicate that `labs:*` layers are driving the request instead of raw `RunSettings` sampling/model fields.

#### Risks to Watch Out For
1. **UI Mode vs. Chat Scope Race (`_loadedLayersBundle` coupling):**
   * Today, `_loadedLayersBundle` and `_spokenMaxSentences` in `RiviaViewModel` switch reactively when `_appUiMode` changes. If a user starts a turn in Labs and switches modes while streaming (or if `executeAssistantTurn` reads `_loadedLayersBundle.value` directly), it could read the wrong mode's bundle unless `executeAssistantTurn` explicitly resolves the `labs:*` bundle for `chat.isLabChat`.
2. **Rivia Telemetry & Fingerprint Leakage:**
   * If `H7`, `H9`, or `H11` rules fire in a Lab chat with `"Use Labs layers"` enabled, writing to `dao.insertRuleEvent(...)` would mix Labs rule events into Dev's live `rule_events` table unless `rule_events` is either skipped for Lab chats or filtered by chat kind. Similarly, Lab chats must never write to `layer_test_runs` for Rivia fingerprints.
3. **Stale `pendingH7Notice` When Toggling Off:**
   * If `H7` trims a spoken reply while `"Use Labs layers"` is ON, `chat.pendingH7Notice` is written to `ChatEntity`. If the user then toggles `"Use Labs layers"` OFF in the same Lab chat, `pendingH7Notice` must be ignored and cleared so it doesn't stay stuck on the chat row.
4. **Silent Override of `RunSettingsDrawer` Controls:**
   * `buildRiviaRequestBodyJson` ignores `RunSettings` fields like `temperature`, `topP`, `topK`, `seed`, `stopSequences`, `systemInstruction`, and per-chat tool toggles. If those controls remain editable in `RunSettingsDrawer` while `"Use Labs layers"` is ON without being disabled/badged as overridden by `labs:engine`, it will look like changing temperature or model in `RunSettingsDrawer` should take effect when `buildRiviaRequestBodyJson` actually ignores them.

---

## Part 1: Approved Implementation Plan (Ready to Build on Proceed)

### 1. Mode Switching (`ChatScreen.kt`)
* Remove `ModeSwitcherSegmentedPill` (`Rivia | Dev | Labs`) from the top bar.
* Add a quiet `"Dev & Labs"` row (`Modifier.testTag("drawer_dev_labs_button")`) at the very bottom of the chats drawer, below Settings.
* Tapping `"Dev & Labs"` opens `DevLabsModeSheet` with two choices:
  * **Dev**: Requires a `600 ms` long press (`withTimeoutOrNull(600L) { tryAwaitRelease() } == null`), triggers a haptic tick (`HapticFeedbackType.LongPress`) when it unlocks, and enters `AppUiMode.DEV`.
  * **Labs**: Opens on a normal tap (`AppUiMode.LABS`).
* In **Dev** and **Labs**, show a small mode tag (`"DEV"` or `"LABS"`) in the top bar and an `"Exit to Rivia"` action (`Modifier.testTag("drawer_exit_to_rivia_button")`) in the drawer.
* **Rivia** mode shows no mode tag.

### 2. Mode Colors & Status Color Reservation (`Color.kt`, `ChatScreen.kt`, `LayersScreen.kt`)
* **Rivia**: Mint `#5DCAA5`.
* **Dev**: Violet `#A78BFA`.
* **Labs**: Blue `#60A5FA`.
* **Status colors (`#FF6B6B` and `#F2C14E`)**: Reserved strictly for errors (`#FF6B6B`), the H9 `"No reply"` notice (`#F2C14E`), the error tone, `"not tested"` profile dots (`#F2C14E`), and failed steps/badges (`#FF6B6B`). Never change with the mode theme.

### 3. Tests Placement (`ChatScreen.kt`, `LayersScreen.kt`)
* Remove the Step 0 / Step 1 test runner from the chats drawer in every mode.
* Move the 7 Step 0 / Step 1 checks and `"Run all 7 checks"` to a dedicated **Tests** screen (`LabsTestsScreen`) accessible only in **Labs** mode from the drawer (`Modifier.testTag("drawer_labs_tests_button")`).
* Keep the layer tests (`H10` pushback, `H7` spoken length) strictly in **Dev** mode, inside the Layers editor's **Tests** card.

### 4. Backup in Rivia & Strict Labs Isolation (`SettingsScreen.kt`, `RiviaDao.kt`, `RiviaViewModel.kt`)
* Add **Backup** (`Export JSON` and `Import JSON`) back to **Rivia** mode's Settings alongside **Voice**. API keys and the app signature SHA-256 stay **Dev-only**.
* Scope `requestLogs` and `totalRequestLogsCount` by `chatKind` (`"Lab"` in Labs mode vs. `"Rivia"` in Dev mode) via `RiviaDao.observeRequestLogsByKind` / `observeTotalRequestLogsCountByKind` so Labs Request Log never shows Rivia requests.
* Keep **Plug-ins** strictly in **Dev** Settings, hide live Rivia emotion/rule logs in Labs Settings, and guard `PluginRegistry.updateH2State` in `RiviaViewModel.recomputeLayerState` so Labs never shows or changes Rivia's data.

### 5. Tests to Add & Run (`app/src/test/java/com/example/Pass1LayersTest.kt`)
1. `testLabsEditsLeaveRiviaFingerprintsUnchanged`: Editing any Labs layer, voice, or VAD value leaves all four Rivia profile fingerprints (`fast`, `balanced`, `deep`, `live`) unchanged.
2. `testStatusColorsRemainRedAndAmberInAllModeThemes`: Renders the profile test dots, the H9 `"No reply"` notice, and a failed test badge under each mode theme (`Rivia`, `Dev`, `Labs`) via Robolectric Compose UI test (`createComposeRule()`) and asserts the colors actually drawn are `#FF6B6B` and `#F2C14E`.
3. `testDatabaseMigrationPreservesIsLabChatWithoutDestructiveFallback`: Verifies existing chats keep their `isLabChat` values after migration, nothing is deleted, and no `fallbackToDestructiveMigration` is used (no database schema change; stays at `version = 7`).

### 6. Complete List of Files to Touch
1. `app/src/main/java/com/example/ui/theme/Color.kt`
2. `app/src/main/java/com/example/data/RiviaDao.kt`
3. `app/src/main/java/com/example/viewmodel/RiviaViewModel.kt`
4. `app/src/main/java/com/example/ui/screens/ChatScreen.kt`
5. `app/src/main/java/com/example/ui/screens/LayersScreen.kt`
6. `app/src/main/java/com/example/ui/screens/SettingsScreen.kt`
7. `app/src/test/java/com/example/Pass1LayersTest.kt`
