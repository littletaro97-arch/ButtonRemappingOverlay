package com.example.buttonremapping

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorGuidancePolicyTest {
    @Test
    fun huaweiDevicesShowHuaweiGuide() {
        assertTrue(VendorGuidancePolicy.shouldShowHuaweiGuide("HUAWEI", "HUAWEI"))
        assertTrue(VendorGuidancePolicy.shouldShowHuaweiGuide("华为", null))
    }

    @Test
    fun honorDevicesDoNotShowHuaweiGuide() {
        assertFalse(VendorGuidancePolicy.shouldShowHuaweiGuide("HONOR", "HONOR"))
    }

    @Test
    fun otherVendorsDoNotShowHuaweiGuide() {
        assertFalse(VendorGuidancePolicy.shouldShowHuaweiGuide("samsung", "galaxy"))
        assertFalse(VendorGuidancePolicy.shouldShowHuaweiGuide(null, null))
    }
}
