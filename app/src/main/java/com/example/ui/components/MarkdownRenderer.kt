package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ChivoFontFamily
import com.example.ui.theme.ChivoMonoFontFamily
import com.example.ui.theme.RiviaBackground
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaIconDefault
import com.example.ui.theme.RiviaMutedText
import com.example.ui.theme.RiviaRaisedSurface
import com.example.ui.theme.RiviaSurface
import com.example.ui.theme.RiviaSurface1
import com.example.ui.theme.RiviaSurface2
import com.example.ui.theme.RiviaText
import com.example.ui.theme.RiviaTextPrimary
import com.example.ui.theme.RiviaTextSecondary
import com.example.util.TextAudioTagProcessor

private sealed class MarkdownBlock {
  data class Paragraph(val text: String) : MarkdownBlock()

  data class Heading(val level: Int, val text: String) : MarkdownBlock()

  data class BulletList(val items: List<String>) : MarkdownBlock()

  data class NumberedList(val items: List<Pair<String, String>>) : MarkdownBlock()

  data class CodeBlock(val language: String, val code: String) : MarkdownBlock()

  data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
}

/**
 * Renders markdown (bold, lists, tables, code blocks with a copy button, links)
 * while stripping square-bracket audio tags like [softly] or [laughs] for on-screen display.
 */
@Composable
fun MarkdownText(
  rawText: String,
  modifier: Modifier = Modifier,
) {
  val cleanText = remember(rawText) { TextAudioTagProcessor.stripAudioTagsForScreen(rawText) }
  val blocks = remember(cleanText) { parseMarkdownBlocks(cleanText) }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    for (block in blocks) {
      when (block) {
        is MarkdownBlock.Heading -> {
          val fontSize =
            when (block.level) {
              1 -> 20.sp
              2 -> 18.sp
              else -> 16.sp
            }
          InlineMarkdownText(
            text = block.text,
            fontSizeSp = fontSize.value,
            fontWeight = FontWeight.SemiBold,
          )
        }
        is MarkdownBlock.Paragraph -> {
          InlineMarkdownText(text = block.text)
        }
        is MarkdownBlock.BulletList -> {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (item in block.items) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
              ) {
                Text(
                  text = "•",
                  color = RiviaTextSecondary,
                  fontFamily = ChivoFontFamily,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(end = 8.dp, top = 1.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                  InlineMarkdownText(text = item)
                }
              }
            }
          }
        }
        is MarkdownBlock.NumberedList -> {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((num, item) in block.items) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
              ) {
                Text(
                  text = "$num.",
                  color = RiviaTextSecondary,
                  fontFamily = ChivoMonoFontFamily,
                  fontSize = 13.sp,
                  modifier = Modifier.padding(end = 8.dp, top = 2.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                  InlineMarkdownText(text = item)
                }
              }
            }
          }
        }
        is MarkdownBlock.CodeBlock -> {
          CodeBlockCard(language = block.language, code = block.code)
        }
        is MarkdownBlock.Table -> {
          MarkdownTableCard(headers = block.headers, rows = block.rows)
        }
      }
    }
  }
}

@Composable
private fun CodeBlockCard(language: String, code: String) {
  val context = LocalContext.current
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(RiviaBackground, RoundedCornerShape(10.dp))
        .border(1.dp, RiviaBorder, RoundedCornerShape(10.dp))
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            RiviaRaisedSurface,
            RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
          )
          .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = language.ifBlank { "code" },
        style = MaterialTheme.typography.labelMedium,
        color = RiviaMutedText,
        fontFamily = ChivoMonoFontFamily,
      )
      IconButton(
        onClick = { copyToClipboard(context, "Code", code) },
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(
          imageVector = Icons.Outlined.ContentCopy,
          contentDescription = "Copy code block",
          tint = RiviaIconDefault,
          modifier = Modifier.size(18.dp),
        )
      }
    }
    HorizontalDivider(color = RiviaBorder, thickness = 1.dp)
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(12.dp)
    ) {
      Text(
        text = code,
        color = RiviaText,
        fontFamily = ChivoMonoFontFamily,
        fontSize = 13.sp,
        lineHeight = 19.sp,
      )
    }
  }
}

@Composable
private fun MarkdownTableCard(headers: List<String>, rows: List<List<String>>) {
  val scrollState = rememberScrollState()
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .horizontalScroll(scrollState)
        .background(RiviaSurface, RoundedCornerShape(8.dp))
        .border(1.dp, RiviaBorder, RoundedCornerShape(8.dp))
  ) {
    if (headers.isNotEmpty()) {
      Row(
        modifier =
          Modifier.background(
              RiviaRaisedSurface,
              RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
            )
            .padding(vertical = 8.dp, horizontal = 10.dp)
      ) {
        for (h in headers) {
          Box(modifier = Modifier.width(140.dp).padding(horizontal = 6.dp)) {
            InlineMarkdownText(
              text = h,
              fontSizeSp = 13f,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
      HorizontalDivider(color = RiviaBorder, thickness = 1.dp)
    }
    rows.forEachIndexed { idx, row ->
      Row(modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)) {
        for (colIdx in 0 until maxOf(headers.size, row.size)) {
          val cell = row.getOrNull(colIdx).orEmpty()
          Box(modifier = Modifier.width(140.dp).padding(horizontal = 6.dp)) {
            InlineMarkdownText(text = cell, fontSizeSp = 13f)
          }
        }
      }
      if (idx < rows.lastIndex) {
        HorizontalDivider(color = RiviaBorder, thickness = 0.5.dp)
      }
    }
  }
}

@Suppress("DEPRECATION")
@Composable
private fun InlineMarkdownText(
  text: String,
  fontSizeSp: Float = 15f,
  fontWeight: FontWeight = FontWeight.Normal,
) {
  val uriHandler = LocalUriHandler.current
  val annotated = remember(text) { buildInlineAnnotatedString(text) }

  ClickableText(
    text = annotated,
    style =
      MaterialTheme.typography.bodyLarge.copy(
        color = RiviaText,
        fontFamily = ChivoFontFamily,
        fontSize = fontSizeSp.sp,
        fontWeight = fontWeight,
        lineHeight = (fontSizeSp * 1.45f).sp,
      ),
    onClick = { offset ->
      annotated
        .getStringAnnotations(tag = "URL", start = offset, end = offset)
        .firstOrNull()
        ?.let { ann ->
          runCatching { uriHandler.openUri(ann.item) }
        }
    },
  )
}

private val INLINE_TOKEN_REGEX =
  Regex(
    """(`[^`\n]+`)|(\*\*[^*\n]+\*\*)|(__[^_\n]+__)|(\*[^*\n]+\*)|(\[[^\]]+\]\([^)\s]+\))|(https?://[^\s)]+)"""
  )

private fun buildInlineAnnotatedString(text: String): AnnotatedString {
  return buildAnnotatedString {
    var lastIndex = 0
    for (match in INLINE_TOKEN_REGEX.findAll(text)) {
      if (match.range.first > lastIndex) {
        append(text.substring(lastIndex, match.range.first))
      }
      val token = match.value
      when {
        token.startsWith("`") && token.endsWith("`") -> {
          withStyle(
            SpanStyle(
              fontFamily = ChivoMonoFontFamily,
              background = RiviaSurface2,
              color = RiviaTextPrimary,
              fontSize = 13.sp,
            )
          ) {
            append(" " + token.substring(1, token.length - 1) + " ")
          }
        }
        (token.startsWith("**") && token.endsWith("**")) ||
          (token.startsWith("__") && token.endsWith("__")) -> {
          withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = RiviaText)) {
            append(token.substring(2, token.length - 2))
          }
        }
        token.startsWith("*") && token.endsWith("*") -> {
          withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = RiviaText)) {
            append(token.substring(1, token.length - 1))
          }
        }
        token.startsWith("[") && token.contains("](") && token.endsWith(")") -> {
          val closeBracket = token.indexOf("](")
          val label = token.substring(1, closeBracket)
          val url = token.substring(closeBracket + 2, token.length - 1)
          pushStringAnnotation(tag = "URL", annotation = url)
          withStyle(
            SpanStyle(
              color = RiviaTextPrimary,
              textDecoration = TextDecoration.Underline,
              fontWeight = FontWeight.Medium,
            )
          ) {
            append(label)
          }
          pop()
        }
        token.startsWith("http://") || token.startsWith("https://") -> {
          pushStringAnnotation(tag = "URL", annotation = token)
          withStyle(
            SpanStyle(
              color = RiviaTextPrimary,
              textDecoration = TextDecoration.Underline,
            )
          ) {
            append(token)
          }
          pop()
        }
        else -> append(token)
      }
      lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
      append(text.substring(lastIndex))
    }
  }
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
  if (text.isBlank()) return emptyList()
  val lines = text.lines()
  val blocks = mutableListOf<MarkdownBlock>()
  var i = 0

  while (i < lines.size) {
    val line = lines[i]
    val trimmed = line.trim()

    // 1. Code block ```
    if (trimmed.startsWith("```")) {
      val lang = trimmed.removePrefix("```").trim()
      val codeSb = StringBuilder()
      i++
      while (i < lines.size && !lines[i].trim().startsWith("```")) {
        if (codeSb.isNotEmpty()) codeSb.append("\n")
        codeSb.append(lines[i])
        i++
      }
      if (i < lines.size) i++ // skip closing ```
      blocks.add(MarkdownBlock.CodeBlock(language = lang, code = codeSb.toString()))
      continue
    }

    // 2. Table (starts with | and next line is | --- | or |row|)
    if (
      trimmed.startsWith("|") &&
        trimmed.endsWith("|") &&
        i + 1 < lines.size &&
        lines[i + 1].trim().startsWith("|")
    ) {
      val tableLines = mutableListOf<String>()
      while (i < lines.size && lines[i].trim().startsWith("|")) {
        tableLines.add(lines[i].trim())
        i++
      }
      val parsedRows =
        tableLines.map { rowLine ->
          rowLine
            .removePrefix("|")
            .removeSuffix("|")
            .split("|")
            .map { it.trim() }
        }
      val isSeparatorSecond =
        parsedRows.size >= 2 &&
          parsedRows[1].all { cell -> cell.all { ch -> ch == '-' || ch == ':' || ch == ' ' } }
      val headers = if (parsedRows.isNotEmpty()) parsedRows.first() else emptyList()
      val dataRows =
        if (isSeparatorSecond) parsedRows.drop(2) else parsedRows.drop(1)
      blocks.add(MarkdownBlock.Table(headers = headers, rows = dataRows))
      continue
    }

    // 3. Heading
    val headingMatch = Regex("""^(#{1,6})\s+(.+)$""").find(trimmed)
    if (headingMatch != null) {
      blocks.add(
        MarkdownBlock.Heading(
          level = headingMatch.groupValues[1].length,
          text = headingMatch.groupValues[2],
        )
      )
      i++
      continue
    }

    // 4. Bullet list
    if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
      val items = mutableListOf<String>()
      while (i < lines.size) {
        val t = lines[i].trim()
        if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("• ")) {
          items.add(t.substring(2).trim())
          i++
        } else {
          break
        }
      }
      blocks.add(MarkdownBlock.BulletList(items))
      continue
    }

    // 5. Numbered list
    val numRegex = Regex("""^(\d+)\.\s+(.+)$""")
    if (numRegex.matches(trimmed)) {
      val items = mutableListOf<Pair<String, String>>()
      while (i < lines.size) {
        val m = numRegex.find(lines[i].trim())
        if (m != null) {
          items.add(m.groupValues[1] to m.groupValues[2])
          i++
        } else {
          break
        }
      }
      blocks.add(MarkdownBlock.NumberedList(items))
      continue
    }

    // 6. Blank line
    if (trimmed.isEmpty()) {
      i++
      continue
    }

    // 7. Paragraph
    val paraSb = StringBuilder(line)
    i++
    while (i < lines.size) {
      val nextTrim = lines[i].trim()
      if (
        nextTrim.isEmpty() ||
          nextTrim.startsWith("```") ||
          nextTrim.startsWith("#") ||
          nextTrim.startsWith("- ") ||
          nextTrim.startsWith("* ") ||
          (nextTrim.startsWith("|") && nextTrim.endsWith("|")) ||
          numRegex.matches(nextTrim)
      ) {
        break
      }
      paraSb.append("\n").append(lines[i])
      i++
    }
    blocks.add(MarkdownBlock.Paragraph(paraSb.toString()))
  }

  return blocks
}

fun copyToClipboard(context: Context, label: String, text: String) {
  val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  cm.setPrimaryClip(ClipData.newPlainText(label, text))
}
