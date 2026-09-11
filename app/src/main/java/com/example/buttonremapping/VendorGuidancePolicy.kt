package com.example.buttonremapping

object VendorGuidancePolicy {
    fun shouldShowHuaweiGuide(manufacturer: String?, brand: String?): Boolean =
        sequenceOf(manufacturer, brand)
            .filterNotNull()
            .map { it.trim().lowercase() }
            .any { it == "huawei" || it == "华为" }
}
