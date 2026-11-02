package com.vurka.emogy

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the store-facing documents. Gradle runs unit tests with the module directory (`app/`) as
 * the working directory, so the repository root is one level up.
 */
class LegalDocsTest {

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "docs/privacy-policy.md").exists() }
    private val docNames = listOf("privacy-policy.md", "terms-of-service.md", "credits.md")

    private fun doc(name: String) = File(root, "docs/$name").readText().replace("\r\n", "\n")
    private fun asset(name: String) = File(root, "app/src/main/assets/legal/$name").readText().replace("\r\n", "\n")

    @Test
    fun inAppCopiesMatchThePublishedDocs() {
        docNames.forEach { name ->
            assertEquals("$name differs: run ./gradlew syncLegalDocs", doc(name), asset(name))
        }
    }

    @Test
    fun privacyPolicyCoversWhatPlayReviewersLookFor() {
        val text = doc("privacy-policy.md")
        listOf(
            "Information we collect", "Information stored on your device", "How to delete your data",
            "Android backup", "Permissions", "Children", "Contact", "VIBRATE"
        ).forEach { assertTrue("policy must mention '$it'", text.contains(it, ignoreCase = true)) }
        assertTrue(text.contains("com.vurka.emogy"))
    }

    @Test
    fun termsCoverVirtualItemsLiabilityAndGoverningLaw() {
        val text = doc("terms-of-service.md")
        listOf("Virtual items", "Disclaimer", "Limitation of liability", "Governing law", "Contact")
            .forEach { assertTrue("terms must mention '$it'", text.contains(it, ignoreCase = true)) }
    }

    @Test
    fun onlyTheKnownPlaceholdersAreUsed() {
        val allowed = setOf("REPLACE_WITH_DEVELOPER_NAME", "REPLACE_WITH_SUPPORT_EMAIL", "REPLACE_WITH_COUNTRY_OR_STATE")
        val found = Regex("REPLACE_WITH_[A-Z_]+").findAll(
            docNames.joinToString("\n") { doc(it) } + doc("index.md")
        ).map { it.value }.toSet()
        assertTrue("unknown placeholders: ${found - allowed}", allowed.containsAll(found))
    }

    @Test
    fun manifestRequestsOnlyVibrateSoThePolicyStaysTrue() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        val permissions = Regex("uses-permission[^>]*android:name=\"([^\"]+)\"").findAll(manifest)
            .map { it.groupValues[1] }.toList()
        assertEquals(listOf("android.permission.VIBRATE"), permissions)
        assertFalse(manifest.contains("INTERNET"))
        assertTrue(manifest.contains("android:allowBackup=\"true\""))
    }

    @Test
    fun backupRulesCoverBothDataStoreFiles() {
        listOf("backup_rules.xml", "data_extraction_rules.xml").forEach { name ->
            val xml = File(root, "app/src/main/res/xml/$name").readText()
            assertTrue(name, xml.contains("datastore/memory_match_prefs.preferences_pb"))
            assertTrue(name, xml.contains("datastore/theme_prefs.preferences_pb"))
        }
    }
}
