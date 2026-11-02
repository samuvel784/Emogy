package com.vurka.emogy.ui.legal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vurka.emogy.BuildConfig
import com.vurka.emogy.R
import com.vurka.emogy.ui.components.AppTopBar
import com.vurka.emogy.ui.theme.BrightCyan
import com.vurka.emogy.ui.theme.EmeraldGreen
import com.vurka.emogy.ui.theme.GoldStar
import com.vurka.emogy.ui.theme.RoyalBlue
import com.vurka.emogy.util.LegalBlock
import com.vurka.emogy.util.LegalMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The three offline documents shipped in `assets/legal`. */
enum class LegalDoc(val assetName: String, @StringRes val titleRes: Int) {
    Privacy("privacy-policy.md", R.string.legal_privacy),
    Terms("terms-of-service.md", R.string.legal_terms),
    Credits("credits.md", R.string.legal_credits),
}

/**
 * About & Legal: version, offline copies of the Privacy Policy, Terms and Credits, plus optional
 * share / rate / contact hand-offs. Everything works without a network or an account.
 */
@Composable
fun LegalScreen(onBackPressed: () -> Unit) {
    var open by remember { mutableStateOf<LegalDoc?>(null) }

    // Back from a document returns to the list; back from the list leaves the screen.
    BackHandler(enabled = open != null) { open = null }

    val doc = open
    if (doc == null) {
        LegalList(onBackPressed = onBackPressed, onOpen = { open = it })
    } else {
        LegalDocument(doc = doc, onBack = { open = null })
    }
}

@Composable
private fun LegalList(onBackPressed: () -> Unit, onOpen: (LegalDoc) -> Unit) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.legal_title), onBack = onBackPressed) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("legal_screen"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🧩", fontSize = 44.sp)
                    Text(
                        stringResource(R.string.app_name),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        stringResource(R.string.legal_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        fontSize = 12.sp,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.testTag("legal_version")
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.legal_section_documents), isDark) }
            item {
                LegalRow(Icons.Default.PrivacyTip, R.string.legal_privacy, R.string.legal_privacy_sub, RoyalBlue, "legal_privacy", isDark) {
                    onOpen(LegalDoc.Privacy)
                }
            }
            item {
                LegalRow(Icons.Default.Gavel, R.string.legal_terms, R.string.legal_terms_sub, GoldStar, "legal_terms", isDark) {
                    onOpen(LegalDoc.Terms)
                }
            }
            item {
                LegalRow(Icons.Default.Description, R.string.legal_credits, R.string.legal_credits_sub, EmeraldGreen, "legal_credits", isDark) {
                    onOpen(LegalDoc.Credits)
                }
            }

            item { SectionLabel(stringResource(R.string.legal_section_community), isDark) }
            item {
                LegalRow(Icons.Default.Star, R.string.legal_rate, R.string.legal_rate_sub, Color(0xFFEC4899), "legal_rate", isDark) {
                    openPlayListing(context)
                }
            }
            item {
                LegalRow(Icons.Default.Share, R.string.legal_share, R.string.legal_share_sub, BrightCyan, "legal_share", isDark) {
                    shareApp(context)
                }
            }
            item {
                LegalRow(Icons.Default.Email, R.string.legal_contact, R.string.legal_contact_sub, Color(0xFF94A3B8), "legal_contact", isDark) {
                    emailSupport(context)
                }
            }
        }
    }
}

@Composable
private fun LegalDocument(doc: LegalDoc, onBack: () -> Unit) {
    val context = LocalContext.current
    var blocks by remember(doc) { mutableStateOf<List<LegalBlock>?>(null) }
    var failed by remember(doc) { mutableStateOf(false) }

    LaunchedEffect(doc) {
        val text = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("legal/${doc.assetName}").bufferedReader().use { it.readText() }
            }.getOrNull()
        }
        if (text == null) failed = true else blocks = LegalMarkdown.parse(text)
    }

    Scaffold(
        topBar = { AppTopBar(title = stringResource(doc.titleRes), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val loaded = blocks
        when {
            failed -> Text(
                stringResource(R.string.legal_load_error),
                modifier = Modifier.padding(innerPadding).padding(24.dp).testTag("legal_error"),
                color = MaterialTheme.colorScheme.onBackground
            )
            loaded == null -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
                    .testTag("legal_document"),
                contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(loaded) { block -> BlockView(block) }
            }
        }
    }
}

@Composable
private fun BlockView(block: LegalBlock) {
    val body = MaterialTheme.colorScheme.onBackground
    when (block) {
        is LegalBlock.Title -> Text(
            block.text, fontSize = 24.sp, fontWeight = FontWeight.Black, color = body,
            modifier = Modifier.padding(top = 4.dp)
        )
        is LegalBlock.Heading -> Text(
            block.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = RoyalBlue,
            modifier = Modifier.padding(top = 10.dp)
        )
        is LegalBlock.Paragraph -> Text(styled(block.text), fontSize = 14.sp, lineHeight = 21.sp, color = body)
        is LegalBlock.Bullet -> Row {
            Text("•", fontSize = 14.sp, color = body, modifier = Modifier.width(18.dp))
            Text(styled(block.text), fontSize = 14.sp, lineHeight = 21.sp, color = body)
        }
    }
}

private fun styled(text: String) = buildAnnotatedString {
    LegalMarkdown.spans(text).forEach { span ->
        when {
            span.bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
            span.code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(span.text) }
            else -> append(span.text)
        }
    }
}

@Composable
private fun SectionLabel(text: String, isDark: Boolean) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 2.sp,
        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    )
}

@Composable
private fun LegalRow(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes subtitle: Int,
    accent: Color,
    testTag: String,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isDark) Color(0xFF1E293B).copy(alpha = 0.85f) else Color.White)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(subtitle), fontSize = 12.sp, color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B))
        }
        Text("›", fontSize = 22.sp, color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8))
    }
}

// ---- Hand-offs to other apps. None of these need the INTERNET permission. -----------------

private fun launchSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No app can handle it (for example a device without a mail app): nothing to do.
    }
}

internal fun openPlayListing(context: Context) {
    val id = context.packageName
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id"))
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id"))
    try {
        context.startActivity(market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        launchSafely(context, web)
    }
}

internal fun shareApp(context: Context) {
    val link = "https://play.google.com/store/apps/details?id=${context.packageName}"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.app_name))
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.legal_share_text, link))
    }
    launchSafely(context, Intent.createChooser(send, context.getString(R.string.legal_share)))
}

internal fun emailSupport(context: Context) {
    val mail = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.support_email)))
        putExtra(
            Intent.EXTRA_SUBJECT,
            context.getString(R.string.legal_contact_subject, context.getString(R.string.app_name), BuildConfig.VERSION_NAME)
        )
    }
    launchSafely(context, mail)
}
