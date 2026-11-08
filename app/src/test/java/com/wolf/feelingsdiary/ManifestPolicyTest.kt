package com.wolf.feelingsdiary

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.backup.DiaryBackupAgent
import com.wolf.feelingsdiary.reminder.BootReceiver
import com.wolf.feelingsdiary.reminder.ReminderReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ManifestPolicyTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun noInternetPermission() {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions.orEmpty()
        assertFalse(permissions.contains(Manifest.permission.INTERNET))
        assertFalse(permissions.any { it.endsWith("EXACT_ALARM") })
    }

    @Test
    fun permissionsAreExactlyTheAllowedSet() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val own = Regex("""<uses-permission\s+android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
        assertEquals(setOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECEIVE_BOOT_COMPLETED), own)
    }

    @Test
    fun onlyBootReceiverIsExported() {
        val pm = ctx.packageManager
        val reminder = pm.getReceiverInfo(ComponentName(ctx, ReminderReceiver::class.java), 0)
        val boot = pm.getReceiverInfo(ComponentName(ctx, BootReceiver::class.java), 0)
        assertFalse(reminder.exported)
        assertTrue(boot.exported)
    }

    @Test
    fun backupAgentDeclared() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:backupAgent=".backup.DiaryBackupAgent""""))
        assertTrue(manifest.contains("""android:fullBackupOnly="true""""))
        assertTrue(manifest.contains("""android:fullBackupContent="@xml/backup_rules""""))
        assertTrue(manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
        // Backup is allowed only so the agent runs; the agent itself decides whether anything leaves the device.
        assertTrue(ctx.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertEquals(DiaryBackupAgent::class.java.name, ctx.applicationInfo.backupAgentName)
    }

    @Test
    fun rulesIncludeOnlyDatabaseAndSettings() {
        val expected = listOf("database" to ".", "file" to "datastore/settings.preferences_pb")
        val extraction = File("src/main/res/xml/data_extraction_rules.xml").readText()
        val sections =
            listOf("cloud-backup", "device-transfer").map { section ->
                val body = Regex("<$section[^>]*>(.*?)</$section>", RegexOption.DOT_MATCHES_ALL).find(extraction)?.groupValues?.get(1)
                section to requireNotNull(body) { "$section missing" }
            } + ("backup_rules.xml" to File("src/main/res/xml/backup_rules.xml").readText())
        sections.forEach { (name, body) ->
            val rules = Regex("""<(include|exclude)\b[^>]*>""").findAll(body).map { it.value }.toList()
            val includes =
                rules.map { rule ->
                    assertTrue("$name: only includes allowed, found $rule", rule.startsWith("<include"))
                    val domain = Regex("""domain="([^"]+)"""").find(rule)!!.groupValues[1]
                    val path = Regex("""path="([^"]+)"""").find(rule)!!.groupValues[1]
                    domain to path
                }
            assertEquals(name, expected, includes)
        }
    }
}
