package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.media.MediaMetaInfo
import com.example.media.normalizeMediaMimeType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SwarDub", appName)
  }

  @Test
  fun `normalize mime and quality labels cover low to 4k formats`() {
    assertEquals(
      "video/x-matroska",
      normalizeMediaMimeType("application/octet-stream", "scene_hd.mkv", "video/mp4")
    )
    assertEquals(
      "video/3gpp",
      normalizeMediaMimeType("", "clip_low.3gp", "video/mp4")
    )

    val lowClip = MediaMetaInfo(
      fileName = "clip_low.3gp",
      mimeType = "video/3gpp",
      durationMs = 12_000L,
      videoWidth = 320,
      videoHeight = 240,
      fileSizeBytes = 512_000L
    )
    assertEquals("3GP", lowClip.formatLabel)
    assertEquals("240p Low", lowClip.qualityLabel)

    val uhdClip = MediaMetaInfo(
      fileName = "movie_scene.mkv",
      mimeType = "video/x-matroska",
      durationMs = 45_000L,
      videoWidth = 3840,
      videoHeight = 2160,
      fileSizeBytes = 25L * 1024L * 1024L
    )
    assertEquals("MKV", uhdClip.formatLabel)
    assertEquals("4K UHD", uhdClip.qualityLabel)
  }
}
