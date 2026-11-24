package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ChatBubbleOutline
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
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatEntity
import com.example.data.MessageEntity
import com.example.model.LayerCardUiMeta
import com.example.model.LayerConfigManager
import com.example.model.LoadedLayersBundle
import com.example.model.ModePreset
import com.example.model.ProfileTestState
import com.example.model.RunSettings
import com.example.ui.components.LocalRiviaParticleFieldController
import com.example.ui.components.MarkdownText
import com.example.ui.components.OrbState
import com.example.ui.components.ParticleSceneMode
import com.example.ui.components.RiviaHomeParticleSphere
import com.example.ui.components.RiviaOrb
import com.example.ui.components.RiviaParticleFieldController
import com.example.ui.components.RiviaSharedSphereSlot
import com.example.ui.components.SimpleRectPx
import com.example.ui.components.StatusShape
import com.example.ui.components.StatusType
import com.example.ui.components.ViewportScale
import com.example.ui.components.copyToClipboard
import com.example.ui.components.isSystemRemoveAnimationsEnabled
import com.example.ui.theme.AppUiMode
import com.example.ui.theme.ChivoFontFamily
import com.example.ui.theme.ChivoMonoFontFamily
import com.example.ui.theme.RiviaBackground
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaErrorRed
import com.example.ui.theme.RiviaIconActive
import com.example.ui.theme.RiviaIconDefault
import com.example.ui.theme.RiviaMint
import com.example.ui.theme.RiviaModeBlue
import com.example.ui.theme.RiviaModeMint
import com.example.ui.theme.RiviaModeViolet
import com.example.ui.theme.RiviaModeWhite
import com.example.ui.theme.RiviaMutedText
import com.example.ui.theme.RiviaOnAccent
import com.example.ui.theme.RiviaRaisedSurface
import com.example.ui.theme.RiviaSurface
import com.example.ui.theme.RiviaSurface1
import com.example.ui.theme.RiviaSurface2
import com.example.ui.theme.RiviaSurface3
import com.example.ui.theme.RiviaText
import com.example.ui.theme.RiviaTextPrimary
import com.example.ui.theme.RiviaTextSecondary
import com.example.ui.theme.RiviaTextTertiary
import com.example.ui.theme.RiviaWarningAmber
import com.example.ui.theme.accentColorForMode
import com.example.service.LiveSessionManager
import com.example.service.LiveVoiceService
import com.example.util.TextAudioTagProcessor
import com.example.viewmodel.RiviaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
  onOpenBots: () -> Unit = {},
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
  val playbackAudioLevel by
    viewModel.audioPlayerManager.playbackAudioLevel.collectAsStateWithLifecycle()
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

  val particleController = LocalRiviaParticleFieldController.current
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val trustThumbs = remember { TrustThumbsState(viewModel, scope) }
  LaunchedEffect(trustThumbs) { trustThumbs.loadSwitches() }
  // Step 3.6: a copy of each finished typed reply of hers to the tablet, for the shadow rewording. Rivia text only.
  // Sent only while Layer 3 is on (layer switches).
  val replyCopy = remember { ReplyCopyState(viewModel, scope) }
  LaunchedEffect(messages, isActiveChatLab) { replyCopy.onMessages(messages, isActiveChatLab) }
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current

  // Dialog & selection states
  var messageForDetails by remember { mutableStateOf<MessageEntity?>(null) }
  var messageForEdit by remember { mutableStateOf<MessageEntity?>(null) }
  var editDraftText by remember { mutableStateOf("") }
  var selectedChatIds by remember { mutableStateOf(emptySet<String>()) }
  var showBulkDeleteConfirm by remember { mutableStateOf(false) }
  var chatToRename by remember { mutableStateOf<ChatEntity?>(null) }
  var renameDraftText by remember { mutableStateOf("") }
  var chatToDelete by remember { mutableStateOf<ChatEntity?>(null) }
  var presetToEdit by remember { mutableStateOf<ModePreset?>(null) }
  var showCreatePresetDialog by remember { mutableStateOf(false) }
  var showRiviaChatInfoDialog by remember { mutableStateOf(false) }
  var showDevLabsModeSheet by remember { mutableStateOf(false) }
  var newChatResetToken by rememberSaveable { mutableStateOf(0) }
  var isInLiveMode by rememberSaveable { mutableStateOf(false) }
  var didApplyOpenInLive by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(chats) {
    if (selectedChatIds.isNotEmpty()) {
      val validIds = chats.map { it.id }.toSet()
      val pruned = selectedChatIds.intersect(validIds)
      if (pruned != selectedChatIds) {
        selectedChatIds = pruned
      }
    }
  }

  BackHandler(enabled = selectedChatIds.isNotEmpty()) {
    selectedChatIds = emptySet()
  }

  LaunchedEffect(voicePrefs.openInLive, geminiKey, elevenKey) {
    if (!didApplyOpenInLive && geminiKey.isNotBlank() && elevenKey.isNotBlank()) {
      didApplyOpenInLive = true
      if (voicePrefs.openInLive) {
        isInLiveMode = true
      }
    }
  }

  // Close the keyboard whenever any drawer, menu, sheet, or dialog is opened
  LaunchedEffect(
    drawerState.isOpen,
    drawerState.targetValue,
    showDevLabsModeSheet,
    showRiviaChatInfoDialog,
    showCreatePresetDialog,
    presetToEdit,
    chatToDelete,
    showBulkDeleteConfirm,
    messageForDetails,
  ) {
    if (
      drawerState.isOpen ||
        drawerState.targetValue == DrawerValue.Open ||
        showDevLabsModeSheet ||
        showRiviaChatInfoDialog ||
        showCreatePresetDialog ||
        presetToEdit != null ||
        chatToDelete != null ||
        showBulkDeleteConfirm ||
        messageForDetails != null
    ) {
      keyboardController?.hide()
      focusManager.clearFocus()
    }
  }

  // Galaxy Z Fold6 Adaptive Layout:
  // Cover screen (compact width < 600.dp) -> one pane with a drawer.
  // Unfolded (medium or expanded width >= 600.dp) -> chat list stays open beside the chat,
  // keeping content off the center hinge.
  BoxWithConstraints(
    modifier =
      Modifier.fillMaxSize()
        .let { base ->
          if (particleController == null) base.background(RiviaBackground) else base
        }
        .statusBarsPadding()
        .navigationBarsPadding()
  ) {
    val isUnfoldedOrExpanded = maxWidth >= 600.dp

    if (isUnfoldedOrExpanded) {
      SideEffect {
        particleController?.updateWantDrawerOpen(open = false, removeAnimations = false)
      }
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
            selectedChatIds = selectedChatIds,
            onToggleSelectChat = { chat ->
              keyboardController?.hide()
              focusManager.clearFocus()
              selectedChatIds =
                if (chat.id in selectedChatIds) selectedChatIds - chat.id
                else selectedChatIds + chat.id
            },
            onSelectAllChats = {
              selectedChatIds = chats.map { it.id }.toSet()
            },
            onClearSelection = {
              selectedChatIds = emptySet()
            },
            onRenameSelectedChat = {
              if (selectedChatIds.size == 1) {
                val targetId = selectedChatIds.first()
                val targetChat = chats.firstOrNull { it.id == targetId }
                if (targetChat != null) {
                  keyboardController?.hide()
                  focusManager.clearFocus()
                  chatToRename = targetChat
                  renameDraftText = targetChat.title
                }
              }
            },
            onDeleteSelectedChats = {
              if (selectedChatIds.isNotEmpty()) {
                keyboardController?.hide()
                focusManager.clearFocus()
                showBulkDeleteConfirm = true
              }
            },
            onNewChat = {
              keyboardController?.hide()
              focusManager.clearFocus()
              newChatResetToken++
              viewModel.startNewChat()
            },
            onSelectChat = {
              keyboardController?.hide()
              focusManager.clearFocus()
              viewModel.selectChat(it.id)
            },
            onOpenTests = {
              keyboardController?.hide()
              focusManager.clearFocus()
              onOpenTests()
            },
            onOpenBots = {
              keyboardController?.hide()
              focusManager.clearFocus()
              onOpenBots()
            },
            onOpenLiveMode = {
              keyboardController?.hide()
              focusManager.clearFocus()
              isInLiveMode = true
            },
            onOpenPipeline = {
              keyboardController?.hide()
              focusManager.clearFocus()
              onOpenPipeline()
            },
            onOpenLayers = {
              keyboardController?.hide()
              focusManager.clearFocus()
              onOpenLayers(null)
            },
            onOpenRunSettings = {
              keyboardController?.hide()
              focusManager.clearFocus()
              onOpenRunSettings()
            },
            onOpenGlobalSettings = {
              keyboardController?.hide()
              focusManager.clearFocus()
              onOpenGlobalSettings()
            },
            onOpenDevLabsSheet = {
              keyboardController?.hide()
              focusManager.clearFocus()
              showDevLabsModeSheet = true
            },
          )
        }
        // Hinge-safe divider gap
        VerticalDivider(color = RiviaBorder, thickness = 1.dp)
        Spacer(modifier = Modifier.width(8.dp))
        // Right conversation pane
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          ChatConversationPane(
            thumbs = trustThumbs,
            activeChatId = activeChat?.id,
            newChatResetToken = newChatResetToken,
            showDrawerButton = false,
            onOpenDrawer = {},
            particleController = particleController,
            playbackAudioLevel = playbackAudioLevel,
            appUiMode = appUiMode,
            onSelectUiMode = viewModel::setAppUiMode,
            onOpenDevLabsSheet = {
              keyboardController?.hide()
              focusManager.clearFocus()
              showDevLabsModeSheet = true
            },
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
            onCreateModePreset = { showCreatePresetDialog = true },
            onEditModePreset = { presetToEdit = it },
            onDeleteModePreset = viewModel::deleteModePreset,
            onToggleAutoReadAloud = viewModel::toggleAutoReadAloud,
            onOpenRunSettings = onOpenRunSettings,
            onOpenGlobalSettings = onOpenGlobalSettings,
            isInLiveMode = isInLiveMode,
            keepScreenOnInLive = voicePrefs.keepScreenOnInLive,
            vibrateOnLiveChanges = voicePrefs.vibrateOnLiveChanges,
            liveTipSeen = voicePrefs.liveTipSeen,
            onDismissLiveTip = { viewModel.updateVoicePrefs { it.copy(liveTipSeen = true) } },
            onOpenLiveMode = { isInLiveMode = true },
            onEndLiveMode = { isInLiveMode = false },
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
            onSendMessage = { text ->
              // Step 6.3: a copy of your words to the tablet, Rivia chats only, only when it really sends.
              val sends = text.isNotBlank() && !isStreaming && !isActiveChatLab && layersLoadError == null
              val last = messages.lastOrNull { it.role == "assistant" && it.status == "completed" }
              val lastReply = last?.content
              // Step 7.1: when her reply finished (start plus how long it took), for your reply speed.
              val lastReplyAt = last?.let { it.createdAt + it.totalTimeMs.coerceAtLeast(0L) }
              viewModel.sendUserMessage(text)
              if (sends) trustThumbs.sendWords(text.trim(), lastReply, lastReplyAt)
            },
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
      val context = LocalContext.current
      val density = LocalDensity.current
      val lifecycleOwner = LocalLifecycleOwner.current
      var removeAnimations by remember { mutableStateOf(isSystemRemoveAnimationsEnabled(context)) }
      DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
          if (event == Lifecycle.Event.ON_RESUME) {
            removeAnimations = isSystemRemoveAnimationsEnabled(context)
          }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
      }

      val drawerCornerRadius = 22.dp
      val drawerCornerRadiusPx = with(density) { drawerCornerRadius.toPx() }
      val defaultDrawerWidthPx = with(density) { minOf(300.dp, maxWidth).toPx() }
      val defaultDrawerHeightPx = with(density) { maxHeight.toPx() }

      var measuredDrawerWidthPx by remember { mutableFloatStateOf(defaultDrawerWidthPx) }

      var wantDrawerOpen by remember { mutableStateOf(false) }
      var drawerRevealElapsedMs by remember { mutableFloatStateOf(0f) }
      val drawerCloseAlpha = remember { Animatable(0f) }

      SideEffect {
        particleController?.let { ctrl ->
          if (!ctrl.drawerWindowRect.isValid) {
            ctrl.drawerWindowRect =
              SimpleRectPx(
                left = 0f,
                top = ctrl.canvasOriginInWindow.y,
                width = defaultDrawerWidthPx,
                height = defaultDrawerHeightPx,
                cornerRadius = drawerCornerRadiusPx,
              )
          }
          ctrl.updateWantDrawerOpen(open = wantDrawerOpen, removeAnimations = removeAnimations)
        }
      }

      LaunchedEffect(drawerState.isClosed) {
        if (drawerState.isClosed && selectedChatIds.isNotEmpty()) {
          selectedChatIds = emptySet()
        }
      }

      LaunchedEffect(drawerState, removeAnimations) {
        var prevFrac = if (drawerState.currentValue == DrawerValue.Open) 1f else 0f
        var dragDir = 0
        snapshotFlow {
          Triple(drawerState.currentValue, drawerState.targetValue, drawerState.currentOffset)
        }.collect { (curVal, tgtVal, rawOffset) ->
          if (removeAnimations) {
            wantDrawerOpen = false
            return@collect
          }
          val w = measuredDrawerWidthPx.coerceAtLeast(1f)
          val frac =
            if (rawOffset.isNaN()) {
              if (curVal == DrawerValue.Open || tgtVal == DrawerValue.Open) 1f else 0f
            } else {
              ((w + rawOffset) / w).coerceIn(0f, 1f)
            }
          val delta = frac - prevFrac
          if (delta > 0.004f) dragDir = 1
          else if (delta < -0.004f) dragDir = -1
          if (frac <= 0.005f || frac >= 0.995f) dragDir = 0
          prevFrac = frac

          val desired =
            when {
              tgtVal == DrawerValue.Open && curVal == DrawerValue.Closed -> true
              tgtVal == DrawerValue.Closed && curVal == DrawerValue.Open -> false
              curVal == DrawerValue.Closed -> frac > 0.04f && dragDir >= 0
              else -> !(frac < 0.96f && dragDir < 0)
            }
          if (desired != wantDrawerOpen) {
            wantDrawerOpen = desired
          }
        }
      }

      LaunchedEffect(wantDrawerOpen, removeAnimations) {
        if (removeAnimations) {
          drawerRevealElapsedMs = 3000f
          drawerCloseAlpha.snapTo(1f)
          return@LaunchedEffect
        }
        if (wantDrawerOpen) {
          if (drawerCloseAlpha.value < 0.25f || drawerRevealElapsedMs <= 1f) {
            drawerRevealElapsedMs = 0f
            drawerCloseAlpha.snapTo(1f)
          } else {
            launch {
              drawerCloseAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
              )
            }
          }
          var lastNanos = -1L
          while (drawerRevealElapsedMs < 2400f) {
            withFrameNanos { nanos ->
              if (lastNanos >= 0L) {
                val dtMs = ((nanos - lastNanos).coerceAtLeast(0L)) / 1_000_000f
                drawerRevealElapsedMs += dtMs.coerceAtMost(100f)
              }
              lastNanos = nanos
            }
          }
        } else {
          if (drawerRevealElapsedMs > 0f) {
            drawerCloseAlpha.animateTo(
              targetValue = 0f,
              animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
            )
            drawerRevealElapsedMs = 0f
          } else {
            drawerCloseAlpha.snapTo(0f)
          }
        }
      }

      val requestOpenDrawer: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus()
        if (!removeAnimations) {
          wantDrawerOpen = true
          particleController?.updateWantDrawerOpen(open = true, removeAnimations = false)
        }
        scope.launch { drawerState.open() }
      }
      val requestCloseDrawer: () -> Unit = {
        wantDrawerOpen = false
        particleController?.updateWantDrawerOpen(open = false, removeAnimations = removeAnimations)
        scope.launch { drawerState.close() }
      }

      Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
          drawerState = drawerState,
          modifier = Modifier.fillMaxSize().zIndex(0f),
          drawerContent = {
            ModalDrawerSheet(
              drawerShape = RoundedCornerShape(drawerCornerRadius),
              drawerContainerColor = if (removeAnimations) RiviaSurface else Color.Transparent,
              drawerContentColor = RiviaText,
              drawerTonalElevation = 0.dp,
              modifier =
                Modifier.width(300.dp)
                  .onGloballyPositioned { coords ->
                    if (coords.size.width > 0 && coords.size.height > 0) {
                      val w = coords.size.width.toFloat()
                      val h = coords.size.height.toFloat()
                      measuredDrawerWidthPx = w
                      val winPos = coords.positionInWindow()
                      particleController?.let { ctrl ->
                        val nextRect =
                          SimpleRectPx(
                            left = 0f,
                            top = winPos.y,
                            width = w,
                            height = h,
                            cornerRadius = drawerCornerRadiusPx,
                          )
                        if (ctrl.drawerWindowRect != nextRect) {
                          ctrl.drawerWindowRect = nextRect
                          ctrl.requestRedraw()
                        }
                      }
                    }
                  }
                  .drawBehind {
                    if (!removeAnimations) {
                      val bgAlpha =
                        stagedDrawerAlpha(
                          elapsedMs = drawerRevealElapsedMs,
                          closeAlpha = drawerCloseAlpha.value,
                          startMs = 700f,
                          durationMs = 180f,
                        )
                      if (bgAlpha > 0.002f) {
                        drawRoundRect(
                          color = RiviaSurface.copy(alpha = bgAlpha),
                          cornerRadius = CornerRadius(drawerCornerRadiusPx, drawerCornerRadiusPx),
                        )
                      }
                    }
                  },
            ) {
              ChatDrawerContent(
                viewModel = viewModel,
                appUiMode = appUiMode,
                chats = chats,
                activeChatId = activeChat?.id,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::setDrawerSearchQuery,
                selectedChatIds = selectedChatIds,
                onToggleSelectChat = { chat ->
                  keyboardController?.hide()
                  focusManager.clearFocus()
                  selectedChatIds =
                    if (chat.id in selectedChatIds) selectedChatIds - chat.id
                    else selectedChatIds + chat.id
                },
                onSelectAllChats = {
                  selectedChatIds = chats.map { it.id }.toSet()
                },
                onClearSelection = {
                  selectedChatIds = emptySet()
                },
                onRenameSelectedChat = {
                  if (selectedChatIds.size == 1) {
                    val targetId = selectedChatIds.first()
                    val targetChat = chats.firstOrNull { it.id == targetId }
                    if (targetChat != null) {
                      keyboardController?.hide()
                      focusManager.clearFocus()
                      chatToRename = targetChat
                      renameDraftText = targetChat.title
                    }
                  }
                },
                onDeleteSelectedChats = {
                  if (selectedChatIds.isNotEmpty()) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    showBulkDeleteConfirm = true
                  }
                },
                onNewChat = {
                  keyboardController?.hide()
                  focusManager.clearFocus()
                  newChatResetToken++
                  viewModel.startNewChat()
                  requestCloseDrawer()
                },
                onSelectChat = { chat ->
                  keyboardController?.hide()
                  focusManager.clearFocus()
                  viewModel.selectChat(chat.id)
                  requestCloseDrawer()
                },
                onOpenTests = {
                  keyboardController?.hide()
                  focusManager.clearFocus()
                  requestCloseDrawer()
                  onOpenTests()
                },
                onOpenBots = {
                  keyboardController?.hide()
                  focusManager.clearFocus()
                  requestCloseDrawer()
                  onOpenBots()
                },
                onOpenLiveMode = {
                  keyboardController?.hide()
                  focusManager.clearFocus()
                  requestCloseDrawer()
                  isInLiveMode = true
                },
                onOpenPipeline = {
                  keyboardController?.hide()
                  focusManager.clearFocus()
                  requestCloseDrawer()
                  onOpenPipeline()
                },
                onOpenLayers = {
                  keyboardController?.hide()
                  focusManager.clearFocus()
                  requestCloseDrawer()
                  onOpenLayers(null)
                },
                onOpenRunSettings = {
                  keyboardController?.hide()
                  focusManager.clearFocus()
                  requestCloseDrawer()
                  onOpenRunSettings()
                },
                onOpenGlobalSettings = {
                  keyboardController?.hide()
                  focusManager.clearFocus()
                  requestCloseDrawer()
                  onOpenGlobalSettings()
                },
                onOpenDevLabsSheet = {
                  keyboardController?.hide()
                  focusManager.clearFocus()
                  requestCloseDrawer()
                  showDevLabsModeSheet = true
                },
                revealElapsedMsProvider = if (removeAnimations) null else ({ drawerRevealElapsedMs }),
                closeAlphaProvider = if (removeAnimations) null else ({ drawerCloseAlpha.value }),
              )
            }
          },
        ) {
          ChatConversationPane(
            thumbs = trustThumbs,
            activeChatId = activeChat?.id,
            newChatResetToken = newChatResetToken,
            showDrawerButton = true,
            onOpenDrawer = requestOpenDrawer,
            particleController = particleController,
            playbackAudioLevel = playbackAudioLevel,
          appUiMode = appUiMode,
          onSelectUiMode = viewModel::setAppUiMode,
          onOpenDevLabsSheet = {
            keyboardController?.hide()
            focusManager.clearFocus()
            showDevLabsModeSheet = true
          },
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
          onCreateModePreset = { showCreatePresetDialog = true },
          onEditModePreset = { presetToEdit = it },
          onDeleteModePreset = viewModel::deleteModePreset,
          onToggleAutoReadAloud = viewModel::toggleAutoReadAloud,
          onOpenRunSettings = onOpenRunSettings,
          onOpenGlobalSettings = onOpenGlobalSettings,
          isInLiveMode = isInLiveMode,
          keepScreenOnInLive = voicePrefs.keepScreenOnInLive,
          vibrateOnLiveChanges = voicePrefs.vibrateOnLiveChanges,
          liveTipSeen = voicePrefs.liveTipSeen,
          onDismissLiveTip = { viewModel.updateVoicePrefs { it.copy(liveTipSeen = true) } },
          onOpenLiveMode = { isInLiveMode = true },
          onEndLiveMode = { isInLiveMode = false },
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
          onSendMessage = { text ->
            // Step 6.3: a copy of your words to the tablet, Rivia chats only, only when it really sends.
            val sends = text.isNotBlank() && !isStreaming && !isActiveChatLab && layersLoadError == null
            val last = messages.lastOrNull { it.role == "assistant" && it.status == "completed" }
            val lastReply = last?.content
            // Step 7.1: when her reply finished (start plus how long it took), for your reply speed.
            val lastReplyAt = last?.let { it.createdAt + it.totalTimeMs.coerceAtLeast(0L) }
            viewModel.sendUserMessage(text)
            if (sends) trustThumbs.sendWords(text.trim(), lastReply, lastReplyAt)
          },
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
      containerColor = RiviaSurface1,
      titleContentColor = RiviaTextPrimary,
      textContentColor = RiviaTextPrimary,
      title = { Text("Edit & resend message") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Resending from this turn will replace subsequent turns in this chat.",
            style = MaterialTheme.typography.bodySmall,
            color = RiviaTextSecondary,
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
          colors = ButtonDefaults.buttonColors(containerColor = RiviaMint, contentColor = RiviaOnAccent),
          modifier = Modifier.testTag("confirm_edit_resend_button"),
        ) {
          Text("Resend", color = RiviaOnAccent, fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { messageForEdit = null }) {
          Text("Cancel", color = RiviaTextSecondary)
        }
      },
    )
  }

  // Bulk Delete Chats Confirm Dialog
  if (showBulkDeleteConfirm && selectedChatIds.isNotEmpty()) {
    val count = selectedChatIds.size
    val titleText = if (count == 1) "Delete 1 chat?" else "Delete $count chats?"
    val bodyText =
      if (count == 1) "Delete 1 chat and all its turns? This cannot be undone."
      else "Delete $count chats and all their turns? This cannot be undone."
    AlertDialog(
      onDismissRequest = { showBulkDeleteConfirm = false },
      containerColor = RiviaSurface1,
      titleContentColor = RiviaTextPrimary,
      title = { Text(titleText) },
      text = {
        Text(
          bodyText,
          color = RiviaTextSecondary,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            val idsToDelete = selectedChatIds
            viewModel.deleteChats(idsToDelete)
            selectedChatIds = emptySet()
            showBulkDeleteConfirm = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = RiviaErrorRed, contentColor = RiviaOnAccent),
        ) {
          Text("Delete", color = RiviaOnAccent, fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showBulkDeleteConfirm = false }) {
          Text("Cancel", color = RiviaTextSecondary)
        }
      },
    )
  }

  // Rename Chat Dialog
  chatToRename?.let { chat ->
    AlertDialog(
      onDismissRequest = { chatToRename = null },
      containerColor = RiviaSurface1,
      titleContentColor = RiviaTextPrimary,
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
          colors = ButtonDefaults.buttonColors(containerColor = RiviaMint, contentColor = RiviaOnAccent),
        ) {
          Text("Save", color = RiviaOnAccent, fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { chatToRename = null }) {
          Text("Cancel", color = RiviaTextSecondary)
        }
      },
    )
  }

  // Delete Chat Confirm Dialog
  chatToDelete?.let { chat ->
    AlertDialog(
      onDismissRequest = { chatToDelete = null },
      containerColor = RiviaSurface1,
      titleContentColor = RiviaTextPrimary,
      title = { Text("Delete chat?") },
      text = {
        Text(
          "Delete \"${chat.title}\" and all its turns? This cannot be undone.",
          color = RiviaTextSecondary,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteChat(chat.id)
            chatToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = RiviaErrorRed, contentColor = RiviaOnAccent),
        ) {
          Text("Delete", color = RiviaOnAccent, fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { chatToDelete = null }) {
          Text("Cancel", color = RiviaTextSecondary)
        }
      },
    )
  }

  // Edit Mode Preset Dialog
  presetToEdit?.let { preset ->
    var editName by remember(preset) { mutableStateOf(preset.name) }
    var editModel by remember(preset) { mutableStateOf(preset.model) }
    var editThinking by remember(preset) { mutableStateOf(preset.thinkingLevel) }
    val allowedLevels = remember(editModel) { RunSettings.allowedThinkingLevels(editModel) }

    AlertDialog(
      onDismissRequest = { presetToEdit = null },
      containerColor = RiviaSurface1,
      titleContentColor = RiviaTextPrimary,
      title = { Text("Edit Preset") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = editName,
            onValueChange = { editName = it },
            label = { Text("Preset name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("edit_preset_name_input"),
            colors = riviaTextFieldColors(),
          )
          OutlinedTextField(
            value = editModel,
            onValueChange = { editModel = it },
            label = { Text("Model ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("edit_preset_model_input"),
            colors = riviaTextFieldColors(),
          )
          Text(
            text = "Thinking level",
            style = MaterialTheme.typography.labelLarge,
            color = RiviaTextSecondary,
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            allowedLevels.forEach { lvl ->
              val selected = editThinking == lvl
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selected) RiviaMint else RiviaSurface2,
                border = BorderStroke(1.dp, if (selected) RiviaMint else RiviaBorder),
                modifier =
                  Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { editThinking = lvl },
              ) {
                Text(
                  text = if (lvl == "model_default") "default" else lvl,
                  color = if (selected) RiviaOnAccent else RiviaTextPrimary,
                  fontFamily = ChivoMonoFontFamily,
                  fontSize = 12.sp,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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
              originalName = preset.name,
              updatedPreset =
                ModePreset(
                  name = editName.trim().ifBlank { preset.name },
                  model = editModel.trim().ifBlank { preset.model },
                  thinkingLevel = editThinking,
                ),
            )
            presetToEdit = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = RiviaMint, contentColor = RiviaOnAccent),
        ) {
          Text("Save Preset", color = RiviaOnAccent, fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { presetToEdit = null }) {
          Text("Cancel", color = RiviaTextSecondary)
        }
      },
    )
  }

  // Create Mode Preset Dialog
  if (showCreatePresetDialog) {
    var createName by remember { mutableStateOf("") }
    var createModel by remember { mutableStateOf("gemini-3.8-flash") }
    var createThinking by remember { mutableStateOf("medium") }
    val allowedLevels = remember(createModel) { RunSettings.allowedThinkingLevels(createModel) }

    AlertDialog(
      onDismissRequest = { showCreatePresetDialog = false },
      containerColor = RiviaSurface1,
      titleContentColor = RiviaTextPrimary,
      title = { Text("Create Preset") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = createName,
            onValueChange = { createName = it },
            label = { Text("Preset name") },
            placeholder = { Text("e.g. Creative, Coding, Research", color = RiviaTextTertiary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("create_preset_name_input"),
            colors = riviaTextFieldColors(),
          )
          OutlinedTextField(
            value = createModel,
            onValueChange = { createModel = it },
            label = { Text("Model ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("create_preset_model_input"),
            colors = riviaTextFieldColors(),
          )
          Text(
            text = "Thinking level",
            style = MaterialTheme.typography.labelLarge,
            color = RiviaTextSecondary,
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            allowedLevels.forEach { lvl ->
              val selected = createThinking == lvl
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selected) RiviaMint else RiviaSurface2,
                border = BorderStroke(1.dp, if (selected) RiviaMint else RiviaBorder),
                modifier =
                  Modifier.clip(RoundedCornerShape(8.dp))
                    .clickable { createThinking = lvl },
              ) {
                Text(
                  text = if (lvl == "model_default") "default" else lvl,
                  color = if (selected) RiviaOnAccent else RiviaTextPrimary,
                  fontFamily = ChivoMonoFontFamily,
                  fontSize = 12.sp,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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
            if (createName.isNotBlank()) {
              viewModel.createModePreset(
                ModePreset(
                  name = createName.trim(),
                  model = createModel.trim().ifBlank { "gemini-3.8-flash" },
                  thinkingLevel = createThinking,
                )
              )
              showCreatePresetDialog = false
            }
          },
          enabled = createName.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = RiviaMint, contentColor = RiviaOnAccent),
          modifier = Modifier.testTag("confirm_create_preset_button"),
        ) {
          Text("Create", color = RiviaOnAccent, fontWeight = FontWeight.SemiBold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreatePresetDialog = false }) {
          Text("Cancel", color = RiviaTextSecondary)
        }
      },
    )
  }
}


private fun stagedDrawerAlpha(
  elapsedMs: Float,
  closeAlpha: Float,
  startMs: Float,
  durationMs: Float = 160f,
): Float {
  val raw = ((elapsedMs - startMs) / durationMs).coerceIn(0f, 1f)
  val inv = 1f - raw
  val eased = 1f - inv * inv * inv
  return (eased * closeAlpha).coerceIn(0f, 1f)
}

private fun Modifier.drawerStaggerReveal(
  revealElapsedMsProvider: (() -> Float)?,
  closeAlphaProvider: (() -> Float)?,
  startMs: Float,
  durationMs: Float = 160f,
  slideUpDp: Float = 5f,
): Modifier {
  if (revealElapsedMsProvider == null || closeAlphaProvider == null) return this
  return this.graphicsLayer {
    val elapsed = revealElapsedMsProvider()
    val closeA = closeAlphaProvider()
    val raw = ((elapsed - startMs) / durationMs).coerceIn(0f, 1f)
    val inv = 1f - raw
    val eased = 1f - inv * inv * inv
    alpha = (eased * closeA).coerceIn(0f, 1f)
    translationY = (1f - eased) * slideUpDp.dp.toPx()
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
  selectedChatIds: Set<String>,
  onToggleSelectChat: (ChatEntity) -> Unit,
  onSelectAllChats: () -> Unit,
  onClearSelection: () -> Unit,
  onRenameSelectedChat: () -> Unit,
  onDeleteSelectedChats: () -> Unit,
  onNewChat: () -> Unit,
  onSelectChat: (ChatEntity) -> Unit,
  onOpenTests: () -> Unit,
  onOpenBots: () -> Unit = {},
  onOpenLiveMode: () -> Unit,
  onOpenPipeline: () -> Unit,
  onOpenLayers: () -> Unit,
  onOpenRunSettings: () -> Unit = {},
  onOpenGlobalSettings: () -> Unit,
  onOpenDevLabsSheet: () -> Unit = {},
  revealElapsedMsProvider: (() -> Float)? = null,
  closeAlphaProvider: (() -> Float)? = null,
) {
  val dateFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }
  val haptic = LocalHapticFeedback.current
  val inSelectionMode = selectedChatIds.isNotEmpty()

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

  val bottomBaseStartMs = 870f + minOf(chats.size.coerceAtLeast(1), 16) * 30f + 30f

  Column(
    modifier = Modifier.fillMaxSize().padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Header + "+ New chat" (or selection action bar in selection mode)
    if (inSelectionMode) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .drawerStaggerReveal(
              revealElapsedMsProvider = revealElapsedMsProvider,
              closeAlphaProvider = closeAlphaProvider,
              startMs = 750f,
            ),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onClearSelection,
          modifier = Modifier.size(40.dp).testTag("chat_selection_cancel"),
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Cancel selection",
            tint = RiviaIconDefault,
          )
        }
        Text(
          text = "${selectedChatIds.size} selected",
          style = MaterialTheme.typography.titleMedium,
          color = RiviaText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(horizontal = 4.dp).testTag("chat_selection_count"),
        )
        IconButton(
          onClick = onSelectAllChats,
          modifier = Modifier.size(40.dp).testTag("chat_select_all"),
        ) {
          Icon(
            imageVector = Icons.Outlined.SelectAll,
            contentDescription = "Select all",
            tint = RiviaIconDefault,
          )
        }
        val canRename = selectedChatIds.size == 1
        IconButton(
          onClick = onRenameSelectedChat,
          enabled = canRename,
          modifier = Modifier.size(40.dp).testTag("chat_selection_rename"),
        ) {
          Icon(
            imageVector = Icons.Outlined.DriveFileRenameOutline,
            contentDescription = "Rename chat",
            tint = if (canRename) RiviaIconDefault else RiviaTextTertiary,
          )
        }
        IconButton(
          onClick = onDeleteSelectedChats,
          modifier = Modifier.size(40.dp).testTag("chat_selection_delete"),
        ) {
          Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = "Delete selected chats",
            tint = RiviaErrorRed,
          )
        }
      }
    } else {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .drawerStaggerReveal(
              revealElapsedMsProvider = revealElapsedMsProvider,
              closeAlphaProvider = closeAlphaProvider,
              startMs = 750f,
            ),
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
              contentColor = RiviaOnAccent,
            ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("new_chat_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Add,
            contentDescription = newChatButtonLabel,
            tint = RiviaOnAccent,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            newChatButtonLabel,
            color = RiviaOnAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }

    // Search field (fades in after background)
    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchQueryChange,
      placeholder = {
        Text(
          text =
            if (appUiMode == AppUiMode.LABS) "Search Labs chats..."
            else "Search Rivia chats...",
          color = RiviaTextTertiary,
        )
      },
      leadingIcon = {
        Icon(
          imageVector = Icons.Outlined.Search,
          contentDescription = "Search chats",
          tint = RiviaIconDefault,
        )
      },
      singleLine = true,
      shape = RoundedCornerShape(16.dp),
      modifier =
        Modifier.fillMaxWidth()
          .drawerStaggerReveal(
            revealElapsedMsProvider = revealElapsedMsProvider,
            closeAlphaProvider = closeAlphaProvider,
            startMs = 810f,
          )
          .testTag("drawer_search_input"),
      colors =
        OutlinedTextFieldDefaults.colors(
          focusedTextColor = RiviaTextPrimary,
          unfocusedTextColor = RiviaTextPrimary,
          focusedContainerColor = RiviaSurface2,
          unfocusedContainerColor = RiviaSurface2,
          focusedBorderColor = RiviaMint,
          unfocusedBorderColor = RiviaBorder,
          cursorColor = RiviaMint,
        ),
    )

    Spacer(modifier = Modifier.height(2.dp))

    // Chat list newest first (rows stagger top-to-bottom 30 ms apart)
    if (chats.isEmpty()) {
      Box(
        modifier =
          Modifier.weight(1f)
            .fillMaxWidth()
            .drawerStaggerReveal(
              revealElapsedMsProvider = revealElapsedMsProvider,
              closeAlphaProvider = closeAlphaProvider,
              startMs = 870f,
            ),
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
          color = RiviaTextSecondary,
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        itemsIndexed(chats, key = { _, item -> item.id }) { index, chat ->
          val isChecked = chat.id in selectedChatIds
          val isSelected = if (inSelectionMode) isChecked else (chat.id == activeChatId)
          val rowStartMs = 870f + minOf(index, 16) * 30f
          Surface(
            shape = RoundedCornerShape(14.dp),
            color =
              if (isSelected) RiviaSurface3
              else androidx.compose.ui.graphics.Color.Transparent,
            border =
              if (isSelected) BorderStroke(1.dp, RiviaBorder)
              else null,
            modifier =
              Modifier.fillMaxWidth()
                .drawerStaggerReveal(
                  revealElapsedMsProvider = revealElapsedMsProvider,
                  closeAlphaProvider = closeAlphaProvider,
                  startMs = rowStartMs,
                )
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                  onClick = {
                    if (inSelectionMode) {
                      onToggleSelectChat(chat)
                    } else {
                      onSelectChat(chat)
                    }
                  },
                  onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleSelectChat(chat)
                  },
                ),
          ) {
            if (inSelectionMode) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(
                  checked = isChecked,
                  onCheckedChange = { onToggleSelectChat(chat) },
                  colors =
                    CheckboxDefaults.colors(
                      checkedColor = RiviaMint,
                      checkmarkColor = RiviaOnAccent,
                      uncheckedColor = RiviaBorder,
                    ),
                  modifier = Modifier.testTag("chat_select_checkbox_${chat.id}"),
                )
                Column(
                  modifier = Modifier.weight(1f),
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
                      color = RiviaTextPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f),
                    )
                    if (chat.isLabChat) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Surface(
                        shape = RoundedCornerShape(50),
                        color = RiviaSurface2,
                        border = BorderStroke(1.dp, RiviaBorder),
                      ) {
                        Text(
                          text = "Labs",
                          color = RiviaTextSecondary,
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
                    color = RiviaTextTertiary,
                  )
                }
              }
            } else {
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
                    color = RiviaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                  if (chat.isLabChat) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                      shape = RoundedCornerShape(50),
                      color = RiviaSurface2,
                      border = BorderStroke(1.dp, RiviaBorder),
                    ) {
                      Text(
                        text = "Labs",
                        color = RiviaTextSecondary,
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
                  color = RiviaTextTertiary,
                )
              }
            }
          }
        }
      }
    }

    // Tablet, Layers, Pipeline, Tests (in Labs), and Settings grouped together in the same menu
    // at the bottom of the drawer, above the mode pill, with one thin #262626 divider line above them.
    val settingsButtonLabel =
      when (appUiMode) {
        AppUiMode.RIVIA -> "Settings"
        AppUiMode.DEV -> "Settings (Keys, Voice, Internals)"
        AppUiMode.LABS -> "Settings (Labs Sandbox)"
      }

    HorizontalDivider(
      color = Color(0xFF262626),
      thickness = 1.dp,
      modifier =
        Modifier.drawerStaggerReveal(
          revealElapsedMsProvider = revealElapsedMsProvider,
          closeAlphaProvider = closeAlphaProvider,
          startMs = bottomBaseStartMs,
          slideUpDp = 2f,
        ),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
      // Tablet row
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .height(44.dp)
            .drawerStaggerReveal(
              revealElapsedMsProvider = revealElapsedMsProvider,
              closeAlphaProvider = closeAlphaProvider,
              startMs = bottomBaseStartMs + 25f,
            )
            .clickable(onClick = onOpenBots)
            .padding(horizontal = 8.dp)
            .testTag("open_bots_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
      ) {
        Icon(
          imageVector = Icons.Outlined.AccountTree,
          contentDescription = "Tablet",
          tint = Color(0xFFA3A3A3),
          modifier = Modifier.size(17.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = "Tablet",
          fontSize = 14.sp,
          color = Color(0xFFEDEDED),
        )
      }

      // Layers row
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .height(44.dp)
            .drawerStaggerReveal(
              revealElapsedMsProvider = revealElapsedMsProvider,
              closeAlphaProvider = closeAlphaProvider,
              startMs = bottomBaseStartMs + 55f,
            )
            .clickable(onClick = onOpenLayers)
            .padding(horizontal = 8.dp)
            .testTag("open_vad_layer_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
      ) {
        Icon(
          imageVector = Icons.Outlined.Tune,
          contentDescription = "Layers",
          tint = Color(0xFFA3A3A3),
          modifier = Modifier.size(17.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = "Layers",
          fontSize = 14.sp,
          color = Color(0xFFEDEDED),
        )
      }

      // Pipeline row
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .height(44.dp)
            .drawerStaggerReveal(
              revealElapsedMsProvider = revealElapsedMsProvider,
              closeAlphaProvider = closeAlphaProvider,
              startMs = bottomBaseStartMs + 85f,
            )
            .clickable(onClick = onOpenPipeline)
            .padding(horizontal = 8.dp)
            .testTag("open_pipeline_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
      ) {
        Icon(
          imageVector = Icons.Outlined.AccountTree,
          contentDescription = "Pipeline",
          tint = Color(0xFFA3A3A3),
          modifier = Modifier.size(17.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = "Pipeline",
          fontSize = 14.sp,
          color = Color(0xFFEDEDED),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Soon",
          color = Color(0xFF8F8F8F),
          fontSize = 11.sp,
        )
      }

      // Labs-only Run settings and Tests rows (moved from Labs top bar into the drawer)
      if (appUiMode == AppUiMode.LABS) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .height(44.dp)
              .drawerStaggerReveal(
                revealElapsedMsProvider = revealElapsedMsProvider,
                closeAlphaProvider = closeAlphaProvider,
                startMs = bottomBaseStartMs + 115f,
              )
              .clickable(onClick = onOpenRunSettings)
              .padding(horizontal = 8.dp)
              .testTag("open_run_settings_drawer_button"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Start,
        ) {
          Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = "Run settings",
            tint = Color(0xFFA3A3A3),
            modifier = Modifier.size(17.dp),
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = "Run settings",
            fontSize = 14.sp,
            color = Color(0xFFEDEDED),
          )
        }

        Row(
          modifier =
            Modifier.fillMaxWidth()
              .height(44.dp)
              .drawerStaggerReveal(
                revealElapsedMsProvider = revealElapsedMsProvider,
                closeAlphaProvider = closeAlphaProvider,
                startMs = bottomBaseStartMs + 145f,
              )
              .clickable(onClick = onOpenTests)
              .padding(horizontal = 8.dp)
              .testTag("open_tests_button"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Start,
        ) {
          Icon(
            imageVector = Icons.Outlined.Science,
            contentDescription = "Tests",
            tint = Color(0xFFA3A3A3),
            modifier = Modifier.size(17.dp),
          )
          Spacer(modifier = Modifier.width(12.dp))
          Text(
            text = "Tests",
            fontSize = 14.sp,
            color = Color(0xFFEDEDED),
          )
        }
      }

      val settingsStartMs =
        if (appUiMode == AppUiMode.LABS) bottomBaseStartMs + 175f else bottomBaseStartMs + 115f

      // Settings row (flask icon)
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .height(44.dp)
            .drawerStaggerReveal(
              revealElapsedMsProvider = revealElapsedMsProvider,
              closeAlphaProvider = closeAlphaProvider,
              startMs = settingsStartMs,
            )
            .clickable(onClick = onOpenGlobalSettings)
            .padding(horizontal = 8.dp)
            .testTag("open_settings_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
      ) {
        Icon(
          imageVector = Icons.Outlined.Science,
          contentDescription = "Settings",
          tint = Color(0xFFA3A3A3),
          modifier = Modifier.size(17.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = settingsButtonLabel,
          fontSize = 14.sp,
          color = Color(0xFFEDEDED),
        )
      }
    }

    val footerStartMs =
      if (appUiMode == AppUiMode.LABS) bottomBaseStartMs + 205f else bottomBaseStartMs + 145f

    if (appUiMode == AppUiMode.RIVIA) {
      Text(
        text = "Everyday mode. Dev and Labs are for building and testing.",
        color = RiviaTextTertiary,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier =
          Modifier.fillMaxWidth()
            .padding(top = 4.dp)
            .drawerStaggerReveal(
              revealElapsedMsProvider = revealElapsedMsProvider,
              closeAlphaProvider = closeAlphaProvider,
              startMs = footerStartMs,
            ),
      )
    }

    // Single hollow purple outlined pill with the selected mode's corresponding color and name inside
    val selectedModeColor = accentColorForMode(appUiMode)
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .padding(top = 4.dp)
          .drawerStaggerReveal(
            revealElapsedMsProvider = revealElapsedMsProvider,
            closeAlphaProvider = closeAlphaProvider,
            startMs = footerStartMs + 20f,
          ),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = BorderStroke(1.dp, RiviaModeViolet),
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onOpenDevLabsSheet)
            .testTag("drawer_dev_labs_button"),
      ) {
        Text(
          text = appUiMode.displayName,
          style = MaterialTheme.typography.labelMedium,
          fontFamily = ChivoMonoFontFamily,
          fontWeight = FontWeight.SemiBold,
          color = selectedModeColor,
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevLabsModeSheet(
  currentMode: AppUiMode,
  onSelectMode: (AppUiMode) -> Unit,
  onDismiss: () -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  var showDevHoldHint by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    keyboardController?.hide()
    focusManager.clearFocus()
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = RiviaSurface1,
    contentColor = RiviaTextPrimary,
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
        color = RiviaTextPrimary,
      )

      // Rivia choice: opens on a normal tap
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = RiviaSurface2,
        border =
          BorderStroke(
            1.dp,
            if (currentMode == AppUiMode.RIVIA) RiviaModeWhite else RiviaBorder,
          ),
        modifier =
          Modifier.fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelectMode(AppUiMode.RIVIA) }
            .testTag("sheet_choice_rivia"),
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
              text = "Rivia",
              style = MaterialTheme.typography.titleSmall,
              color = RiviaModeWhite,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Standard Rivia experience for everyday voice and chat conversations.",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaTextSecondary,
            )
          }
        }
      }

      // Dev choice: requires 600 ms long press + haptic tick on unlock
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = RiviaSurface2,
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
                color = RiviaSurface3,
                border = BorderStroke(1.dp, RiviaBorder),
              ) {
                Text(
                  text = "Hold 600ms",
                  color = RiviaTextSecondary,
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
              color = RiviaTextSecondary,
            )
            if (showDevHoldHint) {
              Text(
                text = "Hold for 600ms to unlock Dev",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = ChivoMonoFontFamily,
                color = RiviaTextSecondary,
                modifier = Modifier.padding(top = 4.dp).testTag("dev_hold_hint_text"),
              )
            }
          }
        }
      }

      // Labs choice: opens on a normal tap
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = RiviaSurface2,
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
              color = RiviaTextSecondary,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ChatConversationPane(
  activeChatId: String? = null,
  newChatResetToken: Int = 0,
  showDrawerButton: Boolean,
  onOpenDrawer: () -> Unit,
  particleController: RiviaParticleFieldController? = null,
  playbackAudioLevel: Float = 0f,
  appUiMode: AppUiMode,
  onSelectUiMode: (AppUiMode) -> Unit,
  onOpenDevLabsSheet: () -> Unit = {},
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
  onCreateModePreset: () -> Unit = {},
  onEditModePreset: (ModePreset) -> Unit,
  onDeleteModePreset: (ModePreset) -> Unit = {},
  onToggleAutoReadAloud: () -> Unit,
  onOpenRunSettings: () -> Unit,
  onOpenGlobalSettings: () -> Unit,
  isInLiveMode: Boolean = false,
  keepScreenOnInLive: Boolean = true,
  vibrateOnLiveChanges: Boolean = true,
  liveTipSeen: Boolean = false,
  onDismissLiveTip: () -> Unit = {},
  onOpenLiveMode: () -> Unit,
  onEndLiveMode: () -> Unit = {},
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
  thumbs: TrustThumbsState? = null,
) {
  val context = LocalContext.current
  val view = LocalView.current
  LaunchedEffect(thumbs) { thumbs?.loadIfNeeded() }
  val density = LocalDensity.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  val removeAnimations = isSystemRemoveAnimationsEnabled(context)

  val liveState by LiveSessionManager.uiState.collectAsStateWithLifecycle()
  val liveMicLevel by LiveSessionManager.liveMicLevel.collectAsStateWithLifecycle()
  var micPermissionDeniedReason by remember { mutableStateOf<String?>(null) }

  var showPresetMenu by remember { mutableStateOf(false) }
  var presetForMoreMenu by remember { mutableStateOf<String?>(null) }
  var composerText by rememberSaveable(activeChatId, newChatResetToken) { mutableStateOf("") }
  var priorComposerTextBeforeDictation by
    rememberSaveable(activeChatId, newChatResetToken) { mutableStateOf("") }
  var showComposerInEmptyChat by
    rememberSaveable(activeChatId, newChatResetToken) { mutableStateOf(false) }
  var isInDictationPanel by
    rememberSaveable(activeChatId, newChatResetToken) { mutableStateOf(false) }
  var dictationOpenedFromComposer by
    rememberSaveable(activeChatId, newChatResetToken) { mutableStateOf(false) }
  var dictationExitedViaError by remember { mutableStateOf(false) }
  var currentDictationVoiceLevel by remember { mutableFloatStateOf(0f) }
  var focusComposerOnReveal by
    rememberSaveable(activeChatId, newChatResetToken) { mutableStateOf(false) }
  var startDictationOnReveal by
    rememberSaveable(activeChatId, newChatResetToken) { mutableStateOf(false) }
  var requestKeyboardFocusNow by remember { mutableStateOf(false) }
  var sphereColumnTopPx by remember { mutableIntStateOf(0) }
  val listState = rememberLazyListState()

  val isInTypedChat =
    !isInLiveMode &&
      !isInDictationPanel &&
      (messages.isNotEmpty() || showComposerInEmptyChat || composerText.isNotEmpty())

  LaunchedEffect(isInTypedChat) {
    if (!isInTypedChat) {
      dictationExitedViaError = false
    }
  }

  val livePermissionsLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
      val micGranted =
        result[Manifest.permission.RECORD_AUDIO] == true ||
          ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
          ) == PackageManager.PERMISSION_GRANTED

      if (micGranted) {
        micPermissionDeniedReason = null
        LiveVoiceService.start(context)
      } else {
        micPermissionDeniedReason =
          "Rivia needs the microphone to hear you in Live."
      }
    }

  LaunchedEffect(isInLiveMode) {
    if (isInLiveMode) {
      val hasMic =
        ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
      val hasNotif =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
          ) == PackageManager.PERMISSION_GRANTED
        } else {
          true
        }

      if (hasMic && hasNotif) {
        micPermissionDeniedReason = null
        LiveVoiceService.start(context)
      } else {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        livePermissionsLauncher.launch(perms.toTypedArray())
      }
    } else {
      micPermissionDeniedReason = null
    }
  }

  // Keep screen on in Live setting (on by default)
  DisposableEffect(isInLiveMode, keepScreenOnInLive) {
    val prev = view.keepScreenOn
    if (isInLiveMode && keepScreenOnInLive) {
      view.keepScreenOn = true
    }
    onDispose { view.keepScreenOn = prev }
  }

  // Subtle haptics on Live state transitions
  LaunchedEffect(isInLiveMode, liveState.orbState, vibrateOnLiveChanges) {
    if (isInLiveMode && liveState.isActive && vibrateOnLiveChanges) {
      view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
  }

  val completedAssistantCount =
    remember(messages) {
      messages.count { it.role == "assistant" && it.status == "completed" }
    }
  var initialCompletedCountInLive by remember { mutableIntStateOf(0) }
  LaunchedEffect(isInLiveMode) {
    if (isInLiveMode) {
      initialCompletedCountInLive =
        messages.count { it.role == "assistant" && it.status == "completed" }
    }
  }
  LaunchedEffect(
    isInLiveMode,
    liveTipSeen,
    completedAssistantCount,
    isStreaming,
    isSpeaking,
    liveState.orbState,
  ) {
    if (
      isInLiveMode &&
        !liveTipSeen &&
        completedAssistantCount > initialCompletedCountInLive &&
        !isStreaming &&
        !isSpeaking &&
        liveState.orbState != OrbState.SPEAKING &&
        liveState.orbState != OrbState.THINKING
    ) {
      onDismissLiveTip()
    }
  }

  val edgeGlowTransition = rememberInfiniteTransition(label = "edge_glow")
  val edgeGlowPulse by
    edgeGlowTransition.animateFloat(
      initialValue = 0.25f,
      targetValue = 0.70f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 800),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "edge_glow_alpha",
    )
  val isLiveSpeaking = liveState.orbState == OrbState.SPEAKING
  val edgeGlowEnvelope by
    animateFloatAsState(
      targetValue = if (isInLiveMode && isLiveSpeaking) 1f else 0f,
      animationSpec =
        if (removeAnimations) snap()
        else tween(durationMillis = 320, easing = FastOutSlowInEasing),
      label = "chat_live_edge_glow_envelope",
    )

  // System Back leaves the Live screen while keeping Live running in the background; End stops it.
  BackHandler(enabled = isInLiveMode) {
    requestKeyboardFocusNow = false
    focusComposerOnReveal = false
    startDictationOnReveal = false
    dictationOpenedFromComposer = false
    isInDictationPanel = false
    onEndLiveMode()
  }

  // Cancel dictation panel on system Back: return to active chat (discarding only dictated text) or Home
  BackHandler(enabled = !isInLiveMode && isInDictationPanel) {
    requestKeyboardFocusNow = false
    focusComposerOnReveal = false
    startDictationOnReveal = false
    dictationExitedViaError = false
    if (dictationOpenedFromComposer || messages.isNotEmpty() || priorComposerTextBeforeDictation.isNotEmpty()) {
      composerText = priorComposerTextBeforeDictation
      showComposerInEmptyChat = true
    } else {
      composerText = ""
      showComposerInEmptyChat = false
    }
    dictationOpenedFromComposer = false
    isInDictationPanel = false
  }

  // Going back from the empty typed chat returns to the sphere Home screen and reverses cleanly
  BackHandler(
    enabled =
      !isInLiveMode &&
        !isInDictationPanel &&
        messages.isEmpty() &&
        (showComposerInEmptyChat || composerText.isNotEmpty())
  ) {
    keyboardController?.hide()
    focusManager.clearFocus()
    requestKeyboardFocusNow = false
    focusComposerOnReveal = false
    startDictationOnReveal = false
    dictationExitedViaError = false
    composerText = ""
    showComposerInEmptyChat = false
  }

  // UI opacity channels (0-200 ms home fade-out, ~700 ms chat bar fill, ~750 ms dictation fill, ~500/~750 ms live UI)
  val buttonsAlpha =
    remember {
      Animatable(if (isInTypedChat || isInLiveMode || isInDictationPanel) 0f else 1f)
    }
  val composerAlpha = remember { Animatable(if (isInTypedChat) 1f else 0f) }
  val dictationContentAlpha = remember { Animatable(if (isInDictationPanel) 1f else 0f) }
  val liveCaptionsAlpha = remember { Animatable(if (isInLiveMode) 1f else 0f) }
  val liveControlsAlpha = remember { Animatable(if (isInLiveMode) 1f else 0f) }
  var isFirstTransitionPass by remember { mutableStateOf(true) }
  var wasInDictationPanel by remember { mutableStateOf(isInDictationPanel) }

  LaunchedEffect(isInTypedChat, isInLiveMode, isInDictationPanel) {
    val skipAnim = isFirstTransitionPass || isSystemRemoveAnimationsEnabled(context)
    val cameFromDictation = wasInDictationPanel && !isInDictationPanel
    isFirstTransitionPass = false
    wasInDictationPanel = isInDictationPanel
    val easeOut = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    when {
      isInLiveMode -> {
        requestKeyboardFocusNow = false
        keyboardController?.hide()
        focusManager.clearFocus()
        if (skipAnim) {
          buttonsAlpha.snapTo(0f)
          composerAlpha.snapTo(0f)
          dictationContentAlpha.snapTo(0f)
          liveCaptionsAlpha.snapTo(1f)
          liveControlsAlpha.snapTo(1f)
        } else {
          coroutineScope {
            launch {
              buttonsAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
              )
            }
            launch {
              composerAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
              )
            }
            launch {
              dictationContentAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = easeOut),
              )
            }
            launch {
              delay(500L)
              liveCaptionsAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = easeOut),
              )
            }
            launch {
              delay(750L)
              liveControlsAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = easeOut),
              )
            }
          }
        }
      }
      isInDictationPanel -> {
        requestKeyboardFocusNow = false
        keyboardController?.hide()
        focusManager.clearFocus()
        if (skipAnim) {
          buttonsAlpha.snapTo(0f)
          composerAlpha.snapTo(0f)
          liveCaptionsAlpha.snapTo(0f)
          liveControlsAlpha.snapTo(0f)
          dictationContentAlpha.snapTo(1f)
        } else {
          coroutineScope {
            launch {
              buttonsAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
              )
            }
            launch {
              composerAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
              )
            }
            launch {
              liveCaptionsAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
              )
            }
            launch {
              liveControlsAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
              )
            }
            launch {
              // ~750 ms: panel's #121212 fill and contents fade in under the particles
              delay(730L)
              dictationContentAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = easeOut),
              )
            }
          }
        }
      }
      isInTypedChat -> {
        if (skipAnim) {
          buttonsAlpha.snapTo(0f)
          liveCaptionsAlpha.snapTo(0f)
          liveControlsAlpha.snapTo(0f)
          dictationContentAlpha.snapTo(0f)
          composerAlpha.snapTo(1f)
          if (focusComposerOnReveal) {
            focusComposerOnReveal = false
            requestKeyboardFocusNow = true
          }
        } else if (cameFromDictation && !dictationExitedViaError) {
          // Done from dictation panel: particles slide down into the message bar outline
          coroutineScope {
            launch {
              dictationContentAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 160, easing = easeOut),
              )
            }
            launch {
              delay(320L)
              composerAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180, easing = easeOut),
              )
              if (focusComposerOnReveal) {
                focusComposerOnReveal = false
                requestKeyboardFocusNow = true
              }
            }
          }
        } else {
          coroutineScope {
            launch {
              buttonsAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
              )
            }
            launch {
              liveCaptionsAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
              )
            }
            launch {
              liveControlsAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
              )
            }
            launch {
              dictationContentAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = easeOut),
              )
            }
            launch {
              // ~700 ms: bar's #161616 fill fades in under the particles; keyboard opens once in place
              delay(680L)
              launch {
                composerAlpha.animateTo(
                  targetValue = 1f,
                  animationSpec = tween(durationMillis = 200, easing = easeOut),
                )
              }
              if (focusComposerOnReveal) {
                focusComposerOnReveal = false
                requestKeyboardFocusNow = true
              }
            }
          }
        }
      }
      else -> {
        requestKeyboardFocusNow = false
        keyboardController?.hide()
        focusManager.clearFocus()
        if (skipAnim) {
          composerAlpha.snapTo(0f)
          liveCaptionsAlpha.snapTo(0f)
          liveControlsAlpha.snapTo(0f)
          dictationContentAlpha.snapTo(0f)
          buttonsAlpha.snapTo(1f)
        } else {
          coroutineScope {
            launch {
              composerAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = easeOut),
              )
            }
            launch {
              liveCaptionsAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = easeOut),
              )
            }
            launch {
              liveControlsAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = easeOut),
              )
            }
            launch {
              dictationContentAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180, easing = easeOut),
              )
            }
            launch {
              delay(740L)
              buttonsAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = easeOut),
              )
            }
          }
        }
      }
    }
  }

  // Existing messages staggered entrance (From 450 ms, 10 dp rise, 80 ms apart from oldest visible)
  var entranceChatId by remember { mutableStateOf<String?>(activeChatId) }
  var entranceMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var entranceCapturedForChat by remember { mutableStateOf(activeChatId != null) }
  var entranceElapsedMs by remember { mutableFloatStateOf(5000f) }

  LaunchedEffect(activeChatId, removeAnimations) {
    if (activeChatId == null || removeAnimations) {
      entranceChatId = activeChatId
      entranceMessageIds = emptySet()
      entranceCapturedForChat = true
      entranceElapsedMs = 5000f
      return@LaunchedEffect
    }
    if (activeChatId != entranceChatId) {
      entranceChatId = activeChatId
      entranceMessageIds = emptySet()
      entranceCapturedForChat = false
      entranceElapsedMs = 0f
      var lastNanos = -1L
      while (entranceElapsedMs < 2600f) {
        withFrameNanos { nanos ->
          if (lastNanos >= 0L) {
            val dtMs = ((nanos - lastNanos).coerceAtLeast(0L)) / 1_000_000f
            entranceElapsedMs += dtMs.coerceAtMost(100f)
          }
          lastNanos = nanos
        }
      }
    }
  }

  LaunchedEffect(activeChatId, messages) {
    if (
      !entranceCapturedForChat &&
        activeChatId != null &&
        messages.isNotEmpty() &&
        messages.first().chatId == activeChatId
    ) {
      entranceMessageIds = messages.map { it.id }.toSet()
      entranceCapturedForChat = true
    } else if (!entranceCapturedForChat && entranceElapsedMs > 420f) {
      entranceCapturedForChat = true
    }
  }

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

  val desiredParticleMode =
    when {
      isInLiveMode -> ParticleSceneMode.LIVE
      isInDictationPanel -> ParticleSceneMode.DICTATION_PANEL
      isInTypedChat ->
        if (dictationExitedViaError) ParticleSceneMode.CHAT_BAR_AFTER_DICTATION_ERROR
        else ParticleSceneMode.CHAT_BAR
      else -> ParticleSceneMode.HOME
    }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val viewportScale =
      remember(maxWidth, maxHeight) {
        ViewportScale.fromPaneDp(maxWidth.value, maxHeight.value)
      }

    SideEffect {
      particleController?.let { ctrl ->
        ctrl.viewportScale = viewportScale
        ctrl.chatBarFillAlpha = composerAlpha.value
        ctrl.dictationPanelFillAlpha = dictationContentAlpha.value
        ctrl.dictateMicLevel = if (isInDictationPanel) currentDictationVoiceLevel else 0f
        ctrl.liveOrbState = liveState.orbState
        ctrl.liveMicLevel = liveMicLevel
        ctrl.livePlaybackLevel = playbackAudioLevel
        ctrl.isLiveSpeakingFallback = isSpeaking || isLiveSpeaking
        ctrl.updateDesiredMode(desiredParticleMode)
      }
    }

    Column(
      modifier =
        Modifier.fillMaxSize()
          .imePadding()
          .drawBehind {
            val effGlowAlpha = edgeGlowPulse * edgeGlowEnvelope
            if (effGlowAlpha > 0.004f) {
              val strokePx = 14.dp.toPx()
              drawRect(
                brush =
                  Brush.radialGradient(
                    colors =
                      listOf(
                        Color.Transparent,
                        RiviaMint.copy(alpha = effGlowAlpha * 0.45f),
                      ),
                    radius = size.maxDimension * 0.72f,
                  )
              )
              drawRect(
                color = RiviaMint.copy(alpha = effGlowAlpha),
                style = Stroke(width = strokePx),
              )
            }
          }
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
              onClick = {
                keyboardController?.hide()
                focusManager.clearFocus()
                onOpenDrawer()
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("drawer_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = "Open navigation drawer",
                tint = RiviaIconDefault,
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
                color = RiviaTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
              )
              // In Dev and Labs, show a hollow purple outlined mode pill with the selected mode's color and name inside
              if (appUiMode != AppUiMode.RIVIA) {
                Surface(
                  shape = RoundedCornerShape(50),
                  color = Color.Transparent,
                  border = BorderStroke(1.dp, RiviaModeViolet),
                  modifier =
                    Modifier.clip(RoundedCornerShape(50))
                      .clickable {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onOpenDevLabsSheet()
                      }
                      .testTag("top_bar_mode_tag"),
                ) {
                  Text(
                    text = appUiMode.displayName,
                    color = accentColorForMode(appUiMode),
                    fontFamily = ChivoMonoFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
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
                color = RiviaTextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          if (appUiMode != AppUiMode.LABS) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              // Auto read-aloud toggle directly in the top bar for Rivia and Dev modes (no model/profile selector)
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
                  tint = if (runSettings.autoReadAloud) RiviaIconActive else RiviaIconDefault,
                )
              }
              if (appUiMode == AppUiMode.DEV) {
                IconButton(
                  onClick = onOpenChatInfo,
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("run_settings_gear_button"),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Open Chat info for this Rivia chat",
                    tint = RiviaTextTertiary,
                  )
                }
              }
            }
          }
        }

        // Secondary action strip in Top Bar (Labs only): Preset selector and Auto read-aloud
        if (appUiMode == AppUiMode.LABS) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            // Labs Mode preset chip (Fast, Balanced, Deep, + custom presets)
            Box {
              Surface(
                shape = RoundedCornerShape(18.dp),
                color = RiviaSurface2,
                border = BorderStroke(1.dp, RiviaBorder),
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                      keyboardController?.hide()
                      focusManager.clearFocus()
                      presetForMoreMenu = null
                      showPresetMenu = true
                    }
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
                    color = RiviaTextPrimary,
                  )
                }
              }

              DropdownMenu(
                expanded = showPresetMenu,
                onDismissRequest = {
                  presetForMoreMenu = null
                  showPresetMenu = false
                },
                modifier = Modifier.background(RiviaSurface1),
              ) {
                modePresets.forEach { preset ->
                  DropdownMenuItem(
                    text = {
                      Text(
                        text = preset.name,
                        color = RiviaTextPrimary,
                        fontWeight =
                          if (runSettings.activeModeName == preset.name) FontWeight.Bold
                          else FontWeight.SemiBold,
                      )
                    },
                    trailingIcon = {
                      Box {
                        IconButton(
                          onClick = {
                            presetForMoreMenu =
                              if (presetForMoreMenu == preset.name) null else preset.name
                          },
                          modifier =
                            Modifier.minimumInteractiveComponentSize()
                              .testTag("preset_more_${preset.name}"),
                        ) {
                          Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options for ${preset.name}",
                            tint = RiviaIconDefault,
                            modifier = Modifier.size(18.dp),
                          )
                        }

                        DropdownMenu(
                          expanded = presetForMoreMenu == preset.name,
                          onDismissRequest = { presetForMoreMenu = null },
                          modifier = Modifier.background(RiviaSurface2),
                        ) {
                          DropdownMenuItem(
                            leadingIcon = {
                              Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit preset",
                                tint = RiviaIconDefault,
                                modifier = Modifier.size(16.dp),
                              )
                            },
                            text = {
                              Text(
                                text = "Edit",
                                color = RiviaTextPrimary,
                              )
                            },
                            onClick = {
                              presetForMoreMenu = null
                              showPresetMenu = false
                              onEditModePreset(preset)
                            },
                            modifier = Modifier.testTag("preset_edit_${preset.name}"),
                          )
                          DropdownMenuItem(
                            leadingIcon = {
                              Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete preset",
                                tint = RiviaErrorRed,
                                modifier = Modifier.size(16.dp),
                              )
                            },
                            text = {
                              Text(
                                text = "Delete",
                                color = RiviaErrorRed,
                              )
                            },
                            onClick = {
                              presetForMoreMenu = null
                              showPresetMenu = false
                              onDeleteModePreset(preset)
                            },
                            modifier = Modifier.testTag("preset_delete_${preset.name}"),
                          )
                        }
                      }
                    },
                    onClick = {
                      presetForMoreMenu = null
                      showPresetMenu = false
                      onSelectModePreset(preset)
                    },
                  )
                }

                HorizontalDivider(color = RiviaBorder, thickness = 1.dp)

                DropdownMenuItem(
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Default.Add,
                      contentDescription = "Create preset",
                      tint = RiviaMint,
                      modifier = Modifier.size(18.dp),
                    )
                  },
                  text = {
                    Text(
                      text = "Create preset",
                      color = RiviaMint,
                      fontWeight = FontWeight.SemiBold,
                    )
                  },
                  onClick = {
                    presetForMoreMenu = null
                    showPresetMenu = false
                    onCreateModePreset()
                  },
                  modifier = Modifier.testTag("create_preset_menu_item"),
                )
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
                  tint = if (runSettings.autoReadAloud) RiviaIconActive else RiviaIconDefault,
                )
              }
            }
          }
        }
      }
    }

    // Confirmed Decision 1: Setup Banner on first launch before keys are entered
    if (missingKeys) {
      Surface(
        color = RiviaSurface1,
        border = BorderStroke(1.dp, RiviaBorder),
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
              text = "Rivia needs two keys before she can talk. Tap Set up keys.",
              style = MaterialTheme.typography.bodySmall,
              color = RiviaTextPrimary,
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = RiviaSurface2,
            border = BorderStroke(1.dp, RiviaBorder),
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
                tint = RiviaIconDefault,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (appUiMode == AppUiMode.RIVIA) "Set up keys" else "Settings",
                color = RiviaTextPrimary,
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
        color = RiviaSurface1,
        border = BorderStroke(1.dp, RiviaBorder),
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
              color = if (isErrorBanner) RiviaErrorRed else RiviaTextPrimary,
            )
          }
          TextButton(onClick = onDismissBanner) {
            Text("Dismiss", color = RiviaTextSecondary, fontSize = 12.sp)
          }
        }
      }
    }

    // Main content + bottom controls container:
    // Anchors Rivia's single global particle sphere and coordinates foreground messages,
    // dictation panel, and Live UI without ever duplicating the sphere.
    Box(
      modifier = Modifier.weight(1f).fillMaxWidth()
    ) {
      // Background anchor slot for the shared global sphere + empty-screen copy
      Box(
        modifier =
          Modifier.fillMaxSize()
            .padding(
              start = (24f * viewportScale.widthScale).dp,
              end = (24f * viewportScale.widthScale).dp,
              top = (24f * viewportScale.heightScale).dp,
              bottom = (146f * viewportScale.heightScale).dp,
            ),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp),
          modifier =
            Modifier.onGloballyPositioned { coords ->
              sphereColumnTopPx = coords.positionInParent().y.roundToInt()
            },
        ) {
          val sphereContentDesc =
            if (isInLiveMode) "Tap to stop Rivia and listen" else "Rivia Orb"
          val sphereClickHandler: (() -> Unit)? =
            if (isInLiveMode) {
              {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                LiveSessionManager.onOrbTapped()
              }
            } else {
              null
            }

          if (particleController != null) {
            RiviaSharedSphereSlot(
              controller = particleController,
              showDevTuning = appUiMode == AppUiMode.DEV,
              devTuningEnabled =
                !isInTypedChat &&
                  !isInLiveMode &&
                  !isInDictationPanel &&
                  buttonsAlpha.value > 0.01f,
              devTuningModifier =
                Modifier.graphicsLayer {
                  alpha = buttonsAlpha.value
                },
              contentDesc = sphereContentDesc,
              onSphereClick = sphereClickHandler,
            )
          } else {
            RiviaHomeParticleSphere(
              showDevTuning = appUiMode == AppUiMode.DEV,
              devTuningEnabled =
                !isInTypedChat &&
                  !isInLiveMode &&
                  !isInDictationPanel &&
                  buttonsAlpha.value > 0.01f,
              devTuningModifier =
                Modifier.graphicsLayer {
                  alpha = buttonsAlpha.value
                },
              contentDesc = sphereContentDesc,
              onSphereClick = sphereClickHandler,
            )
          }

          Text(
            text = "Rivia",
            style = MaterialTheme.typography.headlineMedium,
            color = RiviaTextPrimary,
            modifier =
              Modifier.graphicsLayer {
                alpha = buttonsAlpha.value
              },
          )
          Text(
            text =
              "Ask anything below, tap the mic to dictate, or start a hands-free Live voice session.",
            style = MaterialTheme.typography.bodyMedium,
            color = RiviaTextSecondary,
            modifier =
              Modifier.widthIn(max = 360.dp)
                .graphicsLayer {
                  alpha = buttonsAlpha.value
                },
          )
        }
      }

      // Foreground layer: messages list (over the dimmed blurred sphere), Live state + captions, and bottom controls
      Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          if (messages.isNotEmpty() && !isInLiveMode && liveCaptionsAlpha.value == 0f) {
            val lastAssistantId =
              remember(messages) { messages.lastOrNull { it.role == "assistant" }?.id }
            val oldestVisibleIndex = listState.firstVisibleItemIndex

            LazyColumn(
              state = listState,
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
              verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
              itemsIndexed(messages, key = { _, m -> m.id }) { idx, msg ->
                val itemEntranceModifier =
                  if (
                    !removeAnimations &&
                      msg.id in entranceMessageIds &&
                      entranceElapsedMs < 2500f
                  ) {
                    val visibleOrder = (idx - oldestVisibleIndex).coerceAtLeast(0)
                    val startMs = 450f + visibleOrder * 80f
                    Modifier.graphicsLayer {
                      val raw = ((entranceElapsedMs - startMs) / 240f).coerceIn(0f, 1f)
                      val inv = 1f - raw
                      val eased = 1f - inv * inv * inv
                      alpha = eased
                      translationY =
                        (1f - eased) * (10f * viewportScale.heightScale).dp.toPx()
                    }
                  } else {
                    Modifier
                  }

                Box(modifier = itemEntranceModifier.fillMaxWidth()) {
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
                      thumbs = thumbs,
                    )
                  }
                }
              }
            }
          }

          if (liveCaptionsAlpha.value > 0f || isInLiveMode) {
            val columnTopDp = with(density) { sphereColumnTopPx.toDp() }
            val liveOverlayTopPadding =
              (columnTopDp + (288f * viewportScale.heightScale).dp).coerceAtLeast(240.dp)
            Column(
              modifier =
                Modifier.fillMaxWidth()
                  .padding(top = liveOverlayTopPadding, start = 20.dp, end = 20.dp)
                  .verticalScroll(androidx.compose.foundation.rememberScrollState())
                  .graphicsLayer {
                    alpha = liveCaptionsAlpha.value
                  },
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              // Live state line (~500 ms fade-in)
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                StatusShape(
                  status =
                    when {
                      micPermissionDeniedReason != null || liveState.errorCaption != null ->
                        StatusType.ERROR
                      liveState.isPaused || liveState.isMuted || liveState.pausedForPhoneCall ->
                        StatusType.WARNING
                      else -> StatusType.OK
                    },
                  size = 10.dp,
                )
                Text(
                  text =
                    when {
                      micPermissionDeniedReason != null -> "Microphone needed"
                      liveState.pausedForPhoneCall -> "Paused for your call"
                      liveState.stoppedAfterThreeErrors ->
                        "Paused after errors. Tap the orb to try again."
                      liveState.isPaused -> "Paused"
                      liveState.isMuted -> "Muted"
                      liveState.orbState == OrbState.SPEAKING -> "Rivia is speaking"
                      liveState.orbState == OrbState.THINKING -> "Rivia is thinking…"
                      liveState.orbState == OrbState.LISTENING -> "Listening…"
                      else -> "Live"
                    },
                  style = MaterialTheme.typography.titleMedium,
                  color = RiviaText,
                )
              }

              if (micPermissionDeniedReason != null) {
                Surface(
                  shape = RoundedCornerShape(14.dp),
                  color = RiviaRaisedSurface,
                  border = BorderStroke(1.dp, RiviaErrorRed),
                  modifier = Modifier.widthIn(max = 420.dp),
                ) {
                  Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    StatusShape(status = StatusType.ERROR, size = 14.dp)
                    Text(
                      text = micPermissionDeniedReason!!,
                      style = MaterialTheme.typography.bodyMedium,
                      color = RiviaErrorRed,
                      textAlign = TextAlign.Center,
                    )
                    Button(
                      enabled = isInLiveMode && liveCaptionsAlpha.value > 0.01f,
                      onClick = {
                        livePermissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                      },
                      colors =
                        ButtonDefaults.buttonColors(
                          containerColor = RiviaMint,
                          contentColor = RiviaOnAccent,
                        ),
                    ) {
                      Text("Grant Microphone Permission")
                    }
                  }
                }
              } else {
                Text(
                  text =
                    if (isLiveSpeaking) "Talk over her or tap the orb to cut in"
                    else if (liveState.stoppedAfterThreeErrors) "Tap the orb to try again"
                    else "Just talk",
                  style = MaterialTheme.typography.labelMedium,
                  color = RiviaMutedText,
                )

                // Live Captions of my words and hers
                Column(
                  modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  if (liveState.userCaption.isNotBlank()) {
                    Surface(
                      shape = RoundedCornerShape(12.dp),
                      color = RiviaSurface3,
                      border = BorderStroke(1.dp, RiviaBorder),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        Text(
                          text = "YOU",
                          style = MaterialTheme.typography.labelSmall,
                          fontFamily = ChivoMonoFontFamily,
                          color = RiviaTextTertiary,
                        )
                        Text(
                          text = liveState.userCaption,
                          style = MaterialTheme.typography.bodyLarge,
                          color = RiviaText,
                        )
                      }
                    }
                  }

                  if (liveState.riviaCaption.isNotBlank()) {
                    Surface(
                      shape = RoundedCornerShape(12.dp),
                      color = RiviaSurface2,
                      border = BorderStroke(1.dp, RiviaBorder),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        Text(
                          text = "RIVIA",
                          style = MaterialTheme.typography.labelSmall,
                          fontFamily = ChivoMonoFontFamily,
                          color = RiviaTextSecondary,
                        )
                        Text(
                          text = liveState.riviaCaption,
                          style = MaterialTheme.typography.bodyLarge,
                          color = RiviaText,
                        )
                      }
                    }
                  }

                  if (!liveState.noticeCaption.isNullOrBlank()) {
                    Surface(
                      shape = RoundedCornerShape(12.dp),
                      color = RiviaRaisedSurface,
                      border = BorderStroke(1.dp, RiviaWarningAmber.copy(alpha = 0.6f)),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                      ) {
                        StatusShape(status = StatusType.WARNING, size = 10.dp)
                        Text(
                          text = liveState.noticeCaption!!,
                          style = MaterialTheme.typography.bodySmall,
                          color = RiviaWarningAmber,
                        )
                      }
                    }
                  }

                  if (!liveState.errorCaption.isNullOrBlank()) {
                    Surface(
                      shape = RoundedCornerShape(12.dp),
                      color = RiviaRaisedSurface,
                      border = BorderStroke(1.dp, RiviaErrorRed),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                      ) {
                        StatusShape(status = StatusType.ERROR, size = 10.dp)
                        Text(
                          text = liveState.errorCaption!!,
                          style = MaterialTheme.typography.bodySmall,
                          color = RiviaErrorRed,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }

        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.BottomCenter,
        ) {
          if (buttonsAlpha.value > 0f) {
            EmptyChatBottomActionButtons(
              modifier =
                Modifier.graphicsLayer {
                  alpha = buttonsAlpha.value
                },
              enabled =
                !isInTypedChat &&
                  !isInLiveMode &&
                  !isInDictationPanel &&
                  buttonsAlpha.value > 0.01f,
              onChatClick = {
                dictationExitedViaError = false
                showComposerInEmptyChat = true
                focusComposerOnReveal = true
              },
              onLiveClick = onOpenLiveMode,
              onDictateClick = {
                dictationExitedViaError = false
                priorComposerTextBeforeDictation = ""
                dictationOpenedFromComposer = false
                isInDictationPanel = true
              },
            )
          }

          if (composerAlpha.value > 0f || isInTypedChat) {
            val drawFillInSharedCanvas =
              particleController != null && !particleController.elevateAboveDrawerScrim
            ChatComposerBar(
              modifier =
                Modifier.graphicsLayer {
                  alpha = composerAlpha.value
                },
              viewportScale = viewportScale,
              drawFillInSharedCanvas = drawFillInSharedCanvas,
              controlsEnabled = isInTypedChat && composerAlpha.value > 0.01f,
              onBarBoundsChanged = { rect ->
                particleController?.let { ctrl ->
                  if (ctrl.chatBarWindowRect != rect) {
                    ctrl.chatBarWindowRect = rect
                    ctrl.requestRedraw()
                  }
                }
              },
              text = composerText,
              onTextChange = { composerText = it },
              isStreaming = isStreaming,
              onSend = {
                val toSend = composerText
                composerText = ""
                onSendMessage(toSend)
              },
              onStop = onStopStreaming,
              onOpenLive = onOpenLiveMode,
              onOpenDictation = {
                keyboardController?.hide()
                focusManager.clearFocus()
                requestKeyboardFocusNow = false
                focusComposerOnReveal = false
                dictationExitedViaError = false
                priorComposerTextBeforeDictation = composerText
                dictationOpenedFromComposer = true
                isInDictationPanel = true
              },
              focusOnLaunch = requestKeyboardFocusNow,
              onFocusConsumed = { requestKeyboardFocusNow = false },
            )
          }

          if (dictationContentAlpha.value > 0f || isInDictationPanel) {
            val drawDictateFillInSharedCanvas =
              particleController != null && !particleController.elevateAboveDrawerScrim
            SphereDictationPanel(
              modifier =
                Modifier.graphicsLayer {
                  alpha = dictationContentAlpha.value
                },
              viewportScale = viewportScale,
              drawFillInSharedCanvas = drawDictateFillInSharedCanvas,
              renderFallbackWaveformCanvas = particleController == null,
              isActive = isInDictationPanel,
              buttonsEnabled = isInDictationPanel && dictationContentAlpha.value > 0.01f,
              sttLanguage = sttLanguage,
              preferOnDeviceStt = preferOnDeviceStt,
              onVoiceLevelChanged = { lvl -> currentDictationVoiceLevel = lvl },
              onPanelBoundsChanged = { rect ->
                particleController?.let { ctrl ->
                  if (ctrl.dictationPanelWindowRect != rect) {
                    ctrl.dictationPanelWindowRect = rect
                    ctrl.requestRedraw()
                  }
                }
              },
              onWaveformBoundsChanged = { rect ->
                particleController?.let { ctrl ->
                  if (ctrl.dictationWaveformWindowRect != rect) {
                    ctrl.dictationWaveformWindowRect = rect
                    ctrl.requestRedraw()
                  }
                }
              },
              onDictationError = onDictationError,
              onFatalErrorExit = {
                // Show error, release panel's particles back to the sphere, and land on Chat end state
                requestKeyboardFocusNow = false
                focusComposerOnReveal = false
                startDictationOnReveal = false
                currentDictationVoiceLevel = 0f
                composerText = priorComposerTextBeforeDictation
                dictationExitedViaError = true
                showComposerInEmptyChat = true
                dictationOpenedFromComposer = false
                isInDictationPanel = false
              },
              onCancel = {
                requestKeyboardFocusNow = false
                focusComposerOnReveal = false
                startDictationOnReveal = false
                currentDictationVoiceLevel = 0f
                dictationExitedViaError = false
                if (
                  dictationOpenedFromComposer ||
                    messages.isNotEmpty() ||
                    priorComposerTextBeforeDictation.isNotEmpty()
                ) {
                  composerText = priorComposerTextBeforeDictation
                  showComposerInEmptyChat = true
                } else {
                  composerText = ""
                  showComposerInEmptyChat = false
                }
                dictationOpenedFromComposer = false
                isInDictationPanel = false
              },
              onDone = { finalDictatedText ->
                currentDictationVoiceLevel = 0f
                dictationExitedViaError = false
                composerText =
                  if (dictationOpenedFromComposer && priorComposerTextBeforeDictation.isNotBlank()) {
                    if (finalDictatedText.isNotBlank()) {
                      "${priorComposerTextBeforeDictation.trimEnd()} $finalDictatedText"
                    } else {
                      priorComposerTextBeforeDictation
                    }
                  } else {
                    finalDictatedText
                  }
                showComposerInEmptyChat = true
                focusComposerOnReveal = true
                dictationOpenedFromComposer = false
                isInDictationPanel = false
              },
            )
          }

          if (liveControlsAlpha.value > 0f || isInLiveMode) {
            val liveButtonsEnabled = isInLiveMode && liveControlsAlpha.value > 0.01f
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              if (!liveTipSeen) {
                Surface(
                  color = RiviaSurface1,
                  shape = RoundedCornerShape(14.dp),
                  border = BorderStroke(1.dp, RiviaBorder),
                  modifier =
                    Modifier.fillMaxWidth()
                      .padding(horizontal = 20.dp)
                      .graphicsLayer { alpha = liveControlsAlpha.value }
                      .testTag("live_first_time_tip_card"),
                ) {
                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(
                      text =
                        "Just talk. Speak over Rivia, or tap the orb, to cut in. Back keeps Live running; End stops it.",
                      style = MaterialTheme.typography.bodySmall,
                      color = RiviaTextSecondary,
                      modifier = Modifier.weight(1f),
                    )
                    TextButton(
                      enabled = liveButtonsEnabled,
                      onClick = onDismissLiveTip,
                      modifier = Modifier.testTag("live_first_time_tip_ok_button"),
                    ) {
                      Text(
                        text = "OK",
                        color = RiviaMint,
                        fontWeight = FontWeight.SemiBold,
                      )
                    }
                  }
                }
              }
              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
                    .graphicsLayer {
                      alpha = liveControlsAlpha.value
                    },
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                // Mute Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  IconButton(
                    enabled = liveButtonsEnabled,
                    onClick = {
                      view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                      LiveSessionManager.toggleMute()
                    },
                    modifier =
                      Modifier.size(60.dp)
                        .clip(CircleShape)
                        .background(if (liveState.isMuted) RiviaWarningAmber else RiviaSurface2)
                        .border(1.dp, RiviaBorder, CircleShape)
                        .testTag("live_mute_button"),
                  ) {
                    Icon(
                      imageVector =
                        if (liveState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                      contentDescription =
                        if (liveState.isMuted) "Unmute microphone" else "Mute microphone",
                      tint = if (liveState.isMuted) RiviaOnAccent else RiviaIconDefault,
                    )
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = if (liveState.isMuted) "Unmute mic" else "Mute mic",
                    style = MaterialTheme.typography.labelSmall,
                    color = RiviaMutedText,
                  )
                }

                // Pause Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  IconButton(
                    enabled = liveButtonsEnabled,
                    onClick = {
                      view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                      LiveSessionManager.togglePause()
                    },
                    modifier =
                      Modifier.size(60.dp)
                        .clip(CircleShape)
                        .background(if (liveState.isPaused) RiviaSurface3 else RiviaSurface2)
                        .border(1.dp, RiviaBorder, CircleShape)
                        .testTag("live_pause_button"),
                  ) {
                    Icon(
                      imageVector =
                        if (liveState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                      contentDescription =
                        if (liveState.isPaused) "Resume Live session" else "Pause Live session",
                      tint = if (liveState.isPaused) RiviaIconActive else RiviaIconDefault,
                    )
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = if (liveState.isPaused) "Resume Live" else "Pause Live",
                    style = MaterialTheme.typography.labelSmall,
                    color = RiviaMutedText,
                  )
                }

                // End Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  IconButton(
                    enabled = liveButtonsEnabled,
                    onClick = {
                      view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                      LiveVoiceService.stop(context)
                      requestKeyboardFocusNow = false
                      focusComposerOnReveal = false
                      startDictationOnReveal = false
                      dictationOpenedFromComposer = false
                      isInDictationPanel = false
                      onEndLiveMode()
                    },
                    modifier =
                      Modifier.size(60.dp)
                        .clip(CircleShape)
                        .background(RiviaErrorRed)
                        .testTag("live_end_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Default.CallEnd,
                      contentDescription = "End Live",
                      tint = RiviaOnAccent,
                    )
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = "End",
                    style = MaterialTheme.typography.labelSmall,
                    color = RiviaErrorRed,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
              }
            }
          }
        }
      }
    }
    }
  }
}

@Composable
private fun SphereDictationPanel(
  modifier: Modifier = Modifier,
  viewportScale: ViewportScale = ViewportScale(1f, 1f, 1f),
  drawFillInSharedCanvas: Boolean = false,
  renderFallbackWaveformCanvas: Boolean = false,
  isActive: Boolean,
  buttonsEnabled: Boolean,
  sttLanguage: String,
  preferOnDeviceStt: Boolean,
  onVoiceLevelChanged: (Float) -> Unit = {},
  onPanelBoundsChanged: (SimpleRectPx) -> Unit = {},
  onWaveformBoundsChanged: (SimpleRectPx) -> Unit = {},
  onDictationError: (String) -> Unit,
  onFatalErrorExit: () -> Unit = {},
  onCancel: () -> Unit,
  onDone: (String) -> Unit,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val removeAnimations = isSystemRemoveAnimationsEnabled(context)
  var dictatedText by remember { mutableStateOf("") }
  var baseTextBeforeDictation by remember { mutableStateOf("") }
  var voiceLevel by remember { mutableFloatStateOf(0f) }
  var elapsedSeconds by remember { mutableIntStateOf(0) }
  var cursorVisible by remember { mutableStateOf(true) }
  var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

  SideEffect {
    onVoiceLevelChanged(if (isActive) voiceLevel else 0f)
  }

  DisposableEffect(Unit) {
    onDispose {
      onVoiceLevelChanged(0f)
      speechRecognizer?.destroy()
      speechRecognizer = null
    }
  }

  fun handleFatalError(message: String) {
    voiceLevel = 0f
    onVoiceLevelChanged(0f)
    try {
      speechRecognizer?.cancel()
      speechRecognizer?.destroy()
    } catch (_: Exception) {}
    speechRecognizer = null
    onDictationError(message)
    onFatalErrorExit()
  }

  fun speechErrorDescription(code: Int): String =
    when (code) {
      SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
      SpeechRecognizer.ERROR_CLIENT -> "Client speech error"
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient microphone permissions"
      SpeechRecognizer.ERROR_NETWORK -> "Network error"
      SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
      SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch anything"
      SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
      SpeechRecognizer.ERROR_SERVER -> "Speech server error"
      SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch anything"
      else -> "Speech error"
    }

  fun startDictation() {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
      handleFatalError("Speech recognition service is not available on this device.")
      return
    }
    try {
      speechRecognizer?.destroy()
      val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
      speechRecognizer = recognizer
      baseTextBeforeDictation = if (dictatedText.isBlank()) "" else "$dictatedText "

      recognizer.setRecognitionListener(
        object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {}

          override fun onBeginningOfSpeech() {}

          override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 10f).coerceIn(0f, 1f)
            voiceLevel = voiceLevel * 0.45f + normalized * 0.55f
          }

          override fun onBufferReceived(buffer: ByteArray?) {}

          override fun onEndOfSpeech() {
            voiceLevel = 0f
          }

          override fun onError(error: Int) {
            voiceLevel = 0f
            if (
              isActive &&
                (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                  error == SpeechRecognizer.ERROR_NO_MATCH)
            ) {
              startDictation()
            } else if (error != SpeechRecognizer.ERROR_CLIENT && isActive) {
              handleFatalError("${speechErrorDescription(error)}. Try again.")
            }
          }

          override fun onPartialResults(partialResults: Bundle?) {
            val partial =
              partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (partial.isNotBlank()) {
              dictatedText = baseTextBeforeDictation + partial
            }
          }

          override fun onResults(results: Bundle?) {
            val finalSpoken =
              results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (finalSpoken.isNotBlank()) {
              dictatedText = baseTextBeforeDictation + finalSpoken
            }
            voiceLevel = 0f
            if (isActive) {
              startDictation()
            }
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
    } catch (_: Exception) {
      handleFatalError("Couldn't start listening. Try again.")
    }
  }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        if (isActive) {
          startDictation()
        }
      } else if (isActive) {
        handleFatalError("Rivia needs the microphone to take dictation.")
      }
    }

  LaunchedEffect(isActive) {
    if (isActive) {
      dictatedText = ""
      baseTextBeforeDictation = ""
      elapsedSeconds = 0
      voiceLevel = 0f
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
    } else {
      voiceLevel = 0f
      try {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
      } catch (_: Exception) {}
      speechRecognizer = null
    }
  }

  // Elapsed time counter ("Listening · 0:00")
  LaunchedEffect(isActive) {
    if (!isActive) return@LaunchedEffect
    while (true) {
      delay(1000L)
      elapsedSeconds += 1
    }
  }

  // Blinking cursor timer
  LaunchedEffect(isActive, removeAnimations) {
    if (!isActive || removeAnimations) {
      cursorVisible = true
      return@LaunchedEffect
    }
    while (true) {
      delay(530L)
      cursorVisible = !cursorVisible
    }
  }

  // Gentle RMS decay when onRmsChanged hasn't fired recently
  LaunchedEffect(isActive, removeAnimations) {
    if (!isActive || removeAnimations) return@LaunchedEffect
    var lastNanos = -1L
    while (true) {
      withFrameNanos { nanos ->
        if (lastNanos >= 0L) {
          val dt = ((nanos - lastNanos).coerceAtLeast(0L)) / 1_000_000_000f
          voiceLevel = (voiceLevel - dt * 0.9f).coerceAtLeast(0f)
        }
        lastNanos = nanos
      }
    }
  }

  // Scaled dictation panel geometry (full width - 16 dp margins, 180 dp tall, 26 dp corners on 390x844)
  val horizMarginDp = (8f * viewportScale.widthScale).dp
  val vertMarginDp = (8f * viewportScale.heightScale).dp
  val panelHeightDp = (180f * viewportScale.heightScale).coerceAtLeast(156f).dp
  val panelCornerDp = (26f * viewportScale.geomScale).dp
  val panelCornerPx = with(density) { panelCornerDp.toPx() }

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = horizMarginDp, vertical = vertMarginDp),
  ) {
    Surface(
      shape = RoundedCornerShape(panelCornerDp),
      color = if (drawFillInSharedCanvas) Color.Transparent else Color(0xFF121212),
      border = if (drawFillInSharedCanvas) null else BorderStroke(1.dp, RiviaBorder),
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(min = panelHeightDp)
          .onGloballyPositioned { coords ->
            if (coords.size.width > 0 && coords.size.height > 0) {
              val pos = coords.positionInWindow()
              onPanelBoundsChanged(
                SimpleRectPx(
                  left = pos.x,
                  top = pos.y,
                  width = coords.size.width.toFloat(),
                  height = coords.size.height.toFloat(),
                  cornerRadius = panelCornerPx,
                )
              )
            }
          }
          .testTag("sphere_dictation_panel"),
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = panelHeightDp)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        // 1. Incoming words in 16sp #F5F5F5 with a blinking cursor
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .heightIn(min = 52.dp, max = 88.dp)
              .verticalScroll(androidx.compose.foundation.rememberScrollState()),
        ) {
          Text(
            text =
              buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFFF5F5F5), fontSize = 16.sp)) {
                  append(dictatedText)
                }
                withStyle(
                  SpanStyle(
                    color = Color(0xFFF5F5F5).copy(alpha = if (cursorVisible) 1f else 0f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light,
                  )
                ) {
                  append("│")
                }
              },
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = Color(0xFFF5F5F5),
            modifier = Modifier.fillMaxWidth().testTag("dictation_panel_text"),
          )
        }

        // 2. Voice waveform band: 64 particles rendered in the single shared RiviaParticleFieldCanvas pass
        val waveHeightDp = (28f * viewportScale.heightScale).coerceAtLeast(24f).dp
        if (renderFallbackWaveformCanvas) {
          Canvas(
            modifier =
              Modifier.fillMaxWidth()
                .height(waveHeightDp)
                .testTag("dictation_voice_dots"),
          ) {
            val dotCount = 64
            val horizontalPadding = 6.dp.toPx()
            val usableWidth = (size.width - horizontalPadding * 2f).coerceAtLeast(1f)
            val stepX = usableWidth / (dotCount - 1)
            val centerY = size.height / 2f
            for (i in 0 until dotCount) {
              val u = i.toFloat() / (dotCount - 1).toFloat()
              val env = sin(u * PI.toFloat()).coerceIn(0f, 1f)
              drawCircle(
                color = Color.White.copy(alpha = 0.55f + 0.45f * env * voiceLevel),
                radius = 1.4.dp.toPx(),
                center = Offset(horizontalPadding + i * stepX, centerY),
              )
            }
          }
        } else {
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(waveHeightDp)
                .onGloballyPositioned { coords ->
                  if (coords.size.width > 0 && coords.size.height > 0) {
                    val pos = coords.positionInWindow()
                    onWaveformBoundsChanged(
                      SimpleRectPx(
                        left = pos.x,
                        top = pos.y,
                        width = coords.size.width.toFloat(),
                        height = coords.size.height.toFloat(),
                      )
                    )
                  }
                }
                .testTag("dictation_voice_dots"),
          )
        }

        // 3. Cancel (X) on the left, "Listening · 0:00" in 12sp #8F8F8F in the center, and Done on the right
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            onClick = {
              try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
              } catch (_: Exception) {}
              speechRecognizer = null
              onCancel()
            },
            enabled = buttonsEnabled,
            shape = CircleShape,
            color = RiviaSurface2,
            border = BorderStroke(1.dp, RiviaBorder),
            modifier =
              Modifier.size(40.dp)
                .minimumInteractiveComponentSize()
                .testTag("dictation_cancel_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel dictation",
                tint = RiviaTextPrimary,
                modifier = Modifier.size(18.dp),
              )
            }
          }

          val mins = elapsedSeconds / 60
          val secs = elapsedSeconds % 60
          Text(
            text = String.format(Locale.US, "Listening \u00B7 %d:%02d", mins, secs),
            fontSize = 12.sp,
            color = Color(0xFF8F8F8F),
            modifier = Modifier.testTag("dictation_elapsed_label"),
          )

          Surface(
            onClick = {
              val captured = dictatedText
              try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
              } catch (_: Exception) {}
              speechRecognizer = null
              onDone(captured)
            },
            enabled = buttonsEnabled,
            shape = RoundedCornerShape(20.dp),
            color = RiviaSurface2,
            border = BorderStroke(1.dp, RiviaBorder),
            modifier =
              Modifier.height(40.dp)
                .minimumInteractiveComponentSize()
                .testTag("dictation_done_button"),
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier.padding(horizontal = 18.dp),
            ) {
              Text(
                text = "Done",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = RiviaTextPrimary,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EmptyChatBottomActionButtons(
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onChatClick: () -> Unit,
  onLiveClick: () -> Unit,
  onDictateClick: () -> Unit,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 18.dp),
    horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.Bottom,
  ) {
    // Left: Chat. 58dp circle, chat-bubble icon, label "Chat".
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Surface(
        onClick = onChatClick,
        enabled = enabled,
        shape = CircleShape,
        color = RiviaSurface2,
        modifier =
          Modifier.size(58.dp)
            .minimumInteractiveComponentSize()
            .testTag("empty_chat_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            contentDescription = "Chat",
            tint = RiviaTextPrimary,
            modifier = Modifier.size(24.dp),
          )
        }
      }
      Text(
        text = "Chat",
        fontSize = 12.sp,
        color = RiviaTextSecondary,
      )
    }

    // Center: Live. 84dp circle, waveform icon, label "Live", sitting slightly higher than the other two.
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(bottom = 14.dp),
    ) {
      Surface(
        onClick = onLiveClick,
        enabled = enabled,
        shape = CircleShape,
        color = RiviaSurface2,
        modifier =
          Modifier.size(84.dp)
            .minimumInteractiveComponentSize()
            .testTag("empty_live_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = "Live",
            tint = RiviaTextPrimary,
            modifier = Modifier.size(34.dp),
          )
        }
      }
      Text(
        text = "Live",
        fontSize = 12.sp,
        color = RiviaTextPrimary,
      )
    }

    // Right: Dictate. 58dp circle, mic icon, label "Dictate".
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Surface(
        onClick = onDictateClick,
        enabled = enabled,
        shape = CircleShape,
        color = RiviaSurface2,
        modifier =
          Modifier.size(58.dp)
            .minimumInteractiveComponentSize()
            .testTag("empty_dictate_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Dictate",
            tint = RiviaTextPrimary,
            modifier = Modifier.size(24.dp),
          )
        }
      }
      Text(
        text = "Dictate",
        fontSize = 12.sp,
        color = RiviaTextSecondary,
      )
    }
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
      color = RiviaSurface3,
      border = BorderStroke(1.dp, RiviaBorder),
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
              color = RiviaTextSecondary,
            )
          }
        }
        Text(
          text = TextAudioTagProcessor.stripAudioTagsForScreen(message.content),
          style = MaterialTheme.typography.bodyLarge,
          color = RiviaTextPrimary,
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
          tint = RiviaIconDefault,
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
          tint = RiviaIconDefault,
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
  /** Step 6.3: thumbs on her replies. Null hides them. */
  thumbs: TrustThumbsState? = null,
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
          color = RiviaSurface2,
          border = BorderStroke(1.dp, RiviaBorder),
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
                  color = RiviaTextSecondary,
                )
              }
              Icon(
                imageVector =
                  if (thinkingExpanded) Icons.Outlined.ExpandLess
                  else Icons.Outlined.ExpandMore,
                contentDescription =
                  if (thinkingExpanded) "Collapse Thinking" else "Expand Thinking",
                tint = RiviaIconDefault,
                modifier = Modifier.size(18.dp),
              )
            }
            AnimatedVisibility(visible = thinkingExpanded) {
              Text(
                text = message.thoughtSummary,
                style = MaterialTheme.typography.bodySmall,
                color = RiviaTextSecondary,
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
          color = RiviaSurface2,
          border = BorderStroke(1.dp, RiviaBorder),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          ) {
            StatusShape(status = StatusType.WARNING, size = 8.dp)
            Text(
              text = "Still working on it…",
              style = MaterialTheme.typography.labelMedium,
              fontFamily = ChivoMonoFontFamily,
              color = RiviaWarningAmber,
            )
          }
        }
      }

      // Borderless full-width assistant markdown prose (never rendered when content is empty)
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
              text =
                if (isH9NoReplyNotice) {
                  val detail = message.errorMessage.removePrefix("No reply").trimStart(':', ' ')
                  if (detail.isBlank()) "No reply came back. Try again."
                  else "No reply came back ($detail). Try again."
                } else message.errorMessage,
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
            tint = if (isSpeaking) RiviaIconActive else RiviaIconDefault,
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
            tint = RiviaIconDefault,
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
            tint = RiviaIconDefault,
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
              tint = RiviaIconDefault,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        if (thumbs != null) TrustThumbButtons(message, thumbs)

        IconButton(
          onClick = onShowDetails,
          modifier = Modifier.size(38.dp).minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "Reply details",
            tint = RiviaTextTertiary,
            modifier = Modifier.size(16.dp),
          )
        }

        Text(
          text = message.requestId,
          style = MaterialTheme.typography.labelSmall,
          fontFamily = ChivoMonoFontFamily,
          color = RiviaTextTertiary,
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
    containerColor = RiviaSurface1,
    titleContentColor = RiviaTextPrimary,
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

        HorizontalDivider(color = RiviaBorder)

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = { showPreview = !showPreview },
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaSurface2,
                contentColor = RiviaTextPrimary,
              ),
            border = BorderStroke(1.dp, RiviaBorder),
            modifier = Modifier.testTag("chat_info_preview_request_button"),
          ) {
            Text(if (showPreview) "Hide request" else "Preview request")
          }

          if (showPreview) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              for (ch in listOf("text", "voice")) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (previewChannel == ch) RiviaMint else RiviaSurface2,
                  border =
                    BorderStroke(1.dp, if (previewChannel == ch) RiviaMint else RiviaBorder),
                  modifier =
                    Modifier.clip(RoundedCornerShape(8.dp))
                      .clickable { previewChannel = ch }
                      .testTag("chat_info_preview_channel_$ch"),
                ) {
                  Text(
                    text = if (ch == "text") "Text" else "Voice",
                    color = if (previewChannel == ch) RiviaOnAccent else RiviaTextPrimary,
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
            color = RiviaSurface2,
            border = BorderStroke(1.dp, RiviaBorder),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            androidx.compose.foundation.text.selection.SelectionContainer {
              Text(
                text = previewJson,
                fontFamily = ChivoMonoFontFamily,
                fontSize = 11.sp,
                color = RiviaTextPrimary,
                modifier = Modifier.padding(10.dp),
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = RiviaTextPrimary)
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
    containerColor = RiviaSurface1,
    titleContentColor = RiviaTextPrimary,
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
        if (
          !message.ruleEventsSummary.isNullOrBlank() &&
            message.chatKind != "Lab" &&
            !message.ruleEventsSummary.trimStart().startsWith("{")
        ) {
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
            colors =
              ButtonDefaults.outlinedButtonColors(
                containerColor = RiviaSurface2,
                contentColor = RiviaTextPrimary,
              ),
            border = BorderStroke(1.dp, RiviaBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(if (showRawSteps) "Hide Raw Steps" else "Show Raw Steps")
          }
          if (showRawSteps) {
            Surface(
              color = RiviaSurface2,
              border = BorderStroke(1.dp, RiviaBorder),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                  text = message.rawStepsJson,
                  fontFamily = ChivoMonoFontFamily,
                  fontSize = 10.sp,
                  color = RiviaTextPrimary,
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
        Text("Close", color = RiviaTextPrimary)
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
      color = RiviaTextSecondary,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.labelMedium,
      fontFamily = ChivoMonoFontFamily,
      color = RiviaTextPrimary,
    )
  }
}

@Composable
private fun ChatComposerBar(
  modifier: Modifier = Modifier,
  viewportScale: ViewportScale = ViewportScale(1f, 1f, 1f),
  drawFillInSharedCanvas: Boolean = false,
  controlsEnabled: Boolean = true,
  onBarBoundsChanged: (SimpleRectPx) -> Unit = {},
  text: String,
  onTextChange: (String) -> Unit,
  isStreaming: Boolean,
  onSend: () -> Unit,
  onStop: () -> Unit,
  onOpenLive: () -> Unit,
  onOpenDictation: () -> Unit,
  focusOnLaunch: Boolean = false,
  onFocusConsumed: () -> Unit = {},
) {
  val density = LocalDensity.current
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current

  LaunchedEffect(focusOnLaunch) {
    if (focusOnLaunch) {
      onFocusConsumed()
      focusRequester.requestFocus()
      keyboardController?.show()
    }
  }

  // Scaled message bar geometry (full width - 16 dp margins, 52 dp tall, 26 dp corners on 390x844)
  val horizMarginDp = (8f * viewportScale.widthScale).dp
  val vertMarginDp = (8f * viewportScale.heightScale).dp
  val barMinHeightDp = (52f * viewportScale.heightScale).coerceAtLeast(48f).dp
  val barCornerDp = (26f * viewportScale.geomScale).dp
  val barCornerPx = with(density) { barCornerDp.toPx() }
  val actionButtonSize = (44f * viewportScale.heightScale).coerceIn(40f, 48f).dp

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = horizMarginDp, vertical = vertMarginDp),
  ) {
    Surface(
      shape = RoundedCornerShape(barCornerDp),
      color = if (drawFillInSharedCanvas) Color.Transparent else RiviaSurface1,
      border = if (drawFillInSharedCanvas) null else BorderStroke(1.dp, RiviaBorder),
      modifier =
        Modifier.fillMaxWidth()
          .heightIn(min = barMinHeightDp)
          .onGloballyPositioned { coords ->
            if (coords.size.width > 0 && coords.size.height > 0) {
              val pos = coords.positionInWindow()
              onBarBoundsChanged(
                SimpleRectPx(
                  left = pos.x,
                  top = pos.y,
                  width = coords.size.width.toFloat(),
                  height = coords.size.height.toFloat(),
                  cornerRadius = barCornerPx,
                )
              )
            }
          },
    ) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .heightIn(min = barMinHeightDp)
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        IconButton(
          enabled = controlsEnabled,
          onClick = onOpenDictation,
          modifier =
            Modifier.size(actionButtonSize)
              .minimumInteractiveComponentSize()
              .testTag("dictate_mic_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Dictate",
            tint = RiviaTextPrimary,
          )
        }

        BasicTextField(
          value = text,
          onValueChange = onTextChange,
          enabled = controlsEnabled,
          textStyle =
            MaterialTheme.typography.bodyLarge.copy(
              color = RiviaTextPrimary,
            ),
          cursorBrush = SolidColor(RiviaMint),
          maxLines = 5,
          modifier =
            Modifier.weight(1f)
              .padding(horizontal = 4.dp, vertical = 8.dp)
              .focusRequester(focusRequester)
              .testTag("message_rivia_input"),
          decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
              if (text.isEmpty()) {
                Text(
                  text = "Message Rivia...",
                  style = MaterialTheme.typography.bodyLarge,
                  color = RiviaTextTertiary,
                )
              }
              innerTextField()
            }
          },
        )

        // Single right button: Stop while streaming, Send when typed, Live when empty
        when {
          isStreaming -> {
            IconButton(
              enabled = controlsEnabled,
              onClick = onStop,
              modifier =
                Modifier.size(actionButtonSize)
                  .minimumInteractiveComponentSize()
                  .clip(CircleShape)
                  .background(RiviaErrorRed)
                  .testTag("stop_streaming_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop reply",
                tint = RiviaOnAccent,
              )
            }
          }
          text.isNotBlank() -> {
            IconButton(
              enabled = controlsEnabled,
              onClick = onSend,
              modifier =
                Modifier.size(actionButtonSize)
                  .minimumInteractiveComponentSize()
                  .clip(CircleShape)
                  .background(RiviaSurface2)
                  .testTag("send_message_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = RiviaTextPrimary,
                modifier = Modifier.size(18.dp),
              )
            }
          }
          else -> {
            IconButton(
              enabled = controlsEnabled,
              onClick = onOpenLive,
              modifier =
                Modifier.size(actionButtonSize)
                  .minimumInteractiveComponentSize()
                  .clip(CircleShape)
                  .background(RiviaSurface2)
                  .testTag("open_live_mode_button"),
            ) {
              Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = "Start Live",
                tint = RiviaTextPrimary,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun riviaTextFieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedTextColor = RiviaTextPrimary,
    unfocusedTextColor = RiviaTextPrimary,
    focusedContainerColor = RiviaSurface2,
    unfocusedContainerColor = RiviaSurface2,
    focusedBorderColor = RiviaMint,
    unfocusedBorderColor = RiviaBorder,
    cursorColor = RiviaMint,
    focusedLabelColor = RiviaTextPrimary,
    unfocusedLabelColor = RiviaTextSecondary,
  )
