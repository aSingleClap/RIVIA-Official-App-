package com.example.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.MessageEntity
import com.example.network.TabletTrustClient
import com.example.network.TrustResult
import com.example.ui.theme.RiviaIconActive
import com.example.ui.theme.RiviaIconDefault
import com.example.viewmodel.RiviaViewModel
import kotlinx.coroutines.CoroutineScope
import java.util.UUID
import kotlinx.coroutines.launch

// Step 6.3: thumbs up and down on Rivia's replies.
// Jordan (Nov 23): "we can add a thumbs up and thumbs down to messages too".
// A thumb goes to the tablet (POST /app/trust/feedback) as your satisfaction signal for that day. It's a separate
// number from her accuracy, and in shadow it changes nothing she does. A second press on the other thumb replaces
// the first (the tablet keeps one per message). A thumb also takes the place of that day's "quiet day" half point.
// Only on Rivia chats (not Lab), and only on finished replies. Never a Gemini tool.
// Your words: a copy of each message you type in a Rivia chat goes to /app/trust/words, because your chats go from
// the phone straight to Google and never pass the tablet. Tablet code, not Rivia, decides whether it was praise
// ("good plan"), a correction ("that's wrong") or neither, and keeps only what it heard, not your message.
// Step 7.1 (Nov 24): the same copy also carries when you sent it and when her reply before it finished, so Step 7
// can see your reply speed. Step 7 reads your text on the tablet and drops it; only numbers are kept.

internal class TrustThumbsState(private val viewModel: RiviaViewModel, private val scope: CoroutineScope) {
  private val client = TabletTrustClient()
  /** message id -> 1 or -1, as the tablet last confirmed it. */
  val pressed = mutableStateMapOf<String, Int>()
  private val sending = mutableStateMapOf<String, Boolean>()
  private var loaded = false

  /** Reads your recent thumbs once, so the buttons show them after a restart. Quiet if the tablet isn't ready. */
  fun loadIfNeeded() {
    if (loaded) return
    loaded = true
    scope.launch {
      val res = client.getTrust(url(), token())
      if (res is TrustResult.Success) res.snapshot?.thumbs?.forEach { (id, v) -> pressed.putIfAbsent(id, v) }
    }
  }

  fun press(message: MessageEntity, value: Int) {
    if (sending[message.id] == true || pressed[message.id] == value) return
    sending[message.id] = true
    scope.launch {
      try {
        when (val res = client.sendThumb(url(), token(), message.id, value, message.content)) {
          is TrustResult.Success -> pressed[message.id] = value
          is TrustResult.Failure -> {
            if (res.errorCode == "http_401" && token().isNotBlank()) {
              viewModel.signOutGoogleAccount()
              viewModel.showError("Signed out, sign in again")
            } else {
              viewModel.showError("Thumb not saved: ${res.message}")
            }
          }
        }
      } finally {
        sending.remove(message.id)
      }
    }
  }

  /**
   * A copy of a message you just typed in a Rivia chat, so your words count (6.3). Quiet: if the tablet can't be
   * reached, the message still goes to Rivia as always and only this copy is lost.
   */
  fun sendWords(text: String, previousReply: String?, previousReplyAtMs: Long? = null) {
    if (text.isBlank()) return
    // Step 7.1: the send time is taken here, before the network call, so a slow tablet doesn't skew it.
    val sentAt = System.currentTimeMillis()
    scope.launch {
      client.sendWords(url(), token(), UUID.randomUUID().toString(), text, previousReply, sentAt, previousReplyAtMs)
    }
  }

  private suspend fun url() = viewModel.keyRepository.getTabletServerUrl()

  private suspend fun token() = viewModel.keyRepository.getTabletAppToken()
}

/** Two small buttons for the quiet action row under one of Rivia's replies. Draws nothing on Lab chats. */
@Composable
internal fun TrustThumbButtons(message: MessageEntity, state: TrustThumbsState) {
  if (!message.chatKind.equals("Rivia", ignoreCase = true) || message.status != "completed") return
  val now = state.pressed[message.id]
  IconButton(
    onClick = { state.press(message, 1) },
    modifier = Modifier.size(38.dp).minimumInteractiveComponentSize().testTag("thumb_up_${message.id}"),
  ) {
    Icon(
      imageVector = if (now == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
      contentDescription = if (now == 1) "You gave this a thumbs up" else "Thumbs up",
      tint = if (now == 1) RiviaIconActive else RiviaIconDefault,
      modifier = Modifier.size(16.dp),
    )
  }
  IconButton(
    onClick = { state.press(message, -1) },
    modifier = Modifier.size(38.dp).minimumInteractiveComponentSize().testTag("thumb_down_${message.id}"),
  ) {
    Icon(
      imageVector = if (now == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
      contentDescription = if (now == -1) "You gave this a thumbs down" else "Thumbs down",
      tint = if (now == -1) RiviaIconActive else RiviaIconDefault,
      modifier = Modifier.size(16.dp),
    )
  }
}
