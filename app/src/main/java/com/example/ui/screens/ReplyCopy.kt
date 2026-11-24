package com.example.ui.screens

import com.example.data.MessageEntity
import com.example.network.LayerSwitches
import com.example.network.TabletAppraisalClient
import com.example.viewmodel.RiviaViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Step 3.6: a copy of each finished typed reply of hers goes to the tablet (/app/appraisal/reply) once it's on your
// screen, so the shadow rewording can be tried there. Rivia text chats only: never Lab chats, never Live voice
// messages. Each reply is sent once. Replies already in a chat when it's first opened (after a restart, or switching
// chats) are marked as seen and never sent, so old chats don't flood the tablet. Quiet: if the tablet can't be
// reached, that one copy is lost and nothing else changes. Her reply on your screen is never touched.
// Jordan (Nov 24): "Split", "Keep it all". Layer switches: sent only while Layer 3 is on (LayerSwitches.replyCopyOn).

internal class ReplyCopyState(private val viewModel: RiviaViewModel, private val scope: CoroutineScope) {
  private val client = TabletAppraisalClient()
  private val seen = HashSet<String>()
  private val openedChats = HashSet<String>()

  /** Call each time the active chat's messages change. */
  fun onMessages(messages: List<MessageEntity>, isLab: Boolean) {
    val chatId = messages.firstOrNull()?.chatId ?: return
    val finished = messages.filter { it.role == "assistant" && it.status == "completed" }
    if (openedChats.add(chatId)) {
      // First look at this chat: what's already finished was seen before, not new.
      finished.forEach { seen.add(it.id) }
      return
    }
    if (isLab) return
    for (m in finished) {
      if (!seen.add(m.id)) continue
      // Layer switches: no copy at all while Layer 3 is off on the tablet. Marked as seen, so turning it back on
      // doesn't send old replies.
      if (!LayerSwitches.replyCopyOn()) continue
      if (!m.chatKind.equals("Rivia", ignoreCase = true) || m.channel != "text") continue
      val text = m.content.trim()
      if (text.isBlank()) continue
      send(m, text)
    }
  }

  private fun send(m: MessageEntity, text: String) {
    val at = m.createdAt + m.totalTimeMs.coerceAtLeast(0L)
    val toolOnly = usedTool(m) && wordCount(text) <= TOOL_ONLY_WORDS
    scope.launch {
      client.sendReply(
        viewModel.keyRepository.getTabletServerUrl(),
        viewModel.keyRepository.getTabletAppToken(),
        m.id,
        text,
        at,
        toolOnly,
      )
    }
  }

  companion object {
    /** A short reply right after one of her tools ran is a tool report, not chat. */
    const val TOOL_ONLY_WORDS = 25

    fun usedTool(m: MessageEntity): Boolean = m.rawStepsJson.contains("\"function_call\"")

    fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }
  }
}
