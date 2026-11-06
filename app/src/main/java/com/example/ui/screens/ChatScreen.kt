package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatEntity
import com.example.data.MessageEntity
import com.example.model.LayerCardUiMeta
import com.example.model.LayerConfigManager
import com.example.model.LoadedLayersBundle
import com.example.model.ModePreset
import com.example.model.ProfileTestState
import com.example.model.RunSettings
import com.example.ui.components.MarkdownText
import com.example.ui.components.OrbState
import com.example.ui.components.RiviaOrb
import com.example.ui.components.StatusShape
import com.example.ui.components.StatusType
import com.example.ui.components.copyToClipboard
import com.example.ui.theme.AppUiMode
import com.example.ui.theme.ChivoFontFamily
import com.example.ui.theme.ChivoMonoFontFamily
import com.example.ui.theme.RiviaBackground
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaErrorRed
import com.example.ui.theme.RiviaMint
import com.example.ui.theme.RiviaModeBlue
import com.example.ui.theme.RiviaModeMint
import com.example.ui.theme.RiviaModeViolet
import com.example.ui.theme.RiviaMutedText
import com.example.ui.theme.RiviaRaisedSurface
import com.example.ui.theme.RiviaSurface
import com.example.ui.theme.RiviaText
import com.example.ui.theme.RiviaWarningAmber
import com.example.ui.theme.accentColorForMode
import com.example.util.TextAudioTagProcessor
import com.example.viewmodel.RiviaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun ChatScreen(
  viewModel: RiviaViewModel,
  onOpenRunSettings: () -> Unit,
  onOpenGlobalSettings: () -> Unit,
  onOpenLiveMode: () -> Unit,
  onOpenPipeline: () -> Unit = {},
  onOpenLayers: (focusLayer: String?) -> Unit = {},
  onOpenTests: () -> Unit = {},
) {
  val appUiMode by viewModel.appUiMode.collectAsStateWithLifecycle()
  val chats by viewModel.chats.collectAsStateWithLifecycle()
  val activeChat by viewModel.activeChat.collectAsStateWithLifecycle()
  val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
  val runSettings by viewModel.currentRunSettings.collectAsStateWithLifecycle()
  val modePresets by viewModel.modePresets.collectAsStateWithLifecycle()
  val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
  val searchQuery by viewModel.drawerSearchQuery.collectAsStateWithLifecycle()
  val geminiKey by viewModel.geminiKey.collectAsStateWithLifecycle()
  val elevenKey by viewModel.elevenLabsKey.collectAsStateWithLifecycle()
  val voicePrefs by viewModel.voicePreferences.collectAsStateWithLifecycle()
  val isSpeaking by viewModel.audioPlayerManager.isSpeaking.collectAsStateWithLifecycle()
  val speakingMsgId by viewModel.audioPlayerManager.speakingMessageId.collectAsStateWithLifecycle()
  val audioError by viewModel.audioPlayerManager.lastAudioError.collectAsStateWithLifecycle()
  val audioNotice by viewModel.audioPlayerManager.lastAudioNotice.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
  val isErrorBanner by viewModel.isErrorBanner.collectAsStateWithLifecycle()
  val isActiveChatLab by viewModel.isActiveChatLab.collectAsStateWithLifecycle()
  val activeRiviaProfile by viewModel.activeRiviaProfile.collectAsStateWithLifecycle()
  val profileFingerprints by viewModel.profileFingerprints.collectAsStateWithLifecycle()
  val profileTestStates by viewModel.profileTestStates.collectAsStateWithLifecycle()
  val hasAnyLayerDraft by viewModel.hasAnyLayerDraft.collectAsStateWithLifecycle()
  val layersLoadError by viewModel.layersLoadError.collectAsStateWithLifecycle()
  val loadedBundle by viewModel.loadedLayersBundle.collectAsStateWithLifecycle()
  val cardMetas by viewModel.layerCardMetas.collectAsStateWithLifecycle()

  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val scope = rememberCoroutineScope()

  // Dialog states
  var messageForDetails by remember { mutableStateOf<MessageEntity?>(null) }
  var messageForEdit by remember { mutableStateOf<MessageEntity?>(null) }
  var editDraftText by remember { mutableStateOf("") }
  var chatForContextMenu by remember { mutableStateOf<ChatEntity?>(null) }
  var chatToRename by remember { mutableStateOf<ChatEntity?>(null) }
  var renameDraftText by remember { mutableStateOf("") }
  var chatToDelete by remember { mutableStateOf<ChatEntity?>(null) }
  var presetToEdit by remember { mutableStateOf<ModePreset?>(null) }
  var showRiviaChatInfoDialog by remember { mutableStateOf(false) }
  var showDevLabsModeSheet by remember { mutableStateOf(false) }

  // Galaxy Z Fold6 Adaptive Layout:
  // Cover screen (compact width < 600.dp) -> one pane with a drawer.
  // Unfolded (medium or expanded width >= 600.dp) -> chat list stays open beside the chat,
  // keeping content off the center hinge.
  BoxWithConstraints(
    modifier =
      Modifier.fillMaxSize()
        .background(RiviaBackground)
        .statusBarsPadding()
        .navigationBarsPadding()
  ) {
    val isUnfoldedOrExpanded = maxWidth >= 600.dp

    if (isUnfoldedOrExpanded) {
      Row(modifier = Modifier.fillMaxSize()) {
        // Persistent left chat list pane (kept clear of center hinge)
        Box(
          modifier =
            Modifier.width(292.dp)
              .fillMaxHeight()
              .background(RiviaSurface)
        ) {
          ChatDrawerContent(
            viewModel = viewModel,
            appUiMode = appUiMode,
            chats = chats,
            activeChatId = activeChat?.id,
            searchQuery = searchQuery,
            onSearchQueryChange = viewModel::setDrawerSearchQuery,
            onNewChat = { viewModel.startNewChat() },
            onSelectChat = { viewModel.selectChat(it.id) },
            onLongPressChat = { chatForContextMenu = it },
            onOpenTests = onOpenTests,
            onOpenLiveMode = onOpenLiveMode,
            onOpenPipeline = onOpenPipeline,
            onOpenLayers = { onOpenLayers(null) },
            onOpenGlobalSettings = onOpenGlobalSettings,
            onOpenDevLabsSheet = { showDevLabsModeSheet = true },
            onExitToRivia = { viewModel.setAppUiMode(AppUiMode.RIVIA) },
          )
        }
        // Hinge-safe divider gap
        VerticalDivider(color = RiviaBorder, thickness = 1.dp)
        Spacer(modifier = Modifier.width(8.dp))
        // Right conversation pane
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          ChatConversationPane(
            showDrawerButton = false,
            onOpenDrawer = {},
            appUiMode = appUiMode,
            onSelectUiMode = viewModel::setAppUiMode,
            chatTitle = activeChat?.title ?: "New chat",
            isLabChat = isActiveChatLab,
            activeRiviaProfile = activeRiviaProfile,
            loadedBundle = loadedBundle,
            profileTestStates = profileTestStates,
            hasAnyLayerDraft = hasAnyLayerDraft,
            onSelectRiviaProfile = viewModel::selectRiviaProfile,
            onOpenLayers = onOpenLayers,
            onOpenChatInfo = { showRiviaChatInfoDialog = true },
            runSettings = runSettings,
            modePresets = modePresets,
            onSelectModePreset = viewModel::applyModePreset,
            onEditModePreset = { presetToEdit = it },
            onToggleAutoReadAloud = viewModel::toggleAutoReadAloud,
            onOpenRunSettings = onOpenRunSettings,
            onOpenGlobalSettings = onOpenGlobalSettings,
            onOpenLiveMode = onOpenLiveMode,
            missingKeys = geminiKey.isBlank() || elevenKey.isBlank(),
            statusBanner =
              (if (!isActiveChatLab) layersLoadError else null)
                ?: audioError
                ?: audioNotice
                ?: statusBanner,
            isErrorBanner =
              (!isActiveChatLab && layersLoadError != null) ||
                audioError != null ||
                (audioNotice == null && isErrorBanner),
            onDismissBanner = viewModel::clearStatusBanner,
            messages = messages,
            isStreaming = isStreaming,
            isSpeaking = isSpeaking,
            speakingMessageId = speakingMsgId,
            sttLanguage = voicePrefs.sttLanguage,
            preferOnDeviceStt = voicePrefs.preferOnDeviceRecognition,
            onSendMessage = viewModel::sendUserMessage,
            onStopStreaming = viewModel::stopStreaming,
            onDictationError = viewModel::showError,
            onListenMessage = { msg ->
              viewModel.audioPlayerManager.speakMarkdownMessage(
                messageId = msg.id,
                rawMarkdown = msg.content,
                isLiveChannel = false,
              )
            },
            onRegenerateLast = viewModel::regenerateLastReply,
            onShowDetails = { messageForDetails = it },
            onEditUserMessage = { msg ->
              messageForEdit = msg
              editDraftText = msg.content
            },
          )
        }
      }
    } else {
      ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
          ModalDrawerSheet(
            drawerContainerColor = RiviaSurface,
            drawerContentColor = RiviaText,
            modifier = Modifier.width(300.dp),
          ) {
            ChatDrawerContent(
              viewModel = viewModel,
              appUiMode = appUiMode,
              chats = chats,
              activeChatId = activeChat?.id,
              searchQuery = searchQuery,
              onSearchQueryChange = viewModel::setDrawerSearchQuery,
              onNewChat = {
                viewModel.startNewChat()
                scope.launch { drawerState.close() }
              },
              onSelectChat = { chat ->
                viewModel.selectChat(chat.id)
                scope.launch { drawerState.close() }
              },
              onLongPressChat = { chatForContextMenu = it },
              onOpenTests = {
                scope.launch { drawerState.close() }
                onOpenTests()
              },
              onOpenLiveMode = {
                scope.launch { drawerState.close() }
                onOpenLiveMode()
              },
              onOpenPipeline = {
                scope.launch { drawerState.close() }
                onOpenPipeline()
              },
              onOpenLayers = {
                scope.launch { drawerState.close() }
                onOpenLayers(null)
              },
              onOpenGlobalSettings = {
                scope.launch { drawerState.close() }
                onOpenGlobalSettings()
              },
              onOpenDevLabsSheet = {
                scope.launch { drawerState.close() }
                showDevLabsModeSheet = true
              },
              onExitToRivia = {
                scope.launch { drawerState.close() }
                viewModel.setAppUiMode(AppUiMode.RIVIA)
              },
            )
          }
        },
      ) {
        ChatConversationPane(
          showDrawerButton = true,
          onOpenDrawer = { scope.launch { drawerState.open() } },
          appUiMode = appUiMode,
          onSelectUiMode = viewModel::setAppUiMode,
          chatTitle = activeChat?.title ?: "New chat",
          isLabChat = isActiveChatLab,
          activeRiviaProfile = activeRiviaProfile,
          loadedBundle = loadedBundle,
          profileTestStates = profileTestStates,
          hasAnyLayerDraft = hasAnyLayerDraft,
          onSelectRiviaProfile = viewModel::selectRiviaProfile,
          onOpenLayers = onOpenLayers,
          onOpenChatInfo = { showRiviaChatInfoDialog = true },
          runSettings = runSettings,
          modePresets = modePresets,
          onSelectModePreset = viewModel::applyModePreset,
          onEditModePreset = { presetToEdit = it },
          onToggleAutoReadAloud = viewModel::toggleAutoReadAloud,
          onOpenRunSettings = onOpenRunSettings,
          onOpenGlobalSettings = onOpenGlobalSettings,
          onOpenLiveMode = onOpenLiveMode,
          missingKeys = geminiKey.isBlank() || elevenKey.isBlank(),
          statusBanner =
            (if (!isActiveChatLab) layersLoadError else null)
              ?: audioError
              ?: audioNotice
              ?: statusBanner,
          isErrorBanner =
            (!isActiveChatLab && layersLoadError != null) ||
              audioError != null ||
              (audioNotice == null && isErrorBanner),
          onDismissBanner = viewModel::clearStatusBanner,
          messages = messages,
          isStreaming = isStreaming,
          isSpeaking = isSpeaking,
          speakingMessageId = speakingMsgId,
          sttLanguage = voicePrefs.sttLanguage,
          preferOnDeviceStt = voicePrefs.preferOnDeviceRecognition,
          onSendMessage = viewModel::sendUserMessage,
          onStopStreaming = viewModel::stopStreaming,
          onDictationError = viewModel::showError,
          onListenMessage = { msg ->
            viewModel.audioPlayerManager.speakMarkdownMessage(
              messageId = msg.id,
              rawMarkdown = msg.content,
              isLiveChannel = false,
            )
          },
          onRegenerateLast = viewModel::regenerateLastReply,
          onShowDetails = { messageForDetails = it },
          onEditUserMessage = { msg ->
            messageForEdit = msg
            editDraftText = msg.content
          },
        )
      }
    }
  }

  // Dev & Labs Mode Sheet (Dev = 600ms hold + haptic tick, Labs = normal tap)
  if (showDevLabsModeSheet) {
    DevLabsModeSheet(
      currentMode = appUiMode,
      onSelectMode = { mode ->
        showDevLabsModeSheet = false
        viewModel.setAppUiMode(mode)
      },
      onDismiss = { showDevLabsModeSheet = false },
    )
  }

  // Rivia Chat Info Dialog (Section 4)
  if (showRiviaChatInfoDialog) {
    RiviaChatInfoDialog(
      activeProfile = activeRiviaProfile,
      fingerprints = profileFingerprints,
      bundle = loadedBundle,
      cardMetas = cardMetas,
      viewModel = viewModel,
      onDismiss = { showRiviaChatInfoDialog = false },
    )
  }

  // Details Dialog (Section 2: model, service tier, input/output/thought tokens, TTFT, total time, interaction ID, request ID + Pass 2 emotion & capture status)
  messageForDetails?.let { msg ->
    MessageDetailsDialog(
      message = msg,
      viewModel = viewModel,
      onDismiss = { messageForDetails = null },
    )
  }

  // Edit User Message Dialog (Section 2: resends from that point and replaces later turns)
  messageForEdit?.let { msg ->
    AlertDialog(
      onDismissRequest = { messageForEdit = null },
      containerColor = RiviaRaisedSurface,
      titleContentColor = RiviaText,
      textContentColor = RiviaText,
      title = { Text("Edit & resend message") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Resending from this turn will replace subsequent turns in this chat.",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaMutedText,
          )
          OutlinedTextField(
            value = editDraftText,
            onValueChange = { editDraftText = it },
            modifier = Modifier.fillMaxWidth().testTag("edit_message_input"),
            minLines = 3,
            maxLines = 8,
            colors = riviaTextFieldColors(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val targetId = msg.id
            val updatedText = editDraftText
            messageForEdit = null
            viewModel.editUserMessageAndResend(targetId, updatedText)
          },
          colors = ButtonDefaults.buttonColors(containerColor = RiviaMint, contentColor = RiviaBackground),
          modifier = Modifier.testTag("confirm_edit_resend_button"),
        ) {
          Text("Resend")
        }
      },
      dismissButton = {
        TextButton(onClick = { messageForEdit = null }) {
          Text("Cancel", color = RiviaMutedText)
        }
      },
    )
  }

  // Long-press Chat Context Menu (Rename or Delete)
  chatForContextMenu?.let { chat ->
    AlertDialog(
      onDismissRequest = { chatForContextMenu = null },
      containerColor = RiviaRaisedSurface,
      titleContentColor = RiviaText,
      title = { Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          TextButton(
            onClick = {
              chatForContextMenu = null
              chatToRename = chat
              renameDraftText = chat.title
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(
              imageVector = Icons.Outlined.DriveFileRenameOutline,
              contentDescription = "Rename chat",
              tint = RiviaMint,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Rename chat", color = RiviaText, modifier = Modifier.weight(1f))
          }
          TextButton(
            onClick = {
              chatForContextMenu = null
              chatToDelete = chat
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(
              imageVector = Icons.Outlined.DeleteOutline,
              contentDescription = "Delete chat",
              tint = RiviaErrorRed,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Delete chat", color = RiviaErrorRed, modifier = Modifier.weight(1f))
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { chatForContextMenu = null }) {
          Text("Close", color = RiviaMutedText)
        }
      },
    )
  }

  // Rename Chat Dialog
  chatToRename?.let { chat ->
    AlertDialog(
      onDismissRequest = { chatToRename = null },
      containerColor = RiviaRaisedSurface,
      titleContentColor = RiviaText,
      title = { Text("Rename chat") },
      text = {
        OutlinedTextField(
          value = renameDraftText,
          onValueChange = { renameDraftText = it },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          colors = riviaTextFieldColors(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.renameChat(chat.id, renameDraftText)
            chatToRename = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = RiviaMint, contentColor = RiviaBackground),
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { chatToRename = null }) {
          Text("Cancel", color = RiviaMutedText)
        }
      },
    )
  }

  // Delete Chat Confirm Dialog
  chatToDelete?.let { chat ->
    AlertDialog(
      onDismissRequest = { chatToDelete = null },
      containerColor = RiviaRaisedSurface,
      titleContentColor = RiviaText,
      title = { Text("Delete chat?") },
      text = {
        Text(
          "Delete \"${chat.title}\" and all its turns? This cannot be undone.",
          color = RiviaMutedText,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteChat(chat.id)
            chatToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = RiviaErrorRed, contentColor = RiviaBackground),
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { chatToDelete = null }) {
          Text("Cancel", color = RiviaMutedText)
        }
      },
    )
  }

  // Edit Mode Preset Dialog (Fast, Balanced, Deep; all editable)
  presetToEdit?.let { preset ->
    var editModel by remember(preset) { mutableStateOf(preset.model) }
    var editThinking by remember(preset) { mutableStateOf(preset.thinkingLevel) }
    val allowedLevels = remember(editModel) { RunSettings.allowedThinkingLevels(editModel) }

    AlertDialog(
      onDismissRequest = { presetToEdit = null },
      containerColor = RiviaRaisedSurface,
      titleContentColor = RiviaText,
      title = { Text("Edit ${preset.name} Preset") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = editModel,
            onValueChange = { editModel = it },
            label = { Text("Model ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = riviaTextFieldColors(),
          )
          Text(
            text = "Thinking level",
            style = MaterialTheme.typography.labelLarge,
            color = RiviaMutedText,
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            allowedLevels.forEach { lvl ->
              val selected = editThinking == lvl
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selected) RiviaMint else RiviaSurface,
                border = BorderStroke(1.dp, if (selected) RiviaMint else RiviaBorder),
                modifier =
                  Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { editThinking = lvl },
              ) {
                Text(
                  text = if (lvl == "model_default") "default" else lvl,
                  color = if (selected) RiviaBackground else RiviaText,
                  fontFamily = ChivoMonoFontFamily,
                  fontSize = 12.sp,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.updateModePresetDefinition(
              ModePreset(
                name = preset.name,
                model = editModel.trim(),
                thinkingLevel = editThinking,
              )
            )
            presetToEdit = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = RiviaMint, contentColor = RiviaBackground),
        ) {
          Text("Save Preset")
        }
      },
      dismissButton = {
        TextButton(onClick = { presetToEdit = null }) {
          Text("Cancel", color = RiviaMutedText)
        }
      },
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatDrawerContent(
  viewModel: RiviaViewModel,
  appUiMode: AppUiMode,
  chats: List<ChatEntity>,
  activeChatId: String?,
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  onNewChat: () -> Unit,
  onSelectChat: (ChatEntity) -> Unit,
  onLongPressChat: (ChatEntity) -> Unit,
  onOpenTests: () -> Unit,
  onOpenLiveMode: () -> Unit,
  onOpenPipeline: () -> Unit,
  onOpenLayers: () -> Unit,
  onOpenGlobalSettings: () -> Unit,
  onOpenDevLabsSheet: () -> Unit = {},
  onExitToRivia: () -> Unit = {},
) {
  val dateFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }

  val modeHeaderTitle =
    when (appUiMode) {
      AppUiMode.RIVIA -> "Rivia"
      AppUiMode.DEV -> "Rivia · Dev"
      AppUiMode.LABS -> "Rivia · Labs"
    }

  val newChatButtonLabel =
    when (appUiMode) {
      AppUiMode.LABS -> "New Lab chat"
      else -> "New chat"
    }

  Column(
    modifier = Modifier.fillMaxSize().padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Header + "+ New chat" (scoped to current mode: Rivia chat in Rivia/Dev, Lab chat in Labs)
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        RiviaOrb(state = OrbState.IDLE, size = 24.dp)
        Text(
          text = modeHeaderTitle,
          style = MaterialTheme.typography.titleMedium,
          color = RiviaText,
        )
      }
      Button(
        onClick = onNewChat,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = RiviaMint,
            contentColor = RiviaBackground,
          ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("new_chat_button"),
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = newChatButtonLabel,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(newChatButtonLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
      }
    }

    // Search field
    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchQueryChange,
      placeholder = {
        Text(
          text =
            if (appUiMode == AppUiMode.LABS) "Search Labs chats..."
            else "Search Rivia chats...",
          color = RiviaMutedText,
        )
      },
      leadingIcon = {
        Icon(
          imageVector = Icons.Outlined.Search,
          contentDescription = "Search chats",
          tint = RiviaMutedText,
        )
      },
      singleLine = true,
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth().testTag("drawer_search_input"),
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedTextColor = RiviaText,
          unfocusedTextColor = RiviaText,
          focusedContainerColor = RiviaRaisedSurface,
          unfocusedContainerColor = RiviaRaisedSurface,
          focusedBorderColor = RiviaMint.copy(alpha = 0.5f),
          unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
          cursorColor = RiviaMint,
        ),
    )

    Spacer(modifier = Modifier.height(2.dp))

    // Chat list newest first
    if (chats.isEmpty()) {
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text =
            when {
              searchQuery.isNotBlank() -> "No matching chats"
              appUiMode == AppUiMode.LABS -> "No Labs conversations yet"
              else -> "No conversations yet"
            },
          style = MaterialTheme.typography.bodyMedium,
          color = RiviaMutedText,
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        itemsIndexed(chats, key = { _, item -> item.id }) { _, chat ->
          val isSelected = chat.id == activeChatId
          Surface(
            shape = RoundedCornerShape(14.dp),
            color =
              if (isSelected) RiviaMint.copy(alpha = 0.14f)
              else androidx.compose.ui.graphics.Color.Transparent,
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                  onClick = { onSelectChat(chat) },
                  onLongClick = { onLongPressChat(chat) },
                ),
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = chat.title,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                  color = if (isSelected) RiviaMint else RiviaText,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
                if (chat.isLabChat) {
                  Spacer(modifier = Modifier.width(6.dp))
                  Surface(
                    shape = RoundedCornerShape(50),
                    color = RiviaModeBlue.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, RiviaModeBlue.copy(alpha = 0.55f)),
                  ) {
                    Text(
                      text = "Labs",
                      color = RiviaModeBlue,
                      fontFamily = ChivoMonoFontFamily,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                  }
                }
              }
              Text(
                text = dateFormatter.format(Date(chat.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChivoMonoFontFamily,
                color = RiviaMutedText,
              )
            }
          }
        }
      }
    }

    // Labs-only Tests drawer item (opens the 7 Step 0 / Step 1 checks screen)
    if (appUiMode == AppUiMode.LABS) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = RiviaRaisedSurface,
        modifier =
          Modifier.fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenTests)
            .testTag("open_tests_button"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Science,
            contentDescription = "Tests",
            tint = RiviaMint,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Tests",
            style = MaterialTheme.typography.labelLarge,
            color = RiviaText,
          )
        }
      }
    }

    // Pipeline item directly above Layers (opens Coming Soon screen in all 3 modes)
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = RiviaRaisedSurface,
      modifier =
        Modifier.fillMaxWidth()
          .minimumInteractiveComponentSize()
          .clip(RoundedCornerShape(16.dp))
          .clickable(onClick = onOpenPipeline)
          .testTag("open_pipeline_button"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector = Icons.Outlined.AccountTree,
          contentDescription = "Pipeline",
          tint = RiviaMint,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Pipeline",
          style = MaterialTheme.typography.labelLarge,
          color = RiviaText,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
          shape = RoundedCornerShape(50),
          color = RiviaMint.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, RiviaMint.copy(alpha = 0.45f)),
        ) {
          Text(
            text = "Soon",
            color = RiviaMint,
            fontFamily = ChivoMonoFontFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
          )
        }
      }
    }

    // Layers item above Settings (visual Coming Soon in Rivia mode; full editor in Dev & Labs modes)
    Surface(
      shape = RoundedCornerShape(16.dp),
      color = RiviaRaisedSurface,
      modifier =
        Modifier.fillMaxWidth()
          .minimumInteractiveComponentSize()
          .clip(RoundedCornerShape(16.dp))
          .clickable(onClick = onOpenLayers)
          .testTag("open_layers_button"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector = Icons.Outlined.Tune,
          contentDescription = "Layers",
          tint = RiviaMint,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = if (appUiMode == AppUiMode.LABS) "Layers (Labs)" else "Layers",
          style = MaterialTheme.typography.labelLarge,
          color = RiviaText,
        )
        if (appUiMode == AppUiMode.RIVIA) {
          Spacer(modifier = Modifier.width(8.dp))
          Surface(
            shape = RoundedCornerShape(50),
            color = RiviaMint.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, RiviaMint.copy(alpha = 0.45f)),
          ) {
            Text(
              text = "Soon",
              color = RiviaMint,
              fontFamily = ChivoMonoFontFamily,
              fontSize = 9.sp,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
          }
        }
      }
    }

    // Settings button
    val settingsButtonLabel =
      when (appUiMode) {
        AppUiMode.RIVIA -> "Settings"
        AppUiMode.DEV -> "Settings (Keys, Voice, Internals)"
        AppUiMode.LABS -> "Settings (Labs Sandbox)"
      }

    Surface(
      shape = RoundedCornerShape(16.dp),
      color = RiviaRaisedSurface,
      modifier =
        Modifier.fillMaxWidth()
          .minimumInteractiveComponentSize()
          .clip(RoundedCornerShape(16.dp))
          .clickable(onClick = onOpenGlobalSettings)
          .testTag("open_settings_button"),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector = Icons.Outlined.Settings,
          contentDescription = "Settings",
          tint = RiviaMint,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = settingsButtonLabel,
          style = MaterialTheme.typography.labelLarge,
          color = RiviaText,
        )
      }
    }

    // Exit to Rivia action in Dev and Labs modes
    if (appUiMode != AppUiMode.RIVIA) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = RiviaRaisedSurface,
        border = BorderStroke(1.dp, RiviaMint.copy(alpha = 0.45f)),
        modifier =
          Modifier.fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onExitToRivia)
            .testTag("drawer_exit_to_rivia_button"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
            contentDescription = "Exit to Rivia",
            tint = RiviaMint,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Exit to Rivia",
            style = MaterialTheme.typography.labelMedium,
            color = RiviaMint,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }

    // Quiet "Dev & Labs" row at the very bottom of the chats drawer, below Settings
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .minimumInteractiveComponentSize()
          .clip(RoundedCornerShape(12.dp))
          .clickable(onClick = onOpenDevLabsSheet)
          .padding(horizontal = 12.dp, vertical = 6.dp)
          .testTag("drawer_dev_labs_button"),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Text(
        text = "Dev & Labs",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = ChivoMonoFontFamily,
        color = RiviaMutedText.copy(alpha = 0.75f),
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevLabsModeSheet(
  currentMode: AppUiMode,
  onSelectMode: (AppUiMode) -> Unit,
  onDismiss: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  var showDevHoldHint by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = RiviaRaisedSurface,
    contentColor = RiviaText,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Dev & Labs",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = RiviaText,
      )

      // Dev choice: requires 600 ms long press + haptic tick on unlock
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = RiviaSurface,
        border =
          BorderStroke(
            1.dp,
            if (currentMode == AppUiMode.DEV) RiviaModeViolet else RiviaBorder,
          ),
        modifier =
          Modifier.fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
              detectTapGestures(
                onPress = {
                  val releasedEarly = withTimeoutOrNull(600L) { tryAwaitRelease() } != null
                  if (!releasedEarly) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelectMode(AppUiMode.DEV)
                  } else {
                    showDevHoldHint = true
                  }
                }
              )
            }
            .testTag("sheet_choice_dev"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "Dev",
                style = MaterialTheme.typography.titleSmall,
                color = RiviaModeViolet,
                fontWeight = FontWeight.Bold,
              )
              Surface(
                shape = RoundedCornerShape(50),
                color = RiviaModeViolet.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, RiviaModeViolet.copy(alpha = 0.5f)),
              ) {
                Text(
                  text = "Hold 600ms",
                  color = RiviaModeViolet,
                  fontFamily = ChivoMonoFontFamily,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
              }
            }
            Text(
              text = "Edit Rivia's live layers, keys, voice, and internals.",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaMutedText,
            )
            if (showDevHoldHint) {
              Text(
                text = "Hold for 600ms to unlock Dev",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChivoMonoFontFamily,
                color = RiviaModeViolet,
                modifier = Modifier.padding(top = 4.dp).testTag("dev_hold_hint_text"),
              )
            }
          }
        }
      }

      // Labs choice: opens on a normal tap
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = RiviaSurface,
        border =
          BorderStroke(
            1.dp,
            if (currentMode == AppUiMode.LABS) RiviaModeBlue else RiviaBorder,
          ),
        modifier =
          Modifier.fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelectMode(AppUiMode.LABS) }
            .testTag("sheet_choice_labs"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = "Labs",
              style = MaterialTheme.typography.titleSmall,
              color = RiviaModeBlue,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Isolated sandbox for testing models, layers, voice, VAD, and Step 0/1 checks.",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaMutedText,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ChatConversationPane(
  showDrawerButton: Boolean,
  onOpenDrawer: () -> Unit,
  appUiMode: AppUiMode,
  onSelectUiMode: (AppUiMode) -> Unit,
  chatTitle: String,
  isLabChat: Boolean,
  activeRiviaProfile: String,
  loadedBundle: LoadedLayersBundle?,
  profileTestStates: Map<String, ProfileTestState>,
  hasAnyLayerDraft: Boolean,
  onSelectRiviaProfile: (String) -> Unit,
  onOpenLayers: (String?) -> Unit,
  onOpenChatInfo: () -> Unit,
  runSettings: RunSettings,
  modePresets: List<ModePreset>,
  onSelectModePreset: (ModePreset) -> Unit,
  onEditModePreset: (ModePreset) -> Unit,
  onToggleAutoReadAloud: () -> Unit,
  onOpenRunSettings: () -> Unit,
  onOpenGlobalSettings: () -> Unit,
  onOpenLiveMode: () -> Unit,
  missingKeys: Boolean,
  statusBanner: String?,
  isErrorBanner: Boolean,
  onDismissBanner: () -> Unit,
  messages: List<MessageEntity>,
  isStreaming: Boolean,
  isSpeaking: Boolean,
  speakingMessageId: String?,
  sttLanguage: String,
  preferOnDeviceStt: Boolean,
  onSendMessage: (String) -> Unit,
  onStopStreaming: () -> Unit,
  onDictationError: (String) -> Unit,
  onListenMessage: (MessageEntity) -> Unit,
  onRegenerateLast: () -> Unit,
  onShowDetails: (MessageEntity) -> Unit,
  onEditUserMessage: (MessageEntity) -> Unit,
) {
  var showPresetMenu by remember { mutableStateOf(false) }
  var composerText by rememberSaveable { mutableStateOf("") }
  val listState = rememberLazyListState()

  // Fix #12: Auto-scroll only when already at the bottom
  val isAtBottom by remember {
    androidx.compose.runtime.derivedStateOf {
      val layoutInfo = listState.layoutInfo
      val totalItems = layoutInfo.totalItemsCount
      if (totalItems == 0) {
        true
      } else {
        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
        lastVisible != null && lastVisible.index >= totalItems - 2
      }
    }
  }
  var prevMessageCount by remember { androidx.compose.runtime.mutableIntStateOf(messages.size) }

  LaunchedEffect(
    messages.size,
    messages.lastOrNull()?.content?.length,
    messages.lastOrNull()?.thoughtSummary?.length,
  ) {
    if (messages.isNotEmpty()) {
      val addedNewMessage = messages.size > prevMessageCount
      prevMessageCount = messages.size
      if (isAtBottom || (addedNewMessage && messages.last().role == "user")) {
        listState.scrollToItem(messages.lastIndex)
      }
    } else {
      prevMessageCount = 0
    }
  }

  val normRiviaProfile = LayerConfigManager.normalizeProfileKey(activeRiviaProfile)
  val displayedModelName =
    if (isLabChat) {
      runSettings.model
    } else {
      loadedBundle?.engine?.profiles?.get(normRiviaProfile)?.model ?: "gemini-3.8-flash"
    }

  Column(
    modifier = Modifier.fillMaxSize().imePadding()
  ) {
    // Clean modern Top Bar with 3-Mode Segmented Pill (Rivia | Dev | Labs)
    Surface(
      color = RiviaBackground,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (showDrawerButton) {
            IconButton(
              onClick = onOpenDrawer,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("drawer_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = "Open navigation drawer",
                tint = RiviaText,
              )
            }
          } else {
            Spacer(modifier = Modifier.width(8.dp))
          }

          Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = chatTitle,
                style = MaterialTheme.typography.titleMedium,
                color = RiviaText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
              )
              // In Dev and Labs, show a small mode tag in the top bar ("DEV" or "LABS"); Rivia mode shows no mode tag
              if (appUiMode != AppUiMode.RIVIA) {
                Surface(
                  shape = RoundedCornerShape(50),
                  color = RiviaMint.copy(alpha = 0.16f),
                  border = BorderStroke(1.dp, RiviaMint.copy(alpha = 0.6f)),
                  modifier = Modifier.testTag("top_bar_mode_tag"),
                ) {
                  Text(
                    text = if (appUiMode == AppUiMode.DEV) "DEV" else "LABS",
                    color = RiviaMint,
                    fontFamily = ChivoMonoFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                  )
                }
              }
              if (appUiMode != AppUiMode.RIVIA && hasAnyLayerDraft) {
                // Dev and Labs modes show a "Draft" chip in the top bar when any layer in that mode has phone edits
                Surface(
                  shape = RoundedCornerShape(50),
                  color = RiviaWarningAmber.copy(alpha = 0.16f),
                  border = BorderStroke(1.dp, RiviaWarningAmber.copy(alpha = 0.6f)),
                  modifier =
                    Modifier.clickable { onOpenLayers(null) }
                      .testTag("top_bar_draft_chip"),
                ) {
                  Text(
                    text = "Draft",
                    color = RiviaWarningAmber,
                    fontFamily = ChivoMonoFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                  )
                }
              }
            }
            if (appUiMode != AppUiMode.RIVIA) {
              Text(
                text = displayedModelName,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChivoMonoFontFamily,
                color = RiviaMutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          if (appUiMode == AppUiMode.RIVIA) {
            // Auto read-aloud toggle directly in the top bar for Rivia mode (no model/profile selector)
            IconButton(
              onClick = onToggleAutoReadAloud,
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("auto_read_aloud_toggle"),
            ) {
              Icon(
                imageVector =
                  if (runSettings.autoReadAloud) Icons.AutoMirrored.Outlined.VolumeUp
                  else Icons.AutoMirrored.Outlined.VolumeOff,
                contentDescription =
                  if (runSettings.autoReadAloud) "Disable auto read-aloud"
                  else "Enable auto read-aloud",
                tint = if (runSettings.autoReadAloud) RiviaMint else RiviaMutedText,
              )
            }
          }
        }

        // Secondary action strip in Top Bar (Dev & Labs only): Profile / Preset selector, Auto read-aloud, and Dev/Labs inspector buttons
        if (appUiMode != AppUiMode.RIVIA) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            if (appUiMode == AppUiMode.LABS) {
              // Labs Mode preset chip (Fast, Balanced, Deep)
              Box {
                Surface(
                  shape = RoundedCornerShape(18.dp),
                  color = RiviaMint.copy(alpha = 0.14f),
                  modifier =
                    Modifier.minimumInteractiveComponentSize()
                      .clip(RoundedCornerShape(18.dp))
                      .clickable { showPresetMenu = true }
                      .testTag("mode_preset_chip"),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    StatusShape(status = StatusType.OK, size = 7.dp)
                    Text(
                      text = runSettings.activeModeName,
                      style = MaterialTheme.typography.labelLarge,
                      color = RiviaMint,
                    )
                  }
                }

                DropdownMenu(
                  expanded = showPresetMenu,
                  onDismissRequest = { showPresetMenu = false },
                  modifier = Modifier.background(RiviaRaisedSurface),
                ) {
                  modePresets.forEach { preset ->
                    DropdownMenuItem(
                      text = {
                        Column {
                          Text(
                            text = preset.name,
                            color = RiviaText,
                            fontWeight = FontWeight.SemiBold,
                          )
                          Text(
                            text = "${preset.model} · ${preset.thinkingLevel}",
                            color = RiviaMutedText,
                            fontFamily = ChivoMonoFontFamily,
                            fontSize = 11.sp,
                          )
                        }
                      },
                      trailingIcon = {
                        IconButton(
                          onClick = {
                            showPresetMenu = false
                            onEditModePreset(preset)
                          },
                          modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                          Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit ${preset.name} preset",
                            tint = RiviaMutedText,
                            modifier = Modifier.size(16.dp),
                          )
                        }
                      },
                      onClick = {
                        showPresetMenu = false
                        onSelectModePreset(preset)
                      },
                    )
                  }
                }
              }
            } else {
              // Dev Mode chip: picks between Fast, Balanced, Deep, and Live (typed)
              val activeTestState =
                profileTestStates[normRiviaProfile] ?: ProfileTestState.NOT_RUN
              val (symbol, tint) = profileTestIndicator(activeTestState)

              Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                  Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = RiviaMint.copy(alpha = 0.14f),
                    modifier =
                      Modifier.minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { showPresetMenu = true }
                        .testTag("rivia_mode_chip"),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                      Text(
                        text = symbol,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                      )
                      Text(
                        text = LayerConfigManager.displayProfileLabel(activeRiviaProfile),
                        style = MaterialTheme.typography.labelLarge,
                        color = RiviaMint,
                      )
                    }
                  }

                  DropdownMenu(
                    expanded = showPresetMenu,
                    onDismissRequest = { showPresetMenu = false },
                    modifier = Modifier.background(RiviaRaisedSurface),
                  ) {
                    val riviaModes = listOf("fast", "balanced", "deep", "live_typed")
                    riviaModes.forEach { modeKey ->
                      val normKey = LayerConfigManager.normalizeProfileKey(modeKey)
                      val profCfg = loadedBundle?.engine?.profiles?.get(normKey)
                      val tState = profileTestStates[normKey] ?: ProfileTestState.NOT_RUN
                      val (sym, symColor) = profileTestIndicator(tState)

                      DropdownMenuItem(
                        text = {
                          Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                          ) {
                            Text(sym, color = symColor, fontWeight = FontWeight.Bold)
                            Column {
                              Text(
                                text = LayerConfigManager.displayProfileLabel(modeKey),
                                color = RiviaText,
                                fontWeight = FontWeight.SemiBold,
                              )
                              if (profCfg != null) {
                                Text(
                                  text = "${profCfg.model} · ${profCfg.thinking_level}",
                                  color = RiviaMutedText,
                                  fontFamily = ChivoMonoFontFamily,
                                  fontSize = 11.sp,
                                )
                              }
                            }
                          }
                        },
                        onClick = {
                          showPresetMenu = false
                          onSelectRiviaProfile(modeKey)
                        },
                      )
                    }
                  }
                }

                IconButton(
                  onClick = { onOpenLayers("engine") },
                  modifier =
                    Modifier.size(36.dp)
                      .minimumInteractiveComponentSize()
                      .testTag("rivia_mode_edit_engine_button"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit Engine profiles in Layers",
                    tint = RiviaMutedText,
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              // Auto read-aloud toggle
              IconButton(
                onClick = onToggleAutoReadAloud,
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("auto_read_aloud_toggle"),
              ) {
                Icon(
                  imageVector =
                    if (runSettings.autoReadAloud) Icons.AutoMirrored.Outlined.VolumeUp
                    else Icons.AutoMirrored.Outlined.VolumeOff,
                  contentDescription =
                    if (runSettings.autoReadAloud) "Disable auto read-aloud"
                    else "Enable auto read-aloud",
                  tint = if (runSettings.autoReadAloud) RiviaMint else RiviaMutedText,
                )
              }

              // Gear / Info button: shown in Dev (Chat info) and Labs (Run settings + Layers shortcut)
              if (appUiMode == AppUiMode.LABS) {
                IconButton(
                  onClick = { onOpenLayers(null) },
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("labs_top_bar_layers_button"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Science,
                    contentDescription = "Open Labs Sandbox Layers",
                    tint = RiviaMint,
                  )
                }
                IconButton(
                  onClick = onOpenRunSettings,
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("run_settings_gear_button"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = "Open Run settings for this Labs chat",
                    tint = RiviaText,
                  )
                }
              } else if (appUiMode == AppUiMode.DEV) {
                IconButton(
                  onClick = onOpenChatInfo,
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("run_settings_gear_button"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Open Chat info for this Rivia chat",
                    tint = RiviaText,
                  )
                }
              }
            }
          }
        }
      }
    }

    // Confirmed Decision 1: Setup Banner on first launch before keys are entered
    if (missingKeys) {
      Surface(
        color = RiviaSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
      ) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
          ) {
            StatusShape(status = StatusType.WARNING, size = 10.dp)
            Text(
              text = "Paste your Gemini and ElevenLabs API keys in Settings to get started.",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaText,
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = RiviaMint.copy(alpha = 0.16f),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                  if (appUiMode == AppUiMode.RIVIA) {
                    onSelectUiMode(AppUiMode.DEV)
                  }
                  onOpenGlobalSettings()
                }
                .testTag("setup_banner_settings_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = "Open Settings",
                tint = RiviaMint,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (appUiMode == AppUiMode.RIVIA) "Dev Settings" else "Settings",
                color = RiviaMint,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
      }
    }

    // Plain API Error / Status Banner
    if (!statusBanner.isNullOrBlank()) {
      Surface(
        color = RiviaSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
      ) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
          ) {
            StatusShape(
              status = if (isErrorBanner) StatusType.ERROR else StatusType.OK,
              size = 10.dp,
            )
            Text(
              text = statusBanner,
              style = MaterialTheme.typography.bodySmall,
              color = if (isErrorBanner) RiviaErrorRed else RiviaText,
            )
          }
          TextButton(onClick = onDismissBanner) {
            Text("Dismiss", color = RiviaMutedText, fontSize = 12.sp)
          }
        }
      }
    }

    // Message list or Empty state
    if (messages.isEmpty()) {
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          RiviaOrb(
            state = if (isSpeaking) OrbState.SPEAKING else OrbState.IDLE,
            size = 76.dp,
          )
          Text(
            text = "Rivia",
            style = MaterialTheme.typography.headlineMedium,
            color = RiviaText,
          )
          Text(
            text = "Ask anything below, tap the mic to dictate, or start a hands-free Live voice session.",
            style = MaterialTheme.typography.bodyMedium,
            color = RiviaMutedText,
            modifier = Modifier.widthIn(max = 360.dp),
          )
        }
      }
    } else {
      val lastAssistantId =
        remember(messages) { messages.lastOrNull { it.role == "assistant" }?.id }

      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        itemsIndexed(messages, key = { _, m -> m.id }) { _, msg ->
          if (msg.role == "user") {
            UserMessageBubble(
              message = msg,
              onEdit = { onEditUserMessage(msg) },
            )
          } else {
            val isThisSpeaking = isSpeaking && speakingMessageId == msg.id
            val isThisStreaming =
              isStreaming && (msg.status == "streaming" || msg.status.startsWith("polling"))
            AssistantMessageBubble(
              message = msg,
              isLastAssistantReply = msg.id == lastAssistantId,
              isSpeaking = isThisSpeaking,
              isStreaming = isThisStreaming,
              onListen = { onListenMessage(msg) },
              onRegenerate = onRegenerateLast,
              onShowDetails = { onShowDetails(msg) },
            )
          }
        }
      }
    }

    // Unified Floating Pill Composer Bar
    ChatComposerBar(
      text = composerText,
      onTextChange = { composerText = it },
      isStreaming = isStreaming,
      sttLanguage = sttLanguage,
      preferOnDeviceStt = preferOnDeviceStt,
      onSend = {
        val toSend = composerText
        composerText = ""
        onSendMessage(toSend)
      },
      onStop = onStopStreaming,
      onOpenLive = onOpenLiveMode,
      onDictationError = onDictationError,
    )
  }
}

@Composable
private fun UserMessageBubble(
  message: MessageEntity,
  onEdit: () -> Unit,
) {
  val context = LocalContext.current
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.End,
  ) {
    Surface(
      shape =
        RoundedCornerShape(
          topStart = 22.dp,
          topEnd = 22.dp,
          bottomStart = 22.dp,
          bottomEnd = 6.dp,
        ),
      color = RiviaMint.copy(alpha = 0.14f),
      modifier = Modifier.widthIn(max = 520.dp),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (!message.voiceLabel.isNullOrBlank()) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            StatusShape(status = StatusType.OK, size = 7.dp)
            Text(
              text = message.voiceLabel,
              style = MaterialTheme.typography.labelSmall,
              fontFamily = ChivoMonoFontFamily,
              color = RiviaMint,
            )
          }
        }
        Text(
          text = TextAudioTagProcessor.stripAudioTagsForScreen(message.content),
          style = MaterialTheme.typography.bodyLarge,
          color = RiviaText,
        )
      }
    }

    // Quiet actions on user messages: Copy and Edit
    Row(
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = { copyToClipboard(context, "Message", message.content) },
        modifier = Modifier.size(36.dp).minimumInteractiveComponentSize(),
      ) {
        Icon(
          imageVector = Icons.Outlined.ContentCopy,
          contentDescription = "Copy my message",
          tint = RiviaMutedText.copy(alpha = 0.75f),
          modifier = Modifier.size(15.dp),
        )
      }
      IconButton(
        onClick = onEdit,
        modifier = Modifier.size(36.dp).minimumInteractiveComponentSize(),
      ) {
        Icon(
          imageVector = Icons.Outlined.Edit,
          contentDescription = "Edit and resend message",
          tint = RiviaMutedText.copy(alpha = 0.75f),
          modifier = Modifier.size(15.dp),
        )
      }
    }
  }
}

@Composable
internal fun AssistantMessageBubble(
  message: MessageEntity,
  isLastAssistantReply: Boolean,
  isSpeaking: Boolean,
  isStreaming: Boolean,
  onListen: () -> Unit,
  onRegenerate: () -> Unit,
  onShowDetails: () -> Unit,
) {
  val context = LocalContext.current
  var thinkingExpanded by rememberSaveable(message.id) { mutableStateOf(false) }

  val orbState =
    when {
      isSpeaking -> OrbState.SPEAKING
      isStreaming -> OrbState.THINKING
      else -> OrbState.IDLE
    }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.Top,
  ) {
    RiviaOrb(
      state = orbState,
      size = 28.dp,
      modifier = Modifier.padding(top = 4.dp),
    )
    Spacer(modifier = Modifier.width(12.dp))

    Column(
      modifier = Modifier.weight(1f).widthIn(max = 640.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Soft tonal collapsible "Thinking" pill above the reply
      if (message.thoughtSummary.isNotBlank()) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = RiviaSurface,
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .clickable { thinkingExpanded = !thinkingExpanded },
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                StatusShape(status = StatusType.OK, size = 7.dp)
                Text(
                  text = "Thinking",
                  style = MaterialTheme.typography.labelLarge,
                  color = RiviaMutedText,
                )
              }
              Icon(
                imageVector =
                  if (thinkingExpanded) Icons.Outlined.ExpandLess
                  else Icons.Outlined.ExpandMore,
                contentDescription =
                  if (thinkingExpanded) "Collapse Thinking" else "Expand Thinking",
                tint = RiviaMutedText,
                modifier = Modifier.size(18.dp),
              )
            }
            AnimatedVisibility(visible = thinkingExpanded) {
              Text(
                text = message.thoughtSummary,
                style = MaterialTheme.typography.bodySmall,
                color = RiviaMutedText,
                modifier = Modifier.padding(top = 4.dp),
              )
            }
          }
        }
      }

      // Background polling progress indicator
      if (message.status.startsWith("polling")) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = RiviaSurface,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          ) {
            StatusShape(status = StatusType.WARNING, size = 8.dp)
            Text(
              text = "Background interaction ${message.status}...",
              style = MaterialTheme.typography.labelMedium,
              fontFamily = ChivoMonoFontFamily,
              color = RiviaWarningAmber,
            )
          }
        }
      }

      // Borderless full-width assistant markdown prose
      if (message.content.isNotBlank()) {
        MarkdownText(
          rawText = message.content,
          modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        )
      }

      // Plain API Error / H9 "No reply" Notice Display
      if (!message.errorMessage.isNullOrBlank()) {
        val isH9NoReplyNotice = message.errorMessage.startsWith("No reply")
        val statusColor = if (isH9NoReplyNotice) RiviaWarningAmber else RiviaErrorRed
        val statusShapeType = if (isH9NoReplyNotice) StatusType.WARNING else StatusType.ERROR
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = statusColor.copy(alpha = 0.12f),
          modifier =
            Modifier.fillMaxWidth()
              .testTag(if (isH9NoReplyNotice) "h9_no_reply_surface" else "error_message_surface"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(12.dp),
          ) {
            StatusShape(status = statusShapeType, size = 12.dp)
            Text(
              text = message.errorMessage,
              style = MaterialTheme.typography.bodySmall,
              color = statusColor,
              modifier =
                Modifier.testTag(
                  if (isH9NoReplyNotice) "h9_no_reply_notice" else "error_message_text"
                ),
            )
          }
        }
      }

      // Quiet action row below Rivia's reply
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        IconButton(
          onClick = onListen,
          modifier = Modifier.size(38.dp).minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector =
              if (isSpeaking) Icons.Default.Stop
              else Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription =
              if (isSpeaking) "Stop read-aloud" else "Listen with ElevenLabs",
            tint = if (isSpeaking) RiviaMint else RiviaMutedText.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp),
          )
        }

        IconButton(
          onClick = {
            copyToClipboard(
              context,
              "Rivia reply",
              TextAudioTagProcessor.stripAudioTagsForScreen(message.content),
            )
          },
          modifier = Modifier.size(38.dp).minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = "Copy reply",
            tint = RiviaMutedText.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp),
          )
        }

        IconButton(
          onClick = {
            val sendIntent =
              Intent(Intent.ACTION_SEND).apply {
                putExtra(
                  Intent.EXTRA_TEXT,
                  TextAudioTagProcessor.stripAudioTagsForScreen(message.content),
                )
                type = "text/plain"
              }
            context.startActivity(Intent.createChooser(sendIntent, "Share Rivia's reply"))
          },
          modifier = Modifier.size(38.dp).minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Outlined.Share,
            contentDescription = "Share reply",
            tint = RiviaMutedText.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp),
          )
        }

        if (isLastAssistantReply && !isStreaming) {
          IconButton(
            onClick = onRegenerate,
            modifier = Modifier.size(38.dp).minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = Icons.Outlined.Refresh,
              contentDescription = "Regenerate last reply",
              tint = RiviaMutedText.copy(alpha = 0.8f),
              modifier = Modifier.size(18.dp),
            )
          }
        }

        IconButton(
          onClick = onShowDetails,
          modifier = Modifier.size(38.dp).minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "Reply details",
            tint = RiviaMutedText.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp),
          )
        }

        Text(
          text = message.requestId,
          style = MaterialTheme.typography.labelSmall,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaMutedText.copy(alpha = 0.7f),
          modifier = Modifier.padding(start = 6.dp),
        )

        if (message.h7TrimmedFrom != null && message.h7TrimmedTo != null) {
          Spacer(modifier = Modifier.width(6.dp))
          Surface(
            shape = RoundedCornerShape(50),
            color = RiviaWarningAmber.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, RiviaWarningAmber.copy(alpha = 0.55f)),
            modifier = Modifier.testTag("h7_trimmed_chip"),
          ) {
            Text(
              text = "trimmed",
              color = RiviaWarningAmber,
              fontFamily = ChivoMonoFontFamily,
              fontSize = 10.sp,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RiviaChatInfoDialog(
  activeProfile: String,
  fingerprints: Map<String, String>,
  bundle: LoadedLayersBundle?,
  cardMetas: Map<String, LayerCardUiMeta>,
  viewModel: RiviaViewModel,
  onDismiss: () -> Unit,
) {
  val normProfile = LayerConfigManager.normalizeProfileKey(activeProfile)
  val fp = fingerprints[normProfile] ?: "--------"
  var showPreview by remember { mutableStateOf(false) }
  var previewChannel by remember {
    mutableStateOf(LayerConfigManager.channelForProfile(activeProfile))
  }
  var previewJson by remember { mutableStateOf("") }

  LaunchedEffect(showPreview, previewChannel) {
    if (showPreview) {
      previewJson = viewModel.buildRiviaRedactedRequestPreview(previewChannel)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = RiviaRaisedSurface,
    titleContentColor = RiviaText,
    title = { Text("Chat info") },
    text = {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        DetailMonoRow(
          "Profile",
          "${LayerConfigManager.displayProfileLabel(activeProfile)} ($normProfile)",
        )
        DetailMonoRow("Fingerprint", fp)

        if (bundle != null) {
          HorizontalDivider(color = RiviaBorder)
          Text(
            text = "Layer versions (* = phone draft)",
            style = MaterialTheme.typography.labelSmall,
            color = RiviaMutedText,
          )
          for (layerKey in listOf("persona", "style", "engine", "hard_rules")) {
            val meta = cardMetas[layerKey]
            val verText =
              if (meta != null) {
                "v${meta.version}${if (meta.hasDraft) "*" else ""} (${meta.status.label})"
              } else {
                "v1"
              }
            DetailMonoRow(layerKey, verText)
          }
          DetailMonoRow("tests", "v${bundle.tests.version}")
        }

        HorizontalDivider(color = RiviaBorder)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = { showPreview = !showPreview },
            modifier = Modifier.testTag("chat_info_preview_request_button"),
          ) {
            Text(if (showPreview) "Hide request" else "Preview request")
          }

          if (showPreview) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              for (ch in listOf("text", "voice")) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (previewChannel == ch) RiviaMint else RiviaSurface,
                  border =
                    BorderStroke(1.dp, if (previewChannel == ch) RiviaMint else RiviaBorder),
                  modifier =
                    Modifier.clip(RoundedCornerShape(8.dp))
                      .clickable { previewChannel = ch }
                      .testTag("chat_info_preview_channel_$ch"),
                ) {
                  Text(
                    text = if (ch == "text") "Text" else "Voice",
                    color = if (previewChannel == ch) RiviaBackground else RiviaText,
                    fontFamily = ChivoMonoFontFamily,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
              }
            }
          }
        }

        if (showPreview) {
          Surface(
            color = RiviaSurface,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            androidx.compose.foundation.text.selection.SelectionContainer {
              Text(
                text = previewJson,
                fontFamily = ChivoMonoFontFamily,
                fontSize = 11.sp,
                color = RiviaText,
                modifier = Modifier.padding(10.dp),
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = RiviaMint)
      }
    },
  )
}

@Composable
private fun MessageDetailsDialog(
  message: MessageEntity,
  viewModel: RiviaViewModel,
  onDismiss: () -> Unit,
) {
  var showRawSteps by remember { mutableStateOf(false) }
  val emotionLogFlow =
    remember(message.requestId) { viewModel.observeVoiceEmotionLogForRequest(message.requestId) }
  val emotionLog by emotionLogFlow.collectAsStateWithLifecycle(initialValue = null)

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = RiviaRaisedSurface,
    titleContentColor = RiviaText,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        StatusShape(
          status = if (message.status == "error") StatusType.ERROR else StatusType.OK,
          size = 10.dp,
        )
        Text("Turn Details")
      }
    },
    text = {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        DetailMonoRow("Request ID", message.requestId.ifBlank { "—" })
        DetailMonoRow("Interaction ID", message.interactionId ?: "— (stateless)")
        DetailMonoRow("Chat Kind", message.chatKind)
        DetailMonoRow("Profile", message.profile ?: "—")
        DetailMonoRow("Channel", message.channel.ifBlank { "text" })
        DetailMonoRow("Fingerprint", message.fingerprint ?: "—")
        DetailMonoRow("Model", message.model.ifBlank { "—" })
        DetailMonoRow("Service Tier (Header)", message.serviceTier.ifBlank { "standard" })
        DetailMonoRow("Input Tokens", message.inputTokens.toString())
        DetailMonoRow("Output Tokens", message.outputTokens.toString())
        DetailMonoRow("Thought Tokens", message.thoughtTokens.toString())
        DetailMonoRow("Time to First Token", "${message.ttftMs} ms")
        DetailMonoRow("Total Time", "${message.totalTimeMs} ms")
        if (message.h7TrimmedFrom != null && message.h7TrimmedTo != null) {
          DetailMonoRow(
            "H7 Spoken Limit",
            "H7: trimmed ${message.h7TrimmedFrom} → ${message.h7TrimmedTo}",
          )
        }
        if (!message.ruleEventsSummary.isNullOrBlank()) {
          DetailMonoRow("Rule Events", message.ruleEventsSummary)
        }

        // Pass 2: Voice Emotion (A/D/V to 2 decimals, speaking rate, inference ms, capture status, Mode B & VAD diagnostics)
        emotionLog?.let { emo ->
          HorizontalDivider(color = RiviaBorder)
          val rateStr =
            if (emo.speakingRateWps != null) "${"%.2f".format(Locale.US, emo.speakingRateWps)} w/s"
            else "—"
          val advStr =
            if (emo.arousal != null && emo.dominance != null && emo.valence != null) {
              "A: ${"%.2f".format(Locale.US, emo.arousal)} · D: ${"%.2f".format(Locale.US, emo.dominance)} · V: ${"%.2f".format(Locale.US, emo.valence)}"
            } else {
              "—"
            }
          DetailMonoRow("Emotion (A / D / V)", advStr)
          DetailMonoRow(
            "Speaking Rate (Silero)",
            "$rateStr (${emo.wordCount} w / ${"%.2f".format(Locale.US, emo.speechDurationSec)}s)${if (emo.isShortSpeech) " [SHORT]" else ""}",
          )
          DetailMonoRow(
            "Emotion Inference",
            if (emo.inferenceMs != null) "${emo.inferenceMs} ms (${emo.scoredWindowsCount} win)" else "—",
          )
          val capStatusText =
            if (emo.silentCapture) {
              "Mode ${emo.mode} · SILENT CAPTURE (RMS ${"%.4f".format(Locale.US, emo.rms)})"
            } else {
              "Mode ${emo.mode} · ${emo.captureStatus} (RMS ${"%.4f".format(Locale.US, emo.rms)}, Peak ${"%.4f".format(Locale.US, emo.peak)})"
            }
          DetailMonoRow("Capture Status", capStatusText)
          DetailMonoRow(
            "Source / Device",
            "${emo.audioSource.ifBlank { "VOICE_RECOGNITION" }} · ${emo.inputDevice.ifBlank { "unknown" }}",
          )
          DetailMonoRow(
            "AudioRecord / Pipe",
            "started=${if (emo.audioRecordStarted) "yes" else "no"} · ${emo.pipeBytesWritten} B",
          )
          val resolvedSource =
            emo.transcriptSource.ifBlank {
              if (emo.usedPartialFallback) "used partial" else "onResults"
            }
          val pipeToFinal =
            if (emo.pipeCloseToFinalMs != null) "${emo.pipeCloseToFinalMs} ms" else "—"
          DetailMonoRow(
            "Transcript Used / Pipe→Final",
            "$resolvedSource · $pipeToFinal",
          )
          DetailMonoRow("Final Text", emo.finalText.ifBlank { "—" })
          DetailMonoRow("Last Partial", emo.lastPartialText.ifBlank { "—" })
          DetailMonoRow(
            "Partials / Callbacks",
            "${emo.partialResultsCount} · ${emo.callbacksFired.ifBlank { "none" }}",
          )
          DetailMonoRow(
            "Silero VAD",
            "peak ${"%.2f".format(Locale.US, emo.vadMaxProb)} · speech ${"%.2f".format(Locale.US, emo.speechDurationSec)}s · ended=${if (emo.vadEndedTurn) "yes" else "no"}",
          )
          if (!emo.errorMessage.isNullOrBlank()) {
            DetailMonoRow("Capture / Model Note", emo.errorMessage)
          }
        }

        if (message.rawStepsJson.isNotBlank() && message.rawStepsJson != "[]") {
          OutlinedButton(
            onClick = { showRawSteps = !showRawSteps },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(if (showRawSteps) "Hide Raw Steps" else "Show Raw Steps")
          }
          if (showRawSteps) {
            Surface(
              color = RiviaSurface,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                  text = message.rawStepsJson,
                  fontFamily = ChivoMonoFontFamily,
                  fontSize = 10.sp,
                  color = RiviaText,
                  modifier = Modifier.padding(8.dp),
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = RiviaMint)
      }
    },
  )
}

@Composable
private fun DetailMonoRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = RiviaMutedText,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.labelMedium,
      fontFamily = ChivoMonoFontFamily,
      color = RiviaText,
    )
  }
}

@Composable
private fun ChatComposerBar(
  text: String,
  onTextChange: (String) -> Unit,
  isStreaming: Boolean,
  sttLanguage: String,
  preferOnDeviceStt: Boolean,
  onSend: () -> Unit,
  onStop: () -> Unit,
  onOpenLive: () -> Unit,
  onDictationError: (String) -> Unit,
) {
  val context = LocalContext.current
  var isDictating by remember { mutableStateOf(false) }
  var baseTextBeforeDictation by remember { mutableStateOf("") }
  var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

  DisposableEffect(Unit) {
    onDispose {
      speechRecognizer?.destroy()
      speechRecognizer = null
    }
  }

  fun speechErrorDescription(code: Int): String =
    when (code) {
      SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
      SpeechRecognizer.ERROR_CLIENT -> "Client speech error"
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient microphone permissions"
      SpeechRecognizer.ERROR_NETWORK -> "Network error"
      SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
      SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
      SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
      SpeechRecognizer.ERROR_SERVER -> "Speech server error"
      SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
      else -> "Speech error"
    }

  fun startDictation() {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
      onDictationError(
        "Speech recognition service is not available on this device."
      )
      return
    }
    try {
      speechRecognizer?.destroy()
      val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
      speechRecognizer = recognizer
      baseTextBeforeDictation = if (text.isBlank()) "" else "$text "
      isDictating = true

      recognizer.setRecognitionListener(
        object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {}

          override fun onBeginningOfSpeech() {}

          override fun onRmsChanged(rmsdB: Float) {}

          override fun onBufferReceived(buffer: ByteArray?) {}

          override fun onEndOfSpeech() {
            isDictating = false
          }

          override fun onError(error: Int) {
            isDictating = false
            onDictationError(
              "Dictation failed: ${speechErrorDescription(error)} (code $error)"
            )
          }

          override fun onPartialResults(partialResults: Bundle?) {
            val partial =
              partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (partial.isNotBlank()) {
              onTextChange(baseTextBeforeDictation + partial)
            }
          }

          override fun onResults(results: Bundle?) {
            val finalSpoken =
              results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (finalSpoken.isNotBlank()) {
              onTextChange(baseTextBeforeDictation + finalSpoken)
            }
            isDictating = false
          }

          override fun onEvent(eventType: Int, params: Bundle?) {}
        }
      )

      val intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
          )
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, sttLanguage)
          if (preferOnDeviceStt) {
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
          }
        }
      recognizer.startListening(intent)
    } catch (e: Exception) {
      isDictating = false
      onDictationError(
        "Could not start speech recognition: ${e.message ?: e.javaClass.simpleName}"
      )
    }
  }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        startDictation()
      } else {
        onDictationError("Microphone permission (RECORD_AUDIO) was denied for dictation.")
      }
    }

  // Single Unified Floating Pill Composer Bar
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .background(RiviaBackground)
        .padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = RiviaSurface,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        OutlinedTextField(
          value = text,
          onValueChange = onTextChange,
          placeholder = { Text("Message Rivia...", color = RiviaMutedText) },
          modifier = Modifier.weight(1f).testTag("message_rivia_input"),
          maxLines = 5,
          shape = RoundedCornerShape(24.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedTextColor = RiviaText,
              unfocusedTextColor = RiviaText,
              focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
              unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
              focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
              unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
              cursorColor = RiviaMint,
            ),
        )

        // Mic button for dictation into the field
        IconButton(
          onClick = {
            if (isDictating) {
              speechRecognizer?.stopListening()
              isDictating = false
            } else {
              val hasPerm =
                ContextCompat.checkSelfPermission(
                  context,
                  Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
              if (hasPerm) {
                startDictation()
              } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              }
            }
          },
          modifier =
            Modifier.size(40.dp)
              .minimumInteractiveComponentSize()
              .clip(CircleShape)
              .background(
                if (isDictating) RiviaMint.copy(alpha = 0.2f)
                else androidx.compose.ui.graphics.Color.Transparent
              )
              .testTag("dictate_mic_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = if (isDictating) "Stop dictation" else "Dictate into message field",
            tint = if (isDictating) RiviaMint else RiviaMutedText,
          )
        }

        // Send or Stop button inside unified pill
        if (isStreaming) {
          IconButton(
            onClick = onStop,
            modifier =
              Modifier.size(40.dp)
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(RiviaErrorRed)
                .testTag("stop_streaming_button"),
          ) {
            Icon(
              imageVector = Icons.Default.Stop,
              contentDescription = "Stop streaming reply",
              tint = RiviaBackground,
            )
          }
        } else {
          IconButton(
            onClick = onSend,
            enabled = text.isNotBlank(),
            modifier =
              Modifier.size(40.dp)
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(
                  if (text.isNotBlank()) RiviaMint
                  else RiviaRaisedSurface.copy(alpha = 0.6f)
                )
                .testTag("send_message_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = "Send message",
              tint = if (text.isNotBlank()) RiviaBackground else RiviaMutedText.copy(alpha = 0.6f),
              modifier = Modifier.size(18.dp),
            )
          }
        }

        // Live button inside unified pill
        IconButton(
          onClick = onOpenLive,
          modifier =
            Modifier.size(40.dp)
              .minimumInteractiveComponentSize()
              .clip(CircleShape)
              .background(RiviaMint.copy(alpha = 0.16f))
              .testTag("open_live_mode_button"),
        ) {
          Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = "Open hands-free Live voice mode",
            tint = RiviaMint,
          )
        }
      }
    }
  }
}

@Composable
fun riviaTextFieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedTextColor = RiviaText,
    unfocusedTextColor = RiviaText,
    focusedContainerColor = RiviaRaisedSurface,
    unfocusedContainerColor = RiviaRaisedSurface,
    focusedBorderColor = RiviaMint,
    unfocusedBorderColor = RiviaBorder.copy(alpha = 0.45f),
    cursorColor = RiviaMint,
    focusedLabelColor = RiviaMint,
    unfocusedLabelColor = RiviaMutedText,
  )
