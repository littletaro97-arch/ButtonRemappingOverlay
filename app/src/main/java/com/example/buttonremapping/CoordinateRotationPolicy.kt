package com.example.buttonremapping

/**
 * Layouts are saved in logical screen coordinates, not in physical device
 * orientation. Opposite rotations with the same width/height orientation must
 * therefore be equivalent: 90 and 270 are both landscape, while 0 and 180
 * are both portrait.
 */
object CoordinateRotationPolicy {
    fun canonical(rotation: Int): Int = if (rotation.mod(2) == 0) 0 else 1

    fun quarterTurns(fromRotation: Int, toRotation: Int): Int =
        (canonical(toRotation) - canonical(fromRotation) + 4).mod(4)
}
