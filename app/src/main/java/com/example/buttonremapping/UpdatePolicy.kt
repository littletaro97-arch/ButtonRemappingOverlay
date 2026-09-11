package com.example.buttonremapping

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val digest: String,
)

object UpdatePolicy {
    private val versionPattern = Regex("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?$")
    private val sha256Pattern = Regex("^sha256:([0-9a-fA-F]{64})$")

    fun normalizedVersion(tag: String): String? {
        val match = versionPattern.matchEntire(tag.trim()) ?: return null
        return listOf(match.groupValues[1], match.groupValues[2], match.groupValues[3])
            .filter { it.isNotEmpty() }
            .joinToString(".")
    }

    fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = versionParts(candidate) ?: return false
        val currentParts = versionParts(current) ?: return false
        val width = maxOf(candidateParts.size, currentParts.size)
        return (0 until width)
            .map { (candidateParts.getOrNull(it) ?: 0) - (currentParts.getOrNull(it) ?: 0) }
            .firstOrNull { it != 0 }
            ?.let { it > 0 } == true
    }

    fun selectApk(version: String, assets: List<ReleaseAsset>): ReleaseAsset? {
        val acceptedNames = setOf(
            "button-remapping-v$version-debug.apk",
            "button-remapping-v$version-release.apk",
        )
        val matches = assets.filter { it.name in acceptedNames }
        return matches.singleOrNull()?.takeIf {
            it.size > 0 &&
                it.downloadUrl.startsWith(
                    "https://github.com/littletaro97-arch/ButtonRemappingOverlay/releases/download/",
                ) &&
                expectedSha256(it.digest) != null
        }
    }

    fun expectedSha256(digest: String): String? =
        sha256Pattern.matchEntire(digest.trim())?.groupValues?.get(1)?.lowercase()

    private fun versionParts(value: String): List<Int>? {
        val normalized = normalizedVersion(value) ?: return null
        return normalized.split('.').map { it.toInt() }
    }
}
