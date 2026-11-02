package com.vurka.emogy.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vurka.emogy.R
import com.vurka.emogy.ui.theme.GoldStar
import com.vurka.emogy.ui.theme.VividRed

/**
 * One-time first-run introduction. It is intentionally short: three ideas, a Start button and a
 * link to the full rules. Dismissing it (button, swipe or back) counts as seen.
 */
@Composable
fun WelcomeSheet(
    onDismiss: () -> Unit,
    onHowToPlay: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    EmogyPopupDialog(
        visible = true,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("welcome_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.welcome_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("welcome_close_button")) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_close_welcome),
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            Text(
                text = stringResource(R.string.welcome_intro),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                fontSize = 13.sp,
                textAlign = TextAlign.Start,
                color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
            )

            InstructionCard(
                icon = "🃏",
                title = stringResource(R.string.welcome_pair_title),
                description = stringResource(R.string.welcome_pair_body),
                isDark = isDark
            )
            Spacer(Modifier.height(10.dp))
            InstructionCard(
                icon = "⭐",
                title = stringResource(R.string.welcome_best_title),
                description = stringResource(R.string.welcome_best_body),
                isDark = isDark
            )
            Spacer(Modifier.height(10.dp))
            InstructionCard(
                icon = "📅",
                title = stringResource(R.string.welcome_more_title),
                description = stringResource(R.string.welcome_more_body),
                isDark = isDark
            )

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("welcome_start_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VividRed)
            ) {
                Text(
                    stringResource(R.string.welcome_start),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            TextButton(
                onClick = onHowToPlay,
                modifier = Modifier.testTag("welcome_rules_button")
            ) {
                Text(
                    stringResource(R.string.welcome_rules),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) GoldStar else Color(0xFFB45309)
                )
            }
        }
    }
}
