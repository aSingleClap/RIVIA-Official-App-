package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlayerManager
import com.example.data.CachedModelEntity
import com.example.data.ChatEntity
import com.example.data.DefaultSettingsEntity
import com.example.data.MessageEntity
import com.example.data.RequestLogEntity
import com.example.data.RiviaDatabase
import com.example.model.ModePreset
import com.example.model.RunSettings
import com.example.network.ElevenLabsClient
import com.example.network.ElevenLabsModel
import com.example.network.ElevenLabsVoice
import com.example.network.GeminiInteractionsClient
import com.example.network.GeminiStreamEvent
import com.example.plugin.RiviaBridgeHolder
import com.example.plugin.RiviaChannel
import com.example.plugin.RiviaInternalBridge
import com.example.security.SecureKeyRepository
import com.example.security.VoicePreferences
import com.example.service.LiveSessionManager
import com.example.util.TextAudioTagProcessor
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class RiviaViewModel(application: Application) : AndroidViewModel(application) {
  private val db = RiviaDatabase.getInstance(application)
  private val dao = db.riviaDao()
  val keyRepository = SecureKeyRepository(application)
  val geminiClient = GeminiInteractionsClient()
  val elevenLabsClient = ElevenLabsClient()
  val audioPlayerManager = AudioPlayerManager(application, keyRepository, elevenLabsClient)

  // Search query in drawer
  private val _drawerSearchQuery = MutableStateFlow("")
  val drawerSearchQuery: StateFlow<String> = _drawerSearchQuery.asStateFlow()

  // All chats filtered by search query (newest first)
  val chats: StateFlow<List<ChatEntity>> =
    combine(dao.observeAllChats(), _drawerSearchQuery) { list, query ->
        if (query.isBlank()) {
          list
        } else {
          list.filter { it.title.contains(query.trim(), ignoreCase = true) }
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Active chat ID (null when in a fresh unsaved empty chat state)
  private val _activeChatId = MutableStateFlow<String?>(null)
  val activeChatId: StateFlow<String?> = _activeChatId.asStateFlow()

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  val activeChat: StateFlow<ChatEntity?> =
    _activeChatId
      .flatMapLatest { id -> if (id == null) flowOf(null) else dao.observeChatById(id) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  val activeMessages: StateFlow<List<MessageEntity>> =
    _activeChatId
      .flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.observeMessagesForChat(id)
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Default RunSettings for new chats
  val defaultRunSettings: StateFlow<RunSettings> =
    dao
      .observeDefaultSettings()
      .combine(flowOf(Unit)) { entity, _ ->
        entity?.let { RunSettings.fromJsonString(it.runSettingsJson) } ?: RunSettings()
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RunSettings())

  // Pending RunSettings when in a new chat that hasn't sent its first message yet
  private val _pendingNewChatSettings = MutableStateFlow<RunSettings?>(null)

  // Current effective RunSettings for the active chat
  val currentRunSettings: StateFlow<RunSettings> =
    combine(activeChat, _pendingNewChatSettings, defaultRunSettings) { chat, pending, def ->
        when {
          chat != null -> RunSettings.fromJsonString(chat.runSettingsJson)
          pending != null -> pending
          else -> def
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RunSettings())

  // Mode presets (Fast, Balanced, Deep)
  val modePresets: StateFlow<List<ModePreset>> =
    keyRepository.presetsJsonFlow
      .combine(flowOf(Unit)) { json, _ -> ModePreset.listFromJson(json) }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ModePreset.defaultPresets(),
      )

  // Keys & Voice preferences
  val geminiKey: StateFlow<String> =
    keyRepository.geminiKeyFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      "",
    )

  val elevenLabsKey: StateFlow<String> =
    keyRepository.elevenLabsKeyFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      "",
    )

  val voicePreferences: StateFlow<VoicePreferences> =
    keyRepository.voicePreferencesFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      VoicePreferences(),
    )

  // Cached Gemini models
  val cachedModels: StateFlow<List<CachedModelEntity>> =
    dao
      .observeCachedModels()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Request logs (last 100)
  val requestLogs: StateFlow<List<RequestLogEntity>> =
    dao
      .observeRequestLogs()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Streaming state
  private val _isStreaming = MutableStateFlow(false)
  val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

  private var streamingJob: Job? = null

  // ElevenLabs voices & models state for Settings > Voice
  private val _personalVoices = MutableStateFlow<List<ElevenLabsVoice>>(emptyList())
  val personalVoices: StateFlow<List<ElevenLabsVoice>> = _personalVoices.asStateFlow()

  private val _elevenModels = MutableStateFlow<List<ElevenLabsModel>>(emptyList())
  val elevenModels: StateFlow<List<ElevenLabsModel>> = _elevenModels.asStateFlow()

  // Plain API error / status banner state
  private val _statusBannerMessage = MutableStateFlow<String?>(null)
  val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

  private val _isErrorBanner = MutableStateFlow(false)
  val isErrorBanner: StateFlow<Boolean> = _isErrorBanner.asStateFlow()

  private val _isLoadingModels = MutableStateFlow(false)
  val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

  private val _isLoadingVoices = MutableStateFlow(false)
  val isLoadingVoices: StateFlow<Boolean> = _isLoadingVoices.asStateFlow()

  init {
    // Register internal entry point for Section 7 Plug-ins / Tasker bridge
    RiviaBridgeHolder.bridge =
      object : RiviaInternalBridge {
        override suspend fun askRivia(
          text: String,
          channel: RiviaChannel,
          requestId: String?,
        ): String {
          return executeDirectAsk(text, channel, requestId)
        }

        override suspend fun speakLine(text: String) {
          audioPlayerManager.speakMarkdownMessage(
            messageId = null,
            rawMarkdown = text,
            isLiveChannel = false,
          )
        }
      }

    // Wire LiveSessionManager to save each Live turn in the active chat with "voice · m:ss"
    LiveSessionManager.attachDependencies(
      context = application,
      playerManager = audioPlayerManager,
      keyRepo = keyRepository,
      liveTurnHandler = { userText, voiceLabel, _, onReplyDelta, onTurnComplete, onTurnError ->
        executeLiveVoiceTurnInChat(
          userText = userText,
          voiceDurationLabel = voiceLabel,
          onReplyDelta = onReplyDelta,
          onTurnComplete = onTurnComplete,
          onTurnError = onTurnError,
        )
      },
    )

    // Auto-select latest chat if one exists on launch
    viewModelScope.launch {
      val existing = dao.getAllChats()
      if (existing.isNotEmpty() && _activeChatId.value == null) {
        _activeChatId.value = existing.first().id
      }
    }
  }

  fun clearStatusBanner() {
    _statusBannerMessage.value = null
    audioPlayerManager.clearError()
  }

  fun setDrawerSearchQuery(query: String) {
    _drawerSearchQuery.value = query
  }

  fun startNewChat() {
    stopStreaming()
    _activeChatId.value = null
    _pendingNewChatSettings.value = defaultRunSettings.value
  }

  fun selectChat(chatId: String) {
    if (_activeChatId.value == chatId) return
    stopStreaming()
    _activeChatId.value = chatId
    _pendingNewChatSettings.value = null
  }

  fun renameChat(chatId: String, newTitle: String) {
    val trimmed = newTitle.trim()
    if (trimmed.isEmpty()) return
    viewModelScope.launch {
      val chat = dao.getChatById(chatId) ?: return@launch
      dao.updateChat(chat.copy(title = trimmed, updatedAt = System.currentTimeMillis()))
    }
  }

  fun deleteChat(chatId: String) {
    viewModelScope.launch {
      if (_activeChatId.value == chatId) {
        stopStreaming()
      }
      dao.deleteChatById(chatId)
      if (_activeChatId.value == chatId) {
        val remaining = dao.getAllChats()
        _activeChatId.value = remaining.firstOrNull()?.id
      }
    }
  }

  /**
   * Updates the RunSettings for the current chat.
   */
  fun updateCurrentChatSettings(updated: RunSettings) {
    viewModelScope.launch {
      val chatId = _activeChatId.value
      if (chatId != null) {
        val chat = dao.getChatById(chatId)
        if (chat != null) {
          dao.updateChat(chat.copy(runSettingsJson = updated.toJsonString()))
          return@launch
        }
      }
      _pendingNewChatSettings.value = updated
    }
  }

  fun applyModePreset(preset: ModePreset) {
    val current = currentRunSettings.value
    val updated =
      current.copy(
        model = preset.model,
        thinkingLevel = preset.thinkingLevel,
        activeModeName = preset.name,
      )
    updateCurrentChatSettings(updated)
  }

  fun updateModePresetDefinition(updatedPreset: ModePreset) {
    viewModelScope.launch {
      val currentList = modePresets.value.toMutableList()
      val idx = currentList.indexOfFirst { it.name == updatedPreset.name }
      if (idx >= 0) {
        currentList[idx] = updatedPreset
        keyRepository.savePresetsJson(ModePreset.listToJson(currentList))
        if (currentRunSettings.value.activeModeName == updatedPreset.name) {
          applyModePreset(updatedPreset)
        }
      }
    }
  }

  fun toggleAutoReadAloud() {
    val cur = currentRunSettings.value
    updateCurrentChatSettings(cur.copy(autoReadAloud = !cur.autoReadAloud))
  }

  fun saveCurrentSettingsAsDefault() {
    viewModelScope.launch {
      val cur = currentRunSettings.value
      dao.saveDefaultSettings(DefaultSettingsEntity(id = 1, runSettingsJson = cur.toJsonString()))
      showNotice("Saved as default for new chats.")
    }
  }

  fun updateDefaultSettings(settings: RunSettings) {
    viewModelScope.launch {
      dao.saveDefaultSettings(
        DefaultSettingsEntity(id = 1, runSettingsJson = settings.toJsonString())
      )
      if (_activeChatId.value == null) {
        _pendingNewChatSettings.value = settings
      }
    }
  }

  fun resetCurrentSettingsToDefault() {
    viewModelScope.launch {
      val def = dao.getDefaultSettings()?.let { RunSettings.fromJsonString(it.runSettingsJson) }
        ?: RunSettings()
      updateCurrentChatSettings(def)
      showNotice("Run settings reset to defaults.")
    }
  }

  fun stopStreaming() {
    geminiClient.cancelActiveRequest()
    streamingJob?.cancel()
    streamingJob = null
    _isStreaming.value = false
  }

  /**
   * Sends a new typed chat turn.
   */
  fun sendUserMessage(text: String) {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || _isStreaming.value) return

    streamingJob =
      viewModelScope.launch {
        val settings = currentRunSettings.value
        val chat = ensureActiveChatForFirstMessage(trimmed, settings)
        val priorMessages = dao.getMessagesForChat(chat.id)

        // Interaction ID before this turn
        val prevInteractionId =
          priorMessages
            .lastOrNull { it.role == "assistant" && !it.interactionId.isNullOrBlank() }
            ?.interactionId
            ?: chat.lastInteractionId

        val reqId = TextAudioTagProcessor.generateRequestId()
        val now = System.currentTimeMillis()
        val userMsg =
          MessageEntity(
            id = UUID.randomUUID().toString(),
            chatId = chat.id,
            role = "user",
            content = trimmed,
            requestId = reqId,
            previousInteractionId = prevInteractionId,
            model = settings.model,
            serviceTier = settings.serviceTier,
            status = "completed",
            createdAt = now,
          )
        dao.insertMessage(userMsg)

        executeAssistantTurn(
          chat = chat,
          settings = settings,
          channel = RiviaChannel.TYPED_CHAT,
          priorMessagesBeforeUserTurn = priorMessages,
          userText = trimmed,
          previousInteractionId = prevInteractionId,
          requestId = reqId,
        )
      }
  }

  /**
   * Section 2 & 3: Edit on my message resends from that point and replaces the later turns.
   * Branches from the interaction BEFORE the replaced turn, never the latest one!
   */
  fun editUserMessageAndResend(messageId: String, newText: String) {
    val trimmed = newText.trim()
    if (trimmed.isEmpty() || _isStreaming.value) return

    streamingJob =
      viewModelScope.launch {
        val targetMsg = dao.getMessageById(messageId) ?: return@launch
        val chat = dao.getChatById(targetMsg.chatId) ?: return@launch
        val settings = RunSettings.fromJsonString(chat.runSettingsJson)

        val allMessages = dao.getMessagesForChat(chat.id)
        val targetIdx = allMessages.indexOfFirst { it.id == messageId }
        if (targetIdx < 0) return@launch

        // Messages strictly before the edited user turn
        val messagesBeforeEditedTurn = allMessages.subList(0, targetIdx)
        // Branch interaction ID is the interaction strictly before this turn!
        val branchPreviousInteractionId =
          targetMsg.previousInteractionId
            ?: messagesBeforeEditedTurn
              .lastOrNull { it.role == "assistant" && !it.interactionId.isNullOrBlank() }
              ?.interactionId

        // Delete the edited message and all subsequent turns
        dao.deleteMessagesFromTimestamp(chat.id, targetMsg.createdAt)

        // Update chat title if editing the very first message
        val updatedChat =
          if (messagesBeforeEditedTurn.isEmpty()) {
            chat.copy(
              title = TextAudioTagProcessor.computeChatTitle(trimmed),
              lastInteractionId = branchPreviousInteractionId,
              updatedAt = System.currentTimeMillis(),
            )
          } else {
            chat.copy(
              lastInteractionId = branchPreviousInteractionId,
              updatedAt = System.currentTimeMillis(),
            )
          }
        dao.updateChat(updatedChat)

        val reqId = TextAudioTagProcessor.generateRequestId()
        val newUserMsg =
          MessageEntity(
            id = UUID.randomUUID().toString(),
            chatId = chat.id,
            role = "user",
            content = trimmed,
            requestId = reqId,
            previousInteractionId = branchPreviousInteractionId,
            model = settings.model,
            serviceTier = settings.serviceTier,
            status = "completed",
            createdAt = System.currentTimeMillis(),
          )
        dao.insertMessage(newUserMsg)

        executeAssistantTurn(
          chat = updatedChat,
          settings = settings,
          channel = RiviaChannel.TYPED_CHAT,
          priorMessagesBeforeUserTurn = messagesBeforeEditedTurn,
          userText = trimmed,
          previousInteractionId = branchPreviousInteractionId,
          requestId = reqId,
        )
      }
  }

  /**
   * Section 2 & 3: Regenerate (last reply only) branches from the interaction BEFORE the replaced turn!
   */
  fun regenerateLastReply() {
    if (_isStreaming.value) return
    val chatId = _activeChatId.value ?: return

    streamingJob =
      viewModelScope.launch {
        val chat = dao.getChatById(chatId) ?: return@launch
        val settings = RunSettings.fromJsonString(chat.runSettingsJson)
        val allMessages = dao.getMessagesForChat(chatId)
        val lastAssistantIdx = allMessages.indexOfLast { it.role == "assistant" }
        if (lastAssistantIdx < 0) return@launch

        val lastAssistantMsg = allMessages[lastAssistantIdx]
        val precedingUserIdx =
          allMessages.subList(0, lastAssistantIdx).indexOfLast { it.role == "user" }
        if (precedingUserIdx < 0) return@launch

        val userMsg = allMessages[precedingUserIdx]
        val priorMessagesBeforeUserTurn = allMessages.subList(0, precedingUserIdx)

        // Branch from the interaction BEFORE the replaced turn, never the latest one!
        val branchPreviousInteractionId =
          lastAssistantMsg.previousInteractionId
            ?: userMsg.previousInteractionId
            ?: priorMessagesBeforeUserTurn
              .lastOrNull { it.role == "assistant" && !it.interactionId.isNullOrBlank() }
              ?.interactionId

        // Delete the last assistant reply
        dao.deleteMessagesFromTimestamp(chatId, lastAssistantMsg.createdAt)
        val updatedChat = chat.copy(lastInteractionId = branchPreviousInteractionId)
        dao.updateChat(updatedChat)

        val newReqId = TextAudioTagProcessor.generateRequestId()
        executeAssistantTurn(
          chat = updatedChat,
          settings = settings,
          channel = RiviaChannel.TYPED_CHAT,
          priorMessagesBeforeUserTurn = priorMessagesBeforeUserTurn,
          userText = userMsg.content,
          previousInteractionId = branchPreviousInteractionId,
          requestId = newReqId,
        )
      }
  }

  private suspend fun ensureActiveChatForFirstMessage(
    firstUserMessage: String,
    settings: RunSettings,
  ): ChatEntity {
    val currentId = _activeChatId.value
    if (currentId != null) {
      val existing = dao.getChatById(currentId)
      if (existing != null) return existing
    }
    val newChatId = UUID.randomUUID().toString()
    val now = System.currentTimeMillis()
    val chat =
      ChatEntity(
        id = newChatId,
        title = TextAudioTagProcessor.computeChatTitle(firstUserMessage),
        createdAt = now,
        updatedAt = now,
        runSettingsJson = settings.toJsonString(),
        lastInteractionId = null,
      )
    dao.insertChat(chat)
    _activeChatId.value = newChatId
    _pendingNewChatSettings.value = null
    return chat
  }

  private suspend fun executeAssistantTurn(
    chat: ChatEntity,
    settings: RunSettings,
    channel: RiviaChannel,
    priorMessagesBeforeUserTurn: List<MessageEntity>,
    userText: String,
    previousInteractionId: String?,
    requestId: String,
    onDeltaCallback: ((String) -> Unit)? = null,
    onCompleteCallback: (() -> Unit)? = null,
    onErrorCallback: ((String) -> Unit)? = null,
  ) {
    _isStreaming.value = true
    val startTimeMs = System.currentTimeMillis()
    var firstTokenTimeMs: Long? = null
    val chosenModel =
      if (channel == RiviaChannel.LIVE_VOICE) settings.liveModel else settings.model

    val assistantMsgId = UUID.randomUUID().toString()
    var assistantMsg =
      MessageEntity(
        id = assistantMsgId,
        chatId = chat.id,
        role = "assistant",
        content = "",
        thoughtSummary = "",
        requestId = requestId,
        interactionId = null,
        previousInteractionId = previousInteractionId,
        model = chosenModel,
        serviceTier = settings.serviceTier,
        status = if (settings.store && settings.background) "polling" else "streaming",
        createdAt = startTimeMs + 1L,
      )
    dao.insertMessage(assistantMsg)

    val apiKey = keyRepository.getGeminiKey()

    try {
      geminiClient.sendTurn(
        apiKey = apiKey,
        settings = settings,
        channel = channel,
        priorMessages = priorMessagesBeforeUserTurn,
        newUserText = userText,
        previousInteractionId = previousInteractionId,
      ) { event ->
        when (event) {
          is GeminiStreamEvent.InteractionCreated -> {
            assistantMsg =
              assistantMsg.copy(
                interactionId = event.interactionId,
                serviceTier = event.serviceTierHeader ?: assistantMsg.serviceTier,
              )
            dao.updateMessage(assistantMsg)
          }
          is GeminiStreamEvent.TextDelta -> {
            if (firstTokenTimeMs == null) {
              firstTokenTimeMs = System.currentTimeMillis() - startTimeMs
            }
            assistantMsg =
              assistantMsg.copy(
                content = assistantMsg.content + event.text,
                ttftMs = firstTokenTimeMs ?: 0L,
              )
            dao.updateMessage(assistantMsg)
            onDeltaCallback?.invoke(event.text)
          }
          is GeminiStreamEvent.ThoughtSummaryDelta -> {
            if (firstTokenTimeMs == null) {
              firstTokenTimeMs = System.currentTimeMillis() - startTimeMs
            }
            assistantMsg =
              assistantMsg.copy(
                thoughtSummary = assistantMsg.thoughtSummary + event.thoughtText,
                ttftMs = firstTokenTimeMs ?: 0L,
              )
            dao.updateMessage(assistantMsg)
          }
          is GeminiStreamEvent.ReplaceReplyText -> {
            if (firstTokenTimeMs == null) {
              firstTokenTimeMs = System.currentTimeMillis() - startTimeMs
            }
            assistantMsg =
              assistantMsg.copy(
                content = event.fullText,
                thoughtSummary =
                  event.fullThought.ifBlank { assistantMsg.thoughtSummary },
                ttftMs = firstTokenTimeMs ?: 0L,
              )
            dao.updateMessage(assistantMsg)
          }
          is GeminiStreamEvent.StatusUpdate -> {
            assistantMsg = assistantMsg.copy(status = "polling (${event.status})")
            dao.updateMessage(assistantMsg)
          }
          is GeminiStreamEvent.StepCompleted -> {
            // Raw steps are saved in Completed
          }
          is GeminiStreamEvent.Completed -> {
            val totalMs = System.currentTimeMillis() - startTimeMs
            val finalInteractionId = event.interactionId ?: assistantMsg.interactionId
            assistantMsg =
              assistantMsg.copy(
                interactionId = finalInteractionId,
                serviceTier = event.serviceTier,
                inputTokens = event.inputTokens,
                outputTokens = event.outputTokens,
                thoughtTokens = event.thoughtTokens,
                ttftMs = firstTokenTimeMs ?: totalMs,
                totalTimeMs = totalMs,
                status = "completed",
                rawStepsJson = event.rawSteps.toString(),
              )
            dao.updateMessage(assistantMsg)

            val latestChat = dao.getChatById(chat.id)
            if (latestChat != null) {
              dao.updateChat(
                latestChat.copy(
                  lastInteractionId = finalInteractionId,
                  updatedAt = System.currentTimeMillis(),
                )
              )
            }

            // Log request to last 100 Request Log
            dao.logRequestAndPrune(
              RequestLogEntity(
                timestamp = System.currentTimeMillis(),
                requestId = requestId,
                model = chosenModel,
                status = "ok",
                serviceTier = event.serviceTier,
                inputTokens = event.inputTokens,
                outputTokens = event.outputTokens,
                thoughtTokens = event.thoughtTokens,
                latencyMs = totalMs,
                error = null,
              )
            )

            // If auto read-aloud is enabled in typed chat, speak the reply
            if (
              channel == RiviaChannel.TYPED_CHAT &&
                settings.autoReadAloud &&
                assistantMsg.content.isNotBlank()
            ) {
              audioPlayerManager.speakMarkdownMessage(
                messageId = assistantMsg.id,
                rawMarkdown = assistantMsg.content,
                isLiveChannel = false,
              )
            }
            onCompleteCallback?.invoke()
          }
          is GeminiStreamEvent.Error -> {
            val totalMs = System.currentTimeMillis() - startTimeMs
            val redactedErr = keyRepository.redact(event.message)
            assistantMsg =
              assistantMsg.copy(
                status = "error",
                errorMessage = redactedErr,
                totalTimeMs = totalMs,
              )
            dao.updateMessage(assistantMsg)

            dao.logRequestAndPrune(
              RequestLogEntity(
                timestamp = System.currentTimeMillis(),
                requestId = requestId,
                model = chosenModel,
                status = "error",
                serviceTier = assistantMsg.serviceTier,
                inputTokens = assistantMsg.inputTokens,
                outputTokens = assistantMsg.outputTokens,
                thoughtTokens = assistantMsg.thoughtTokens,
                latencyMs = totalMs,
                error = redactedErr,
              )
            )
            onErrorCallback?.invoke(redactedErr)
          }
        }
      }
    } catch (e: CancellationException) {
      val totalMs = System.currentTimeMillis() - startTimeMs
      assistantMsg =
        assistantMsg.copy(
          status = "completed",
          totalTimeMs = totalMs,
        )
      dao.updateMessage(assistantMsg)
      throw e
    } finally {
      _isStreaming.value = false
    }
  }

  /**
   * Executes a Live voice turn and saves both user ("voice · m:ss") and Rivia turns in the chat.
   */
  private suspend fun executeLiveVoiceTurnInChat(
    userText: String,
    voiceDurationLabel: String,
    onReplyDelta: (String) -> Unit,
    onTurnComplete: () -> Unit,
    onTurnError: (String) -> Unit,
  ) {
    val settings = currentRunSettings.value
    val chat = ensureActiveChatForFirstMessage(userText, settings)
    val priorMessages = dao.getMessagesForChat(chat.id)
    val prevInteractionId =
      priorMessages
        .lastOrNull { it.role == "assistant" && !it.interactionId.isNullOrBlank() }
        ?.interactionId
        ?: chat.lastInteractionId

    val reqId = TextAudioTagProcessor.generateRequestId()
    val userMsg =
      MessageEntity(
        id = UUID.randomUUID().toString(),
        chatId = chat.id,
        role = "user",
        content = userText,
        requestId = reqId,
        previousInteractionId = prevInteractionId,
        model = settings.liveModel,
        serviceTier = settings.serviceTier,
        status = "completed",
        voiceLabel = voiceDurationLabel,
        createdAt = System.currentTimeMillis(),
      )
    dao.insertMessage(userMsg)

    executeAssistantTurn(
      chat = chat,
      settings = settings,
      channel = RiviaChannel.LIVE_VOICE,
      priorMessagesBeforeUserTurn = priorMessages,
      userText = userText,
      previousInteractionId = prevInteractionId,
      requestId = reqId,
      onDeltaCallback = onReplyDelta,
      onCompleteCallback = onTurnComplete,
      onErrorCallback = onTurnError,
    )
  }

  private suspend fun executeDirectAsk(
    text: String,
    channel: RiviaChannel,
    customReqId: String?,
  ): String {
    val settings = currentRunSettings.value
    val chat = ensureActiveChatForFirstMessage(text, settings)
    val priorMessages = dao.getMessagesForChat(chat.id)
    val prevInteractionId = chat.lastInteractionId
    val reqId = customReqId ?: TextAudioTagProcessor.generateRequestId()
    val sb = StringBuilder()

    executeAssistantTurn(
      chat = chat,
      settings = settings,
      channel = channel,
      priorMessagesBeforeUserTurn = priorMessages,
      userText = text,
      previousInteractionId = prevInteractionId,
      requestId = reqId,
      onDeltaCallback = { sb.append(it) },
    )
    return sb.toString()
  }

  /**
   * Section 3 & 8: Refreshes Gemini model list and shows plain API result or error.
   */
  fun refreshGeminiModels(customKeyToSave: String? = null) {
    viewModelScope.launch {
      _isLoadingModels.value = true
      if (customKeyToSave != null) {
        keyRepository.saveGeminiKey(customKeyToSave)
      }
      val key = customKeyToSave?.trim() ?: keyRepository.getGeminiKey()
      val result = geminiClient.fetchModels(key)
      _isLoadingModels.value = false

      result.fold(
        onSuccess = { models ->
          dao.replaceCachedModels(models)
          showNotice("Loaded ${models.size} Gemini models.")
        },
        onFailure = { err ->
          val redacted = keyRepository.redact(err.message ?: "Failed to fetch Gemini models")
          showError(redacted)
        },
      )
    }
  }

  /**
   * Section 5 & 8: Refreshes ElevenLabs personal voices and TTS models.
   */
  fun refreshElevenLabsVoicesAndModels(customKeyToSave: String? = null) {
    viewModelScope.launch {
      _isLoadingVoices.value = true
      if (customKeyToSave != null) {
        keyRepository.saveElevenLabsKey(customKeyToSave)
      }
      val key = customKeyToSave?.trim() ?: keyRepository.getElevenLabsKey()
      val voicesRes = elevenLabsClient.fetchPersonalVoices(key)
      val modelsRes = elevenLabsClient.fetchTtsModels(key)
      _isLoadingVoices.value = false

      voicesRes.fold(
        onSuccess = { voices ->
          _personalVoices.value = voices
          modelsRes.onSuccess { models -> _elevenModels.value = models }
          // If no voice is selected yet and personal voices exist, select the first one and load its settings
          val curPrefs = voicePreferences.value
          if (curPrefs.voiceId.isBlank() && voices.isNotEmpty()) {
            selectElevenLabsVoice(voices.first())
          }
          showNotice("Loaded ${voices.size} personal voices from ElevenLabs.")
        },
        onFailure = { err ->
          val redacted = keyRepository.redact(err.message ?: "Failed to fetch ElevenLabs voices")
          showError(redacted)
        },
      )
    }
  }

  fun selectElevenLabsVoice(voice: ElevenLabsVoice) {
    viewModelScope.launch {
      val isProClone = voice.category.equals("professional", ignoreCase = true)
      keyRepository.updateVoicePreferences {
        it.copy(
          voiceId = voice.voiceId,
          voiceName = voice.name,
          voiceCategory = voice.category,
          isProfessionalClone = isProClone,
        )
      }
      // Fetch default voice settings from GET /v1/voices/{voice_id}/settings
      val key = keyRepository.getElevenLabsKey()
      if (key.isNotBlank()) {
        elevenLabsClient.fetchVoiceSettings(key, voice.voiceId).onSuccess { dto ->
          keyRepository.updateVoicePreferences { cur ->
            cur.copy(
              stability = dto.stability,
              similarityBoost = dto.similarityBoost,
              useSpeakerBoost = dto.useSpeakerBoost,
              style = dto.style,
              speed = dto.speed,
              cloneStrength = dto.cloneStrength ?: cur.cloneStrength,
            )
          }
        }
      }
    }
  }

  fun setCustomVoiceId(voiceId: String) {
    viewModelScope.launch {
      val trimmed = voiceId.trim()
      keyRepository.updateVoicePreferences {
        it.copy(voiceId = trimmed, voiceName = if (trimmed.isNotEmpty()) "Custom ($trimmed)" else "")
      }
      val key = keyRepository.getElevenLabsKey()
      if (key.isNotBlank() && trimmed.isNotEmpty()) {
        elevenLabsClient.fetchVoiceSettings(key, trimmed).onSuccess { dto ->
          keyRepository.updateVoicePreferences { cur ->
            cur.copy(
              stability = dto.stability,
              similarityBoost = dto.similarityBoost,
              useSpeakerBoost = dto.useSpeakerBoost,
              style = dto.style,
              speed = dto.speed,
              cloneStrength = dto.cloneStrength ?: cur.cloneStrength,
              isProfessionalClone = dto.cloneStrength != null,
            )
          }
        }
      }
    }
  }

  fun updateVoicePrefs(transform: (VoicePreferences) -> VoicePreferences) {
    viewModelScope.launch { keyRepository.updateVoicePreferences(transform) }
  }

  fun testElevenLabsSpeakLine() {
    val line =
      "[warmly] Hi Jordan, I'm Rivia. My voice stream and sentence pipeline are ready."
    audioPlayerManager.speakMarkdownMessage(
      messageId = "voice_test_line",
      rawMarkdown = line,
      isLiveChannel = false,
    )
  }

  /**
   * Builds redacted JSON preview for "Preview request" in Run Settings.
   */
  suspend fun buildRedactedRequestPreview(settings: RunSettings): String {
    val gKey = keyRepository.getGeminiKey()
    val eKey = keyRepository.getElevenLabsKey()
    val prevId = activeChat.value?.lastInteractionId
    return geminiClient.buildRedactedPreview(
      settings = settings,
      channel = RiviaChannel.TYPED_CHAT,
      previousInteractionId = prevId,
      geminiKey = gKey,
      elevenKey = eKey,
    )
  }

  /**
   * Section 8: Export all chats (and their messages + raw steps) to a JSON string.
   */
  suspend fun exportAllChatsToJson(): String {
    val allChats = dao.getAllChats()
    val chatsArray = JSONArray()
    for (chat in allChats) {
      val msgs = dao.getMessagesForChat(chat.id)
      val msgsArray = JSONArray()
      for (m in msgs) {
        msgsArray.put(
          JSONObject().apply {
            put("id", m.id)
            put("chatId", m.chatId)
            put("role", m.role)
            put("content", m.content)
            put("thoughtSummary", m.thoughtSummary)
            put("requestId", m.requestId)
            put("interactionId", m.interactionId ?: JSONObject.NULL)
            put("previousInteractionId", m.previousInteractionId ?: JSONObject.NULL)
            put("model", m.model)
            put("serviceTier", m.serviceTier)
            put("inputTokens", m.inputTokens)
            put("outputTokens", m.outputTokens)
            put("thoughtTokens", m.thoughtTokens)
            put("ttftMs", m.ttftMs)
            put("totalTimeMs", m.totalTimeMs)
            put("status", m.status)
            put("errorMessage", m.errorMessage ?: JSONObject.NULL)
            put("voiceLabel", m.voiceLabel ?: JSONObject.NULL)
            put("rawStepsJson", m.rawStepsJson)
            put("createdAt", m.createdAt)
          }
        )
      }
      chatsArray.put(
        JSONObject().apply {
          put("id", chat.id)
          put("title", chat.title)
          put("createdAt", chat.createdAt)
          put("updatedAt", chat.updatedAt)
          put("runSettingsJson", chat.runSettingsJson)
          put("lastInteractionId", chat.lastInteractionId ?: JSONObject.NULL)
          put("messages", msgsArray)
        }
      )
    }
    return JSONObject()
      .apply {
        put("version", 1)
        put("exportedAt", System.currentTimeMillis())
        put("chats", chatsArray)
      }
      .toString(2)
  }

  /**
   * Section 8: Import chats from a JSON string.
   */
  fun importChatsFromJson(jsonString: String) {
    viewModelScope.launch {
      try {
        val root = JSONObject(jsonString)
        val chatsArr = root.optJSONArray("chats") ?: JSONArray()
        var importedCount = 0
        for (i in 0 until chatsArr.length()) {
          val cObj = chatsArr.optJSONObject(i) ?: continue
          val chatId = cObj.optString("id").ifBlank { UUID.randomUUID().toString() }
          val chat =
            ChatEntity(
              id = chatId,
              title = cObj.optString("title", "Imported chat"),
              createdAt = cObj.optLong("createdAt", System.currentTimeMillis()),
              updatedAt = cObj.optLong("updatedAt", System.currentTimeMillis()),
              runSettingsJson = cObj.optString("runSettingsJson", RunSettings().toJsonString()),
              lastInteractionId =
                cObj.optString("lastInteractionId").takeIf {
                  it.isNotBlank() && it != "null"
                },
            )
          dao.insertChat(chat)

          val msgsArr = cObj.optJSONArray("messages") ?: JSONArray()
          val msgEntities = mutableListOf<MessageEntity>()
          for (j in 0 until msgsArr.length()) {
            val mObj = msgsArr.optJSONObject(j) ?: continue
            msgEntities.add(
              MessageEntity(
                id = mObj.optString("id").ifBlank { UUID.randomUUID().toString() },
                chatId = chatId,
                role = mObj.optString("role", "user"),
                content = mObj.optString("content", ""),
                thoughtSummary = mObj.optString("thoughtSummary", ""),
                requestId =
                  mObj.optString("requestId").ifBlank {
                    TextAudioTagProcessor.generateRequestId()
                  },
                interactionId =
                  mObj.optString("interactionId").takeIf {
                    it.isNotBlank() && it != "null"
                  },
                previousInteractionId =
                  mObj.optString("previousInteractionId").takeIf {
                    it.isNotBlank() && it != "null"
                  },
                model = mObj.optString("model", "gemini-3.8-flash"),
                serviceTier = mObj.optString("serviceTier", "standard"),
                inputTokens = mObj.optInt("inputTokens", 0),
                outputTokens = mObj.optInt("outputTokens", 0),
                thoughtTokens = mObj.optInt("thoughtTokens", 0),
                ttftMs = mObj.optLong("ttftMs", 0L),
                totalTimeMs = mObj.optLong("totalTimeMs", 0L),
                status = mObj.optString("status", "completed"),
                errorMessage =
                  mObj.optString("errorMessage").takeIf {
                    it.isNotBlank() && it != "null"
                  },
                voiceLabel =
                  mObj.optString("voiceLabel").takeIf {
                    it.isNotBlank() && it != "null"
                  },
                rawStepsJson = mObj.optString("rawStepsJson", "[]"),
                createdAt = mObj.optLong("createdAt", System.currentTimeMillis()),
              )
            )
          }
          dao.insertMessages(msgEntities)
          importedCount++
        }
        val firstChat = dao.getAllChats().firstOrNull()
        if (firstChat != null) {
          _activeChatId.value = firstChat.id
        }
        showNotice("Imported $importedCount chats.")
      } catch (e: Exception) {
        showError("Import failed: ${e.message}")
      }
    }
  }

  fun showError(message: String) {
    _isErrorBanner.value = true
    _statusBannerMessage.value = message
  }

  fun showNotice(message: String) {
    _isErrorBanner.value = false
    _statusBannerMessage.value = message
  }
}
