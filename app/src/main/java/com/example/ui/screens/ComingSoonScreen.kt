package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppUiMode
import com.example.ui.theme.ChivoMonoFontFamily
import com.example.ui.theme.RiviaBackground
import com.example.ui.theme.RiviaBorder
import com.example.ui.theme.RiviaIconDefault
import com.example.ui.theme.RiviaMint
import com.example.ui.theme.RiviaMutedText
import com.example.ui.theme.RiviaOnAccent
import com.example.ui.theme.RiviaSurface1
import com.example.ui.theme.RiviaSurface2
import com.example.ui.theme.RiviaText
import com.example.ui.theme.RiviaTextSecondary

@Composable
fun ComingSoonScreen(
  title: String,
  subtitle: String,
  isPipeline: Boolean,
  appUiMode: AppUiMode,
  onBack: () -> Unit,
) {
  BackHandler { onBack() }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(RiviaBackground)
        .statusBarsPadding()
        .navigationBarsPadding()
  ) {
    // Top bar
    Surface(color = RiviaBackground, modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onBack,
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("coming_soon_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back to chat",
              tint = RiviaText,
            )
          }
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = RiviaText,
            fontWeight = FontWeight.SemiBold,
          )
        }

        Surface(
          shape = RoundedCornerShape(50),
          color = RiviaSurface2,
          border = BorderStroke(1.dp, RiviaBorder),
          modifier = Modifier.padding(end = 12.dp),
        ) {
          Text(
            text = appUiMode.displayName.uppercase(),
            color = RiviaTextSecondary,
            fontFamily = ChivoMonoFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }
    }

    // Main content
    Box(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        shape = RoundedCornerShape(24.dp),
        color = RiviaSurface2,
        border = BorderStroke(1.dp, RiviaBorder),
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = RiviaSurface1,
            border = BorderStroke(1.dp, RiviaBorder),
            modifier = Modifier.size(64.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector =
                  if (isPipeline) Icons.Outlined.AccountTree else Icons.Outlined.Tune,
                contentDescription = title,
                tint = RiviaIconDefault,
                modifier = Modifier.size(30.dp),
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(50),
            color = RiviaSurface1,
            border = BorderStroke(1.dp, RiviaBorder),
          ) {
            Text(
              text = "COMING SOON",
              color = RiviaTextSecondary,
              fontFamily = ChivoMonoFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
          }

          Text(
            text = "Coming soon",
            style = MaterialTheme.typography.headlineSmall,
            color = RiviaText,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("coming_soon_heading"),
          )

          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = RiviaMutedText,
            textAlign = TextAlign.Center,
          )

          Spacer(modifier = Modifier.height(6.dp))

          Button(
            onClick = onBack,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = RiviaMint,
                contentColor = RiviaOnAccent,
              ),
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("coming_soon_return_button"),
          ) {
            Text(
              text = "Back to Chat",
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(vertical = 4.dp),
            )
          }
        }
      }
    }
  }
}
