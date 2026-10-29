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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatEntity
import com.example.data.MessageEntity
import com.example.model.ModePreset
import com.example.model.RunSettings
import com.example.ui.components.MarkdownText
import com.example.ui.components.OrbState
import com.example.ui.components.RiviaOrb
import com.example.ui.components.StatusShape
import com.example.ui.components.StatusType
import com.example.ui.components.copyToClipboard
import com.example.ui.theme.ChivoFontFamily
import com.example.ui.theme.ChivoMonoFontFamily
import com.example.ui.theme.RiviaBackground
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaErrorRed
import com.example.ui.theme.RiviaMint
import com.example.ui.theme.RiviaMutedText
import com.example.ui.theme.RiviaRaisedSurface
import com.example.ui.theme.RiviaSurface
import com.example.ui.theme.RiviaText
import com.example.ui.theme.RiviaWarningAmber
import com.example.util.TextAudioTagProcessor
import com.example.viewmodel.RiviaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
  viewModel: RiviaViewModel,
  onOpenRunSettings: () -> Unit,
  onOpenGlobalSettings: () -> Unit,
  onOpenLiveMode: () -> Unit,
) {
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
  val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
  val isErrorBanner by viewModel.isErrorBanner.collectAsStateWithLifecycle()

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
            chats = chats,
            activeChatId = activeChat?.id,
            searchQuery = searchQuery,
            onSearchQueryChange = viewModel::setDrawerSearchQuery,
            onNewChat = { viewModel.startNewChat() },
            onSelectChat = { viewModel.selectChat(it.id) },
            onLongPressChat = { chatForContextMenu = it },
            onOpenGlobalSettings = onOpenGlobalSettings,
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
            chatTitle = activeChat?.title ?: "New chat",
            runSettings = runSettings,
            modePresets = modePresets,
            onSelectModePreset = viewModel::applyModePreset,
            onEditModePreset = { presetToEdit = it },
            onToggleAutoReadAloud = viewModel::toggleAutoReadAloud,
            onOpenRunSettings = onOpenRunSettings,
            onOpenGlobalSettings = onOpenGlobalSettings,
            onOpenLiveMode = onOpenLiveMode,
            missingKeys = geminiKey.isBlank() || elevenKey.isBlank(),
            statusBanner = audioError ?: statusBanner,
            isErrorBanner = audioError != null || isErrorBanner,
            onDismissBanner = viewModel::clearStatusBanner,
            messages = messages,
            isStreaming = isStreaming,
            isSpeaking = isSpeaking,
            speakingMessageId = speakingMsgId,
            sttLanguage = voicePrefs.sttLanguage,
            preferOnDeviceStt = voicePrefs.preferOnDeviceRecognition,
            onSendMessage = viewModel::sendUserMessage,
            onStopStreaming = viewModel::stopStreaming,
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
              onOpenGlobalSettings = {
                scope.launch { drawerState.close() }
                onOpenGlobalSettings()
              },
            )
          }
        },
      ) {
        ChatConversationPane(
          showDrawerButton = true,
          onOpenDrawer = { scope.launch { drawerState.open() } },
          chatTitle = activeChat?.title ?: "New chat",
          runSettings = runSettings,
          modePresets = modePresets,
          onSelectModePreset = viewModel::applyModePreset,
          onEditModePreset = { presetToEdit = it },
          onToggleAutoReadAloud = viewModel::toggleAutoReadAloud,
          onOpenRunSettings = onOpenRunSettings,
          onOpenGlobalSettings = onOpenGlobalSettings,
          onOpenLiveMode = onOpenLiveMode,
          missingKeys = geminiKey.isBlank() || elevenKey.isBlank(),
          statusBanner = audioError ?: statusBanner,
          isErrorBanner = audioError != null || isErrorBanner,
          onDismissBanner = viewModel::clearStatusBanner,
          messages = messages,
          isStreaming = isStreaming,
          isSpeaking = isSpeaking,
          speakingMessageId = speakingMsgId,
          sttLanguage = voicePrefs.sttLanguage,
          preferOnDeviceStt = voicePrefs.preferOnDeviceRecognition,
          onSendMessage = viewModel::sendUserMessage,
          onStopStreaming = viewModel::stopStreaming,
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

  // Details Dialog (Section 2: model, service tier, input/output/thought tokens, TTFT, total time, interaction ID, request ID)
  messageForDetails?.let { msg ->
    MessageDetailsDialog(
      message = msg,
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
                modifier =
                  Modifier.border(1.dp, RiviaBorder, RoundedCornerShape(8.dp))
                    .clickable { editThinking = lvl }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
              ) {
                Text(
                  text = if (lvl == "model_default") "default" else lvl,
                  color = if (selected) RiviaBackground else RiviaText,
                  fontFamily = ChivoMonoFontFamily,
                  fontSize = 12.sp,
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
  chats: List<ChatEntity>,
  activeChatId: String?,
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  onNewChat: () -> Unit,
  onSelectChat: (ChatEntity) -> Unit,
  onLongPressChat: (ChatEntity) -> Unit,
  onOpenGlobalSettings: () -> Unit,
) {
  val dateFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }

  Column(
    modifier = Modifier.fillMaxSize().padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Header + New chat button
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        RiviaOrb(state = OrbState.IDLE, size = 28.dp)
        Text(
          text = "Rivia",
          style = MaterialTheme.typography.titleLarge,
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("new_chat_button"),
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = "New chat",
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("New chat", fontWeight = FontWeight.SemiBold)
      }
    }

    // Search field
    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchQueryChange,
      placeholder = { Text("Search chats...", color = RiviaMutedText) },
      leadingIcon = {
        Icon(
          imageVector = Icons.Outlined.Search,
          contentDescription = "Search chats",
          tint = RiviaMutedText,
        )
      },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("drawer_search_input"),
      colors = riviaTextFieldColors(),
    )

    HorizontalDivider(color = RiviaBorder)

    // Chat list newest first
    if (chats.isEmpty()) {
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = if (searchQuery.isBlank()) "No conversations yet" else "No matching chats",
          style = MaterialTheme.typography.bodyMedium,
          color = RiviaMutedText,
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        itemsIndexed(chats, key = { _, item -> item.id }) { _, chat ->
          val isSelected = chat.id == activeChatId
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isSelected) RiviaRaisedSurface else RiviaSurface,
            modifier =
              Modifier.fillMaxWidth()
                .border(
                  width = 1.dp,
                  color = if (isSelected) RiviaMint.copy(alpha = 0.6f) else RiviaBorder,
                  shape = RoundedCornerShape(10.dp),
                )
                .combinedClickable(
                  onClick = { onSelectChat(chat) },
                  onLongClick = { onLongPressChat(chat) },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = chat.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = RiviaText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
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

    HorizontalDivider(color = RiviaBorder)

    // Bottom Settings button
    OutlinedButton(
      onClick = onOpenGlobalSettings,
      modifier =
        Modifier.fillMaxWidth()
          .minimumInteractiveComponentSize()
          .testTag("open_settings_button"),
      border = androidx.compose.foundation.BorderStroke(1.dp, RiviaBorder),
      colors = ButtonDefaults.outlinedButtonColors(contentColor = RiviaText),
    ) {
      Icon(
        imageVector = Icons.Outlined.Settings,
        contentDescription = "Settings",
        tint = RiviaMint,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text("Settings (Keys, Voice, Logs)")
    }
  }
}

@Composable
private fun ChatConversationPane(
  showDrawerButton: Boolean,
  onOpenDrawer: () -> Unit,
  chatTitle: String,
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
  onListenMessage: (MessageEntity) -> Unit,
  onRegenerateLast: () -> Unit,
  onShowDetails: (MessageEntity) -> Unit,
  onEditUserMessage: (MessageEntity) -> Unit,
) {
  var showPresetMenu by remember { mutableStateOf(false) }
  var composerText by rememberSaveable { mutableStateOf("") }
  val listState = rememberLazyListState()

  // Auto-scroll when messages arrive
  LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  Column(
    modifier = Modifier.fillMaxSize().imePadding()
  ) {
    // Section 2: Top bar: drawer button, chat title, mode chip (Fast, Balanced, Deep),
    // auto read-aloud toggle, and a gear that opens Run settings for this chat.
    Surface(
      color = RiviaSurface,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
          Text(
            text = chatTitle,
            style = MaterialTheme.typography.titleMedium,
            color = RiviaText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = runSettings.model,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = ChivoMonoFontFamily,
            color = RiviaMutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        // Mode chip (Fast, Balanced, Deep; editable presets)
        Box {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = RiviaRaisedSurface,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .border(1.dp, RiviaMint.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .clickable { showPresetMenu = true }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("mode_preset_chip"),
          ) {
            Row(
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

        // Auto read-aloud toggle
        IconButton(
          onClick = onToggleAutoReadAloud,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("auto_read_aloud_toggle"),
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

        // Gear button that opens Run settings for this chat
        IconButton(
          onClick = onOpenRunSettings,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("run_settings_gear_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = "Open Run settings for this chat",
            tint = RiviaText,
          )
        }
      }
    }

    HorizontalDivider(color = RiviaBorder)

    // Confirmed Decision 1: Setup Banner on first launch before keys are entered
    if (missingKeys) {
      Surface(
        color = RiviaRaisedSurface,
        modifier = Modifier.fillMaxWidth(),
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
          OutlinedButton(
            onClick = onOpenGlobalSettings,
            border = androidx.compose.foundation.BorderStroke(1.dp, RiviaMint),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("setup_banner_settings_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Key,
              contentDescription = "Open Settings",
              tint = RiviaMint,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Settings", color = RiviaMint, fontSize = 12.sp)
          }
        }
      }
      HorizontalDivider(color = RiviaBorder)
    }

    // Plain API Error / Status Banner
    if (!statusBanner.isNullOrBlank()) {
      Surface(
        color = RiviaRaisedSurface,
        modifier = Modifier.fillMaxWidth(),
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
      HorizontalDivider(color = RiviaBorder)
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
            text = "No messages yet. Type a message below, dictate with the mic, or launch hands-free Live voice mode.",
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
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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

    HorizontalDivider(color = RiviaBorder)

    // Section 2 Composer: "Message Rivia" field, mic button that dictates into the field
    // (Android SpeechRecognizer, partial results shown live), Send (or Stop while streaming), and Live button.
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
          topStart = 16.dp,
          topEnd = 4.dp,
          bottomStart = 16.dp,
          bottomEnd = 16.dp,
        ),
      color = RiviaRaisedSurface,
      modifier =
        Modifier.widthIn(max = 520.dp)
          .border(
            1.dp,
            RiviaBorder,
            RoundedCornerShape(
              topStart = 16.dp,
              topEnd = 4.dp,
              bottomStart = 16.dp,
              bottomEnd = 16.dp,
            ),
          )
          .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

    // Actions on my messages: Copy, and Edit
    Row(
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = { copyToClipboard(context, "Message", message.content) },
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(
          imageVector = Icons.Outlined.ContentCopy,
          contentDescription = "Copy my message",
          tint = RiviaMutedText,
          modifier = Modifier.size(16.dp),
        )
      }
      IconButton(
        onClick = onEdit,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(
          imageVector = Icons.Outlined.Edit,
          contentDescription = "Edit and resend message",
          tint = RiviaMutedText,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

@Composable
private fun AssistantMessageBubble(
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
      size = 32.dp,
      modifier = Modifier.padding(top = 4.dp),
    )
    Spacer(modifier = Modifier.width(10.dp))

    Column(
      modifier = Modifier.weight(1f).widthIn(max = 600.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      // Section 2: When thought summaries are on, they sit in a collapsed "Thinking" row above the reply.
      if (message.thoughtSummary.isNotBlank()) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = RiviaSurface,
          modifier =
            Modifier.fillMaxWidth()
              .border(1.dp, RiviaBorder, RoundedCornerShape(10.dp))
              .clickable { thinkingExpanded = !thinkingExpanded }
              .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier =
            Modifier.background(RiviaSurface, RoundedCornerShape(8.dp))
              .border(1.dp, RiviaBorder, RoundedCornerShape(8.dp))
              .padding(horizontal = 10.dp, vertical = 6.dp),
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

      // Main markdown reply
      if (message.content.isNotBlank()) {
        Surface(
          shape =
            RoundedCornerShape(
              topStart = 4.dp,
              topEnd = 16.dp,
              bottomStart = 16.dp,
              bottomEnd = 16.dp,
            ),
          color = RiviaSurface,
          modifier =
            Modifier.fillMaxWidth()
              .border(
                1.dp,
                RiviaBorder,
                RoundedCornerShape(
                  topStart = 4.dp,
                  topEnd = 16.dp,
                  bottomStart = 16.dp,
                  bottomEnd = 16.dp,
                ),
              )
              .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
          MarkdownText(rawText = message.content)
        }
      }

      // Plain API Error Display (Section 0: Show every failure plainly with the API's error message)
      if (!message.errorMessage.isNullOrBlank()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier =
            Modifier.fillMaxWidth()
              .background(RiviaRaisedSurface, RoundedCornerShape(10.dp))
              .border(1.dp, RiviaErrorRed, RoundedCornerShape(10.dp))
              .padding(12.dp),
        ) {
          StatusShape(status = StatusType.ERROR, size = 12.dp)
          Text(
            text = message.errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = RiviaErrorRed,
          )
        }
      }

      // Actions on Rivia's messages: Listen (tap again to stop), Copy, Share, Regenerate (last reply only), Details
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        IconButton(
          onClick = onListen,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector =
              if (isSpeaking) Icons.Default.Stop
              else Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription =
              if (isSpeaking) "Stop read-aloud" else "Listen with ElevenLabs",
            tint = if (isSpeaking) RiviaMint else RiviaMutedText,
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
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = "Copy reply",
            tint = RiviaMutedText,
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
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Outlined.Share,
            contentDescription = "Share reply",
            tint = RiviaMutedText,
            modifier = Modifier.size(16.dp),
          )
        }

        if (isLastAssistantReply && !isStreaming) {
          IconButton(
            onClick = onRegenerate,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = Icons.Outlined.Refresh,
              contentDescription = "Regenerate last reply",
              tint = RiviaMutedText,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        IconButton(
          onClick = onShowDetails,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "Reply details",
            tint = RiviaMutedText,
            modifier = Modifier.size(16.dp),
          )
        }

        Text(
          text = message.requestId,
          style = MaterialTheme.typography.labelSmall,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaMutedText,
          modifier = Modifier.padding(start = 4.dp),
        )
      }
    }
  }
}

@Composable
private fun MessageDetailsDialog(
  message: MessageEntity,
  onDismiss: () -> Unit,
) {
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
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailMonoRow("Request ID", message.requestId.ifBlank { "—" })
        DetailMonoRow("Interaction ID", message.interactionId ?: "— (stateless)")
        DetailMonoRow("Model", message.model.ifBlank { "—" })
        DetailMonoRow("Service Tier (Header)", message.serviceTier.ifBlank { "standard" })
        DetailMonoRow("Input Tokens", message.inputTokens.toString())
        DetailMonoRow("Output Tokens", message.outputTokens.toString())
        DetailMonoRow("Thought Tokens", message.thoughtTokens.toString())
        DetailMonoRow("Time to First Token", "${message.ttftMs} ms")
        DetailMonoRow("Total Time", "${message.totalTimeMs} ms")
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

  fun startDictation() {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) return
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
  }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        startDictation()
      }
    }

  Surface(
    color = RiviaSurface,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { Text("Message Rivia", color = RiviaMutedText) },
        modifier = Modifier.weight(1f).testTag("message_rivia_input"),
        maxLines = 5,
        shape = RoundedCornerShape(22.dp),
        colors = riviaTextFieldColors(),
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
          Modifier.minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(if (isDictating) RiviaMint.copy(alpha = 0.2f) else RiviaRaisedSurface)
            .testTag("dictate_mic_button"),
      ) {
        Icon(
          imageVector = Icons.Default.Mic,
          contentDescription = if (isDictating) "Stop dictation" else "Dictate into message field",
          tint = if (isDictating) RiviaMint else RiviaText,
        )
      }

      // Send or Stop button
      if (isStreaming) {
        IconButton(
          onClick = onStop,
          modifier =
            Modifier.minimumInteractiveComponentSize()
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
            Modifier.minimumInteractiveComponentSize()
              .clip(CircleShape)
              .background(if (text.isNotBlank()) RiviaMint else RiviaRaisedSurface)
              .testTag("send_message_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send message",
            tint = if (text.isNotBlank()) RiviaBackground else RiviaMutedText,
          )
        }
      }

      // Live button that opens Live mode
      IconButton(
        onClick = onOpenLive,
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .clip(CircleShape)
            .border(1.dp, RiviaMint, CircleShape)
            .background(RiviaRaisedSurface)
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

@Composable
fun riviaTextFieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedTextColor = RiviaText,
    unfocusedTextColor = RiviaText,
    focusedContainerColor = RiviaRaisedSurface,
    unfocusedContainerColor = RiviaRaisedSurface,
    focusedBorderColor = RiviaMint,
    unfocusedBorderColor = RiviaBorder,
    cursorColor = RiviaMint,
    focusedLabelColor = RiviaMint,
    unfocusedLabelColor = RiviaMutedText,
  )
