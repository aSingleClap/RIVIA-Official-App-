package com.example.network

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Layer switches (Nov 24): the one place on the phone that knows which layers and pieces are on.
// Tablet switches come from GET /app/toggles (TabletTogglesClient) and are saved on the tablet, which checks them
// each time the work would run. Phone-only switches (1.5 mood dial, 2.4 controls, 6.3 thumbs) are saved on this
// phone and start on. What needs what comes from the tablet's list only; nothing here keeps its own copy.
// When the tablet has no switches yet (or can't be reached before the first read), everything behaves exactly as it
// did before switches: the words copy is sent, health_status stays, the dial and thumbs show.

object LayerSwitches {
  const val PHONE_DIAL = "p1.5"
  const val PHONE_CONTROLS = "p2.4"
  const val PHONE_THUMBS = "p6.3.thumbs"
  val PHONE_IDS = listOf(PHONE_DIAL, PHONE_CONTROLS, PHONE_THUMBS)

  /** The phone switches, for when the tablet can't send its list (or leaves one out). */
  val PHONE_FALLBACK: List<TogglePiece> =
    listOf(
      phonePiece(PHONE_DIAL, 1, "1.5", "1.5 Manual mood dial"),
      phonePiece(PHONE_CONTROLS, 2, "2.4", "2.4 Mood & baseline controls"),
      phonePiece(PHONE_THUMBS, 6, "6.3", "Thumbs on her replies"),
    )

  private fun phonePiece(id: String, layer: Int, step: String, title: String) =
    TogglePiece(
      id = id,
      layer = layer,
      step = step,
      title = title,
      where = "phone",
      switchable = true,
      value = null,
      state = "phone",
      needs = emptyList(),
      neededBy = emptyList(),
      why = "",
      wouldAlsoTurnOff = emptyList(),
    )

  /** Tablet switches the phone carries out by leaving a Gemini tool out: switch id -> tool name. */
  private val TOOL_FOR = mapOf("l4.8" to "health_status")

  private const val PREFS = "rivia_layer_switches"

  private val client = TabletTogglesClient()

  /** The tablet's last answer, or null if it hasn't been read or has no switches. */
  var snapshot by mutableStateOf<TogglesSnapshot?>(null)
    private set

  /** "" when the last read went fine, otherwise why the tablet's switches couldn't be read. */
  var tabletMessage by mutableStateOf("")
    private set

  /** True when the tablet answered that it has no switches route yet. */
  var notInstalled by mutableStateOf(false)
    private set

  private val phone = mutableStateMapOf<String, Boolean>()
  @Volatile private var phoneLoaded = false

  /** Reads the phone-only switches once. Missing ones are on. */
  fun loadPhone(context: Context) {
    if (phoneLoaded) return
    val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    for (id in PHONE_IDS) phone[id] = prefs.getBoolean(id, true)
    phoneLoaded = true
  }

  /** Saves one phone-only switch on this phone. */
  fun setPhone(context: Context, id: String, on: Boolean) {
    if (id !in PHONE_IDS) return
    phone[id] = on
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(id, on).apply()
  }

  /** Your setting for a phone-only switch (on until you switch it off). */
  fun phoneValue(id: String): Boolean = phone[id] ?: true

  fun piece(id: String): TogglePiece? = snapshot?.pieces?.firstOrNull { it.id == id }

  fun title(id: String): String =
    piece(id)?.title ?: PHONE_FALLBACK.firstOrNull { it.id == id }?.title ?: id

  /** Every piece the Layers screen can show: the tablet's list, plus any phone switch the tablet didn't list. */
  fun allPieces(): List<TogglePiece> {
    val tablet = snapshot?.pieces.orEmpty()
    return tablet + PHONE_FALLBACK.filter { f -> tablet.none { it.id == f.id } }
  }

  /** A step row's test tag -> its step: "vad_step_1_5" -> "1.5", "style_step_8_2b" -> "8.2b". Null if not a step row. */
  fun stepOf(testTag: String): String? {
    val i = testTag.indexOf("_step_")
    if (i < 0) return null
    return testTag.substring(i + 6).replace('_', '.').ifBlank { null }
  }

  /** The switches that sit on one step row, in the tablet's order. */
  fun piecesForRow(testTag: String): List<TogglePiece> {
    val step = stepOf(testTag) ?: return emptyList()
    return allPieces().filter { it.step == step }
  }

  /** A layer's whole-layer switches (no step), shown under the layer's name. */
  fun layerPieces(layer: Int): List<TogglePiece> = allPieces().filter { it.layer == layer && it.step == null }

  /**
   * True when a tablet piece runs (on, on but waiting, or an always-on safety piece). A piece the tablet didn't list
   * counts as on. Always-on counts as running, so a piece that needs a safety piece (3.6 rewording needs 3.8 fact check)
   * isn't greyed out while that safety piece runs.
   */
  fun tabletOn(id: String): Boolean {
    val p = piece(id) ?: return true
    return p.state == "on" || p.state == "waiting" || p.state == "always_on"
  }

  /** The first piece this one needs (hard) that is off, or null. Phone pieces only depend on tablet pieces. */
  fun offParent(id: String): TogglePiece? {
    val p = piece(id) ?: return null
    for (link in p.needs) {
      if (!link.hard) continue
      val parent = piece(link.id) ?: continue
      val parentOn = if (parent.where == "phone") phoneValue(parent.id) else tabletOn(parent.id)
      if (!parentOn) return parent
    }
    return null
  }

  /** A phone-only switch is on when you left it on and nothing it needs is off. */
  fun phoneOn(id: String): Boolean = phoneValue(id) && offParent(id) == null

  /** The copy of each message you type goes to the tablet while 6.3 words or Layer 7 is on. Unknown: sent. */
  fun wordsCopyOn(): Boolean {
    if (snapshot == null) return true
    return tabletOn("l6.words") || tabletOn("l7")
  }

  /**
   * Step 3: the copy of each finished typed reply of hers goes to the tablet while Layer 3 is on. Unknown (no answer
   * from the tablet yet, or Layer 3 not listed): sent, same as the words copy.
   */
  fun replyCopyOn(): Boolean = tabletOn("l3")

  /** True when a Gemini tool's switch is off on the tablet (4.8 health_status). Unknown: false, the tool stays. */
  fun toolHidden(toolName: String): Boolean {
    val id = TOOL_FOR.entries.firstOrNull { it.value == toolName }?.key ?: return false
    if (snapshot == null || piece(id) == null) return false
    return !tabletOn(id)
  }

  /** Reads the tablet's switches. Quiet: on failure the last good answer is kept. */
  suspend fun refresh(serverUrl: String, appToken: String): TogglesResult {
    val res = client.getToggles(serverUrl, appToken)
    take(res)
    return res
  }

  /** Saves one tablet switch, then keeps the tablet's new answer. A refused change leaves everything as it was. */
  suspend fun setTablet(serverUrl: String, appToken: String, id: String, on: Boolean): TogglesResult {
    val res = client.setToggle(serverUrl, appToken, id, on)
    if (res is TogglesResult.Success) take(res)
    return res
  }

  private fun take(res: TogglesResult) {
    when (res) {
      is TogglesResult.Success -> {
        snapshot = res.snapshot
        tabletMessage = ""
        notInstalled = false
      }
      is TogglesResult.Failure -> {
        notInstalled = res.errorCode == TabletTogglesClient.NOT_INSTALLED_CODE
        tabletMessage = res.message
        if (notInstalled) snapshot = null
      }
    }
  }
}
