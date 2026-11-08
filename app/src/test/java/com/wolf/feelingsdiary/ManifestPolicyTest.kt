package com.wolf.feelingsdiary

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
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
    fun backupDisabled() {
        assertEquals(0, ctx.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun deviceTransferAndCloudBackupExcludeEverything() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
        assertTrue(manifest.contains("""android:fullBackupContent="false""""))
        val rules = File("src/main/res/xml/data_extraction_rules.xml").readText()
        listOf("cloud-backup", "device-transfer").forEach { section ->
            val body = Regex("<$section[^>]*>(.*?)</$section>", RegexOption.DOT_MATCHES_ALL).find(rules)?.groupValues?.get(1)
            assertTrue("$section missing", body != null)
            listOf("root", "file", "database", "sharedpref", "external").forEach { domain ->
                assertTrue("$section must exclude $domain", body!!.contains("""<exclude domain="$domain""""))
            }
        }
    }
}
