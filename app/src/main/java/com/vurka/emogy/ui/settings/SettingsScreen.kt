package com.vurka.emogy.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vurka.emogy.R
import com.vurka.emogy.ui.components.HowToPlayBottomSheet
import com.vurka.emogy.ui.components.EmogyPopupDialog
import com.vurka.emogy.ui.components.AppTopBar
import com.vurka.emogy.ui.theme.EmeraldGreen
import com.vurka.emogy.ui.theme.GoldStar
import com.vurka.emogy.ui.theme.RoyalBlue
import com.vurka.emogy.ui.theme.VividRed
import com.vurka.emogy.util.SoundManager
import com.vurka.emogy.viewmodel.LevelSelectionViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: LevelSelectionViewModel,
    onBackPressed: () -> Unit
) {
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val masterVolume by viewModel.masterVolume.collectAsState()
    val musicVolume by viewModel.musicVolume.collectAsState()
    val effectsVolume by viewModel.effectsVolume.collectAsState()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isAssistMode by viewModel.isAssistMode.collectAsState()
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    var showResetDialog by remember { mutableStateOf(false) }
    var showResetDone by remember { mutableStateOf(false) }
    var showHowToPlay by remember { mutableStateOf(false) }

    if (showHowToPlay) {
        HowToPlayBottomSheet(onDismiss = { showHowToPlay = false })
    }

    if (showResetDialog) {
        EmogyPopupDialog(
            visible = true,
            onDismissRequest = { showResetDialog = false },
            borderBrush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(VividRed, Color(0xFFF43F5E)))
        ) {
            Text("⚠️", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_reset_title),
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                color = VividRed
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(stringResource(R.string.settings_reset_clears), color = Color.White, fontSize = 14.sp)
                Text(stringResource(R.string.settings_reset_levels), fontSize = 13.sp, color = Color(0xFF94A3B8))
                Text(stringResource(R.string.settings_reset_coins), fontSize = 13.sp, color = Color(0xFF94A3B8))
                Text(stringResource(R.string.settings_reset_games), fontSize = 13.sp, color = Color(0xFF94A3B8))
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.settings_reset_keep,
                        com.vurka.emogy.data.LevelCatalog.TOTAL_LEVELS
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GoldStar
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    viewModel.resetAllProgress()
                    showResetDialog = false
                    showResetDone = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = VividRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("confirm_reset_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.action_reset), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showResetDialog = false }) {
                Text(stringResource(R.string.action_cancel), color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showResetDone) {
        EmogyPopupDialog(
            visible = true,
            onDismissRequest = { showResetDone = false },
            borderBrush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(EmeraldGreen, Color(0xFF10B981)))
        ) {
            Text("✅", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_reset_done_title),
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                color = EmeraldGreen
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_reset_done_body),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = Color.White
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { showResetDone = false },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("reset_done_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.action_ok), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.settings_title),
                onBack = onBackPressed
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val cardBg = if (isDark) Color(0xFF1C2541) else Color.White
            val cardBorder = if (isDark) BorderStroke(1.dp, Color(0xFF2A3A6A)) else BorderStroke(1.dp, Color(0xFFCBD5E1))

            // Sound Effects Toggle
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            stringResource(R.string.settings_sound),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(stringResource(R.string.settings_sound_desc), fontSize = 13.sp, color = Color(0xFF94A3B8))
                    }
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = { viewModel.setSoundEnabled(it) },
                        modifier = Modifier.testTag("sound_switch"),
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VividRed)
                    )
                }
            }

            // Separate master / music / effects sliders, stored in DataStore.
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_volume),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        if (soundEnabled) {
                            stringResource(R.string.settings_volume_on)
                        } else {
                            stringResource(R.string.settings_volume_off)
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(Modifier.height(6.dp))
                    VolumeSlider(
                        label = stringResource(R.string.volume_master),
                        value = masterVolume,
                        testTag = "master_volume_slider",
                        onChange = viewModel::setMasterVolume
                    )
                    VolumeSlider(
                        label = stringResource(R.string.volume_music),
                        value = musicVolume,
                        testTag = "music_volume_slider",
                        onChange = viewModel::setMusicVolume
                    )
                    VolumeSlider(
                        label = stringResource(R.string.volume_effects),
                        value = effectsVolume,
                        testTag = "effects_volume_slider",
                        onChange = { value ->
                            viewModel.setEffectsVolume(value)
                            // Instant feedback at the new level, without waiting for the flow.
                            SoundManager.setVolumes(masterVolume, value)
                            SoundManager.playTick(urgent = false, enabled = soundEnabled)
                        }
                    )
                }
            }

            // Sound preview row: lets players hear the palette before playing.
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_preview),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        if (soundEnabled) stringResource(R.string.settings_preview_on) else stringResource(R.string.settings_preview_off),
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { SoundManager.playFlip(true) },
                            enabled = soundEnabled,
                            label = { Text(stringResource(R.string.sound_flip), fontSize = 12.sp) }
                        )
                        AssistChip(
                            onClick = { SoundManager.playMatch(4, true) },
                            enabled = soundEnabled,
                            label = { Text(stringResource(R.string.sound_match), fontSize = 12.sp) }
                        )
                        AssistChip(
                            onClick = { SoundManager.playCombo(true) },
                            enabled = soundEnabled,
                            label = { Text(stringResource(R.string.sound_combo), fontSize = 12.sp) }
                        )
                        AssistChip(
                            onClick = { SoundManager.playStar(2, true) },
                            enabled = soundEnabled,
                            label = { Text(stringResource(R.string.sound_star), fontSize = 12.sp) }
                        )
                        AssistChip(
                            onClick = { SoundManager.playTick(urgent = false, enabled = true) },
                            enabled = soundEnabled,
                            label = { Text(stringResource(R.string.sound_tick), fontSize = 12.sp) }
                        )
                        AssistChip(
                            onClick = { SoundManager.playWhoosh(true) },
                            enabled = soundEnabled,
                            label = { Text(stringResource(R.string.sound_whoosh), fontSize = 12.sp) }
                        )
                        AssistChip(
                            onClick = { SoundManager.playCoin(true) },
                            enabled = soundEnabled,
                            label = { Text(stringResource(R.string.sound_coin), fontSize = 12.sp) }
                        )
                        AssistChip(
                            onClick = { SoundManager.playChest(true) },
                            enabled = soundEnabled,
                            label = { Text(stringResource(R.string.sound_chest), fontSize = 12.sp) }
                        )
                        AssistChip(
                            onClick = { SoundManager.playBoss(true) },
                            enabled = soundEnabled,
                            label = { Text(stringResource(R.string.sound_boss), fontSize = 12.sp) }
                        )
                        AssistChip(
                            onClick = { SoundManager.playVictory(true) },
                            enabled = soundEnabled,
                            label = { Text(stringResource(R.string.sound_victory), fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Haptics Toggle
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            stringResource(R.string.settings_haptics),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(stringResource(R.string.settings_haptics_desc), fontSize = 13.sp, color = Color(0xFF94A3B8))
                    }
                    Switch(
                        checked = hapticsEnabled,
                        onCheckedChange = { viewModel.setHapticsEnabled(it) },
                        modifier = Modifier.testTag("haptics_switch"),
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = RoyalBlue)
                    )
                }
            }

            // Assist Mode Toggle (opt-in: reveal one matching pair on request)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_assist),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            stringResource(R.string.settings_assist_desc),
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Switch(
                        checked = isAssistMode,
                        onCheckedChange = { viewModel.setIsAssistMode(it) },
                        modifier = Modifier.testTag("assist_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFB7185)
                        )
                    )
                }
            }

            // Theme Mode Selector (System / Light / Dark)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = cardBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_theme_mode),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(stringResource(R.string.settings_theme_desc), fontSize = 13.sp, color = Color(0xFF94A3B8))

                    Spacer(modifier = Modifier.height(14.dp))

                    val themeModes = listOf(
                        "Light" to R.string.theme_light,
                        "Dark" to R.string.theme_dark,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        themeModes.forEach { (mode, labelRes) ->
                            val isSelected = themeMode == mode
                            val bg = if (isSelected) VividRed else if (isDark) Color(0xFF24325B) else Color(0xFFE2E8F0)
                            val textColor = if (isSelected || isDark) Color.White else Color(0xFF0F172A)
                            val icon = when (mode) {
                                "Light" -> Icons.Default.LightMode
                                "Dark" -> Icons.Default.DarkMode
                                else -> Icons.Default.SettingsBrightness
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bg)
                                    .clickable { viewModel.setThemeMode(mode) }
                                    .testTag("theme_mode_$mode"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = stringResource(labelRes),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // How to Play Guide Button
            OutlinedButton(
                onClick = { showHowToPlay = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("settings_how_to_play_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.8.dp, RoyalBlue)
            ) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = RoyalBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_how_to_play), color = if (isDark) Color.White else RoyalBlue, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            // Reset Progress Button
            Button(
                onClick = { showResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("reset_progress_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9F1239))
            ) {
                Text(stringResource(R.string.settings_reset), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * A labelled volume slider.
 *
 * The local value follows the drag, and DataStore is only written when the finger lifts. That keeps
 * a long drag from producing dozens of preference writes, while the percentage still updates live.
 */
@Composable
private fun VolumeSlider(
    label: String,
    value: Float,
    testTag: String,
    onChange: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    val volumeDescription = stringResource(R.string.cd_volume, label)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "${(local * 100).roundToInt()}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .semantics { contentDescription = volumeDescription },
            colors = SliderDefaults.colors(
                thumbColor = VividRed,
                activeTrackColor = VividRed,
                inactiveTrackColor = Color(0xFFCBD5E1)
            )
        )
    }
}
