package com.example.buttonremapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(UpdatePolicy.isNewer("v6.8", "6.7"))
        assertTrue(UpdatePolicy.isNewer("6.7.1", "6.7"))
        assertFalse(UpdatePolicy.isNewer("6.7", "6.7.0"))
        assertFalse(UpdatePolicy.isNewer("6.6", "6.7"))
        assertFalse(UpdatePolicy.isNewer("latest", "6.7"))
    }

    @Test
    fun acceptsExactlyOneContractApk() {
        val asset = ReleaseAsset(
            "button-remapping-v6.8-debug.apk",
            "https://github.com/littletaro97-arch/ButtonRemappingOverlay/releases/download/v6.8/button-remapping-v6.8-debug.apk",
            123L,
            "sha256:${"a".repeat(64)}",
        )
        assertEquals(asset, UpdatePolicy.selectApk("6.8", listOf(asset)))
        assertNull(UpdatePolicy.selectApk("6.8", listOf(asset, asset)))
        assertNull(UpdatePolicy.selectApk("6.9", listOf(asset)))
    }

    @Test
    fun requiresGithubUrlAndSha256Digest() {
        val wrongHost = ReleaseAsset(
            "button-remapping-v6.8-debug.apk",
            "https://example.com/button-remapping-v6.8-debug.apk",
            123L,
            "sha256:${"a".repeat(64)}",
        )
        assertNull(UpdatePolicy.selectApk("6.8", listOf(wrongHost)))
        assertNull(UpdatePolicy.expectedSha256("sha256:1234"))
        assertEquals("a".repeat(64), UpdatePolicy.expectedSha256("sha256:${"A".repeat(64)}"))
    }
}
