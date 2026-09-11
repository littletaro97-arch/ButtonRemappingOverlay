package com.example.buttonremapping

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateRotationPolicyTest {
    @Test
    fun oppositeLandscapeRotationsShareLogicalCoordinates() {
        assertEquals(0, CoordinateRotationPolicy.quarterTurns(1, 3))
        assertEquals(0, CoordinateRotationPolicy.quarterTurns(3, 1))
    }

    @Test
    fun oppositePortraitRotationsShareLogicalCoordinates() {
        assertEquals(0, CoordinateRotationPolicy.quarterTurns(0, 2))
        assertEquals(0, CoordinateRotationPolicy.quarterTurns(2, 0))
    }

    @Test
    fun portraitLandscapeConversionRemainsInvertible() {
        assertEquals(1, CoordinateRotationPolicy.quarterTurns(0, 1))
        assertEquals(3, CoordinateRotationPolicy.quarterTurns(1, 0))
    }
}
