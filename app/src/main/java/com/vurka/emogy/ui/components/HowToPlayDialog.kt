package com.vurka.emogy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vurka.emogy.R
import com.vurka.emogy.ui.theme.VividRed

@Composable
fun HowToPlayBottomSheet(
    onDismiss: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    EmogyPopupDialog(
        visible = true,
        onDismissRequest = onDismiss
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
                    text = stringResource(R.string.how_to_play_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_how_to_play")) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            InstructionCard(
                icon = "🃏",
                title = stringResource(R.string.howto_flip_title),
                description = stringResource(R.string.howto_flip_body),
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(12.dp))

            InstructionCard(
                icon = "💡",
                title = stringResource(R.string.howto_hints_title),
                description = stringResource(R.string.howto_hints_body),
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(12.dp))

            InstructionCard(
                icon = "💗",
                title = stringResource(R.string.howto_assist_title),
                description = stringResource(R.string.howto_assist_body),
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(12.dp))

            InstructionCard(
                icon = "⚡",
                title = stringResource(R.string.howto_streaks_title),
                description = stringResource(R.string.howto_streaks_body),
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(12.dp))

            InstructionCard(
                icon = "📅",
                title = stringResource(R.string.howto_daily_title),
                description = stringResource(R.string.howto_daily_body),
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(12.dp))

            InstructionCard(
                icon = "📖",
                title = stringResource(R.string.howto_album_title),
                description = stringResource(R.string.howto_album_body),
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(12.dp))

            InstructionCard(
                icon = "🎨",
                title = stringResource(R.string.howto_themes_title),
                description = stringResource(R.string.howto_themes_body),
                isDark = isDark
            )



            Spacer(modifier = Modifier.height(12.dp))

            InstructionCard(
                icon = "🌍",
                title = stringResource(R.string.howto_worlds_title),
                description = stringResource(R.string.howto_worlds_body),
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(12.dp))

            InstructionCard(
                icon = "⭐",
                title = stringResource(R.string.howto_stars_title),
                description = stringResource(R.string.howto_stars_body),
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(12.dp))

            InstructionCard(
                icon = "🗺️",
                title = stringResource(R.string.howto_map_title),
                description = stringResource(R.string.howto_map_body),
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("got_it_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VividRed)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.howto_got_it), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun InstructionCard(
    icon: String,
    title: String,
    description: String,
    isDark: Boolean
) {
    val cardBg = if (isDark) Color(0xFF24325B) else Color(0xFFF1F5F9)
    val cardBorder = if (isDark) BorderStroke(1.dp, Color(0xFF2A3A6A)) else BorderStroke(1.dp, Color(0xFFCBD5E1))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF1C2541) else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                    lineHeight = 18.sp
                )
            }
        }
    }
}
