package com.example.phototrail.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotDetectionTest {
    @Test
    fun detectsCommonScreenshotFileNames() {
        assertTrue(isScreenshotMedia("Screenshot_20261016_092800.jpg", null))
        assertTrue(isScreenshotMedia("screen_capture_42.png", null))
        assertTrue(isScreenshotMedia("스크린샷 2026-10-16.png", null))
    }

    @Test
    fun detectsScreenshotFoldersWithGenericFileNames() {
        assertTrue(isScreenshotMedia("1000027366.jpg", "Pictures/Screenshots/"))
    }

    @Test
    fun keepsRegularCameraPhotosEligibleForAlbums() {
        assertFalse(isScreenshotMedia("IMG_20261016_092800.jpg", "DCIM/Camera/"))
    }
}
