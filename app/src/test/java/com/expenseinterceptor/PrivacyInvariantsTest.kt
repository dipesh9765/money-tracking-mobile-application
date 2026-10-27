package com.expenseinterceptor

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The README promises "no network, no backup, nothing logged". These tests make the promise fail
 * the build if someone breaks it.
 */
class PrivacyInvariantsTest {

  private val manifest = File("src/main/AndroidManifest.xml").readText()
  private val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

  @Test
  fun `the app declares no network permissions`() {
    listOf("INTERNET", "ACCESS_NETWORK_STATE", "ACCESS_WIFI_STATE").forEach {
      assertFalse("$it must not be declared", manifest.contains("android.permission.$it"))
    }
  }

  @Test
  fun `no network or analytics libraries are on the classpath`() {
    val build =
      File("build.gradle.kts").readText() + File("../gradle/libs.versions.toml").readText()
    listOf("okhttp", "retrofit", "firebase", "crashlytics", "analytics", "sentry", "volley")
      .forEach {
        assertFalse("$it must not be a dependency", build.contains(it, ignoreCase = true))
      }
  }

  @Test
  fun `backup and device transfer are disabled`() {
    assertTrue(manifest.contains("""android:allowBackup="false""""))
    assertTrue(manifest.contains("android:dataExtractionRules"))
    assertTrue(manifest.contains("""android:usesCleartextTraffic="false""""))
    val rules = File("src/main/res/xml/data_extraction_rules.xml").readText()
    assertEquals(
      "cloud and transfer must each exclude the database",
      2,
      Regex("""<exclude domain="database" />""").findAll(rules).count()
    )
  }

  @Test
  fun `the sms receiver is only reachable by the system`() {
    val receiver = manifest.substringAfter("<receiver").substringBefore("</receiver>")
    assertTrue(receiver.contains("""android:permission="android.permission.BROADCAST_SMS""""))
  }

  @Test
  fun `only the launcher activity is exported`() {
    val activities = Regex("""<activity[^>]*>""").findAll(manifest).map { it.value }.toList()
    assertEquals(2, activities.size)
    val quickAdd = activities.first { it.contains("QuickAddActivity") }
    assertTrue(quickAdd.contains("""android:exported="false""""))
    val fileProvider = manifest.substringAfter("<provider").substringBefore("</provider>")
    assertTrue(fileProvider.contains("""android:exported="false""""))
  }

  @Test
  fun `source code never logs through android Log or println`() {
    val offenders =
      sources.filter { file ->
        file.readLines().any { line ->
          val code = line.substringBefore("//").trim()
          code.startsWith("println(") ||
            code.contains("printStackTrace") ||
            Regex("""\bLog\.(d|v|i)\(""").containsMatchIn(code)
        }
      }
    assertTrue("debug logging found in: ${offenders.map { it.name }}", offenders.isEmpty())
  }

  @Test
  fun `warning logs never interpolate message content`() {
    val sensitive = Regex("""\$\{?(body|sms|amount|merchant|sender|text|originalSms)\b""")
    val logCalls =
      sources.flatMap { f ->
        f.readLines().filter { it.contains("Log.w(") || it.contains("Log.e(") }
      }
    assertTrue(logCalls.none { sensitive.containsMatchIn(it) })
  }
}
