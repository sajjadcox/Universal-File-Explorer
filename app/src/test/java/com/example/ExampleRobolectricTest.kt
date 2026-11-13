package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.FileCategory
import com.example.domain.model.StorageStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Universal File Explorer", appName)
  }

  @Test
  fun `format bytes correctly`() {
    assertEquals("0 B", StorageStats.formatBytes(0L))
    assertEquals("1.0 KB", StorageStats.formatBytes(1024L))
    assertEquals("1.0 MB", StorageStats.formatBytes(1024L * 1024L))
  }

  @Test
  fun `file category match check`() {
    assertTrue(FileCategory.DOCUMENTS.matches("pdf", "application/pdf"))
    assertTrue(FileCategory.IMAGES.matches("png", "image/png"))
    assertTrue(FileCategory.AUDIO.matches("mp3", "audio/mpeg"))
  }
}
