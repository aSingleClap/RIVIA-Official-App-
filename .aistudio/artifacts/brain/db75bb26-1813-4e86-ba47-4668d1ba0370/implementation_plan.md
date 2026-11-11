# Fix Empty `ask_bot` Function Call Arguments in Labs Chats

Resolves the bug where `ask_bot` function calls in Labs chats reach the tablet with empty arguments (`{}`), causing `missing_argument` rejections.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Root Cause Analysis — What Shape the Code Was Expecting vs. What Actually Happened**:
> 1. **SSE `content.start` / ` content.delta` / `content.stop` vs. `step.*`**: `processSseStream` only looked for `json.optJSONObject("step")` on `content.start` and `content.stop` (ignoring `json.optJSONObject("content")`), and completely ignored `"function_call"` deltas in `content.delta` / `step.delta` (only handling `"text"`, `"thought_summary"`, and `"thought_signature"`).
> 2. **`finalizeStep` kept empty `{}` from start event**: When a `function_call` step started with `"arguments": {}`, `finalizeStep` only copied fields from the stop event when `!currentStep.has(k)`. Because `currentStep.has("arguments")` was already `true` (with `{}`), the populated `"arguments"` on the stop event was discarded.
> 3. **`extractFunctionCalls` deduplication kept the empty start/stop step over `interaction.completed` `outputs`**: When `interaction.completed` arrived with `outputs` containing the full `function_call`, `onCompleted` appended it *after* the empty step in `rawSteps`. `extractFunctionCalls` then used `seenIds.add("$id:$name")`, locking in the first (empty `{}`) entry and ignoring the populated entry from `outputs`.

## 1. Overview & Core Concept

- **What It Does**: Ensures `function_call` arguments (`bot` and `request` for `ask_bot`) from Gemini Interactions SSE streams and responses are preserved intact—whether delivered as a JSON object or a JSON-encoded string, across `content.*`/`step.*` events or `interaction.completed` `outputs`—and guards `ask_bot` against missing `bot` or `request` before calling the tablet.
- **Scope**: Bug fix only. No Room schema changes, no new permissions, and no new libraries.

## 2. Technical Architecture & Data Flow

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     GeminiInteractionsClient (SSE / Poll)                │
│                                                                          │
│  1. content.start / step.start                                           │
│     • Read step from json.optJSONObject("step")                          │
│       ?: json.optJSONObject("content")                                   │
│                                                                          │
│  2. content.delta / step.delta                                           │
│     • Merge function_call fields (id, call_id, name, arguments/args)     │
│       into currentStepObj (accumulating string chunks or merging JSON)   │
│                                                                          │
│  3. content.stop / step.stop -> finalizeStep                             │
│     • Unwrap stopStep ("step" ?: "content" ?: json)                      │
│     • Overwrite currentStep keys when stopStep has non-empty arguments   │
│                                                                          │
│  4. interaction.completed -> outputs & extractFunctionCalls              │
│     • Parse arguments whether JSONObject or JSON-encoded String          │
│     • If a later step with same id:name has non-empty arguments,         │
│       replace any earlier empty-arguments entry                          │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │ GeminiFunctionCall(id, name, arguments)
                                     ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                        RiviaViewModel (Labs Chat)                        │
│                                                                          │
│  • Normalize/parse arguments (JSONObject or JSON-encoded String)         │
│  • If fnCall.name == "ask_bot" and (bot.isBlank() || request.isBlank()): │
│      - Do NOT call tabletMcpClient.callTool                              │
│      - Return function_result to Gemini:                                 │
│        {"ok":false,"error":{"code":"bad_arguments",                      │
│                             "msg":"ask_bot needs bot and request"}}      │
│  • Otherwise pass arguments unchanged to tabletMcpClient.callTool        │
└──────────────────────────────────────────────────────────────────────────┘
```

## 3. Key Changes

1. **Parse `arguments` as Either JSON Object or JSON-Encoded String (`GeminiInteractionsClient`)**:
   - Add a helper `parseArgumentsObject(raw: Any?): JSONObject?` that accepts:
     - A `JSONObject` directly.
     - A `String` (parsing it via `JSONObject(str)`, including unwrapping if it was double-encoded as a JSON string literal).
   - Check both `"arguments"` and `"args"` on the `function_call` object (as well as nested `"function_call"` objects if present).

2. **Preserve `function_call` Arguments Across SSE Stream Events (`GeminiInteractionsClient`)**:
   - In `content.start` / `step.start`: inspect `json.optJSONObject("step") ?: json.optJSONObject("content")`.
   - In `content.delta` / `step.delta`: handle `function_call` deltas by capturing `id`/`call_id`, `name`, and accumulating or merging `arguments` into `currentStepObj`.
   - In `content.stop` / `step.stop` and `finalizeStep`: inspect `json.optJSONObject("step") ?: json.optJSONObject("content") ?: json`, and allow non-empty `arguments` (and other updated fields) from `stopStep` to overwrite empty `{}` placeholders from `currentStep`.
   - In `onCompleted` and `extractFunctionCalls`: if a `function_call` with the same `id:name` appears again with non-empty arguments (e.g., in `interaction.completed` `outputs`), replace the earlier empty-arguments entry instead of dropping the populated one.

3. **Guard `ask_bot` Before Calling the Tablet (`RiviaViewModel`)**:
   - In the Labs chat `requires_action` handler, ensure `fnCall.arguments` is parsed (whether object or JSON-encoded string).
   - If `fnCall.name == "ask_bot"` and either `bot` or `request` is missing/blank after parsing:
     - Skip `tabletMcpClient.callTool` completely.
     - Send `{"ok":false,"error":{"code":"bad_arguments","msg":"ask_bot needs bot and request"}}` to Gemini as the `function_result`.
