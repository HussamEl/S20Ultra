package se.eldebosh.nastastopp.core.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowStateTest {
    private var clockMs = 0f

    /** Drives [seconds] of frames at about 60 a second, at [kmh] by GPS (null: no position). */
    private fun FlowState.drive(
        seconds: Float,
        kmh: Float?,
        level: Float = 0.1f,
        awake: Boolean = true,
        scene: FlowScene = FlowScene.HOME,
        signs: Boolean = false,
        each: FlowState.() -> Unit = {},
    ) {
        val frames = (seconds * 1000f / FRAME_MS).toInt()
        repeat(frames) {
            clockMs += FRAME_MS
            step(clockMs / 1000f, FRAME_MS, level, kmh?.let { it / 3.6f }, awake, scene, signs)
            each()
        }
    }

    @Test
    fun transparentAtAStandAndFullerWithTheSpeed() {
        val s = FlowState()
        s.drive(5f, 0f)
        assertEquals(0f, s.shown, 0f)
        assertFalse(s.visible(MotionStyle.AUTO))
        s.drive(8f, 20f)
        val town = s.shown
        assertTrue("a town's speed shows some of it: $town", town > 0.1f && town < 0.6f)
        s.drive(8f, 60f)
        assertTrue("fully shown from about 50 km/h: ${s.shown}", s.shown > 0.99f)
        assertTrue(s.visible(MotionStyle.ROAD))
        // It flows forward with the speed.
        val before = s.travel
        s.drive(1f, 60f)
        assertEquals("60 km/h runs two units a second", 2.0, s.travel - before, 0.05)
    }

    @Test
    fun autoFollowsTheSpeedWithHysteresis() {
        val s = FlowState()
        assertEquals(MotionStyle.WAKE, s.autoLook)
        s.drive(10f, 38f)
        assertEquals("under 40 km/h it stays the town's look", MotionStyle.WAKE, s.autoLook)
        s.drive(10f, 45f)
        assertEquals(MotionStyle.ROAD, s.autoLook)
        s.drive(10f, 33f)
        assertEquals("not back down before 30 km/h", MotionStyle.ROAD, s.autoLook)
        s.drive(10f, 28f)
        assertEquals(MotionStyle.WAKE, s.autoLook)
        s.drive(10f, 100f)
        assertEquals(MotionStyle.LIGHT, s.autoLook)
        s.drive(10f, 72f)
        assertEquals("not back down before 70 km/h", MotionStyle.LIGHT, s.autoLook)
        s.drive(10f, 65f)
        assertEquals(MotionStyle.ROAD, s.autoLook)
    }

    @Test
    fun autoNeverFlapsAtABoundary() {
        val s = FlowState()
        s.drive(10f, 45f)
        assertEquals(MotionStyle.ROAD, s.autoLook)
        // The GPS's speed jumps around 40 km/h, and around 80 km/h, for a minute each.
        var changes = 0
        var seen = s.autoLook
        val watch: FlowState.() -> Unit = {
            if (autoLook != seen) {
                changes++
                seen = autoLook
            }
        }
        repeat(60) { s.drive(1f, if (it % 2 == 0) 37f else 43f, each = watch) }
        assertEquals("no switch around 40 km/h", 0, changes)
        s.drive(10f, 90f, each = watch)
        assertEquals(MotionStyle.LIGHT, s.autoLook)
        changes = 0
        repeat(60) { s.drive(1f, if (it % 2 == 0) 76f else 84f, each = watch) }
        assertEquals("no switch around 80 km/h", 0, changes)
    }

    @Test
    fun autoCrossfadesFromOneLookToTheNext() {
        val s = FlowState()
        s.drive(10f, 60f)
        assertEquals(MotionStyle.ROAD, s.autoLook)
        assertTrue(s.weight(MotionStyle.AUTO, MotionStyle.ROAD) > 0.99f)
        s.drive(3f, 110f)
        assertEquals(MotionStyle.LIGHT, s.autoLook)
        val road = s.weight(MotionStyle.AUTO, MotionStyle.ROAD)
        val light = s.weight(MotionStyle.AUTO, MotionStyle.LIGHT)
        assertTrue("both show while it crossfades: road $road, light $light", road > 0.02f && light > 0.3f)
        s.drive(10f, 110f)
        assertTrue(s.weight(MotionStyle.AUTO, MotionStyle.LIGHT) > 0.99f)
        assertTrue(s.weight(MotionStyle.AUTO, MotionStyle.ROAD) < 0.01f)
        // AUTO never draws the looks it does not choose; a chosen look is drawn alone.
        assertEquals(0f, s.weight(MotionStyle.AUTO, MotionStyle.WINGS), 0f)
        assertEquals(1f, s.weight(MotionStyle.AURORA, MotionStyle.AURORA), 0f)
        assertEquals(0f, s.weight(MotionStyle.AURORA, MotionStyle.LIGHT), 0f)
    }

    @Test
    fun speedingUpStretchesAndBrakingCompresses() {
        val s = FlowState()
        s.drive(10f, 30f)
        val steady = s.vanishY
        var speedingUp = 0f
        s.drive(1.5f, 70f) { speedingUp = maxOf(speedingUp, accel) }
        assertTrue("speeding up: $speedingUp m/s²", speedingUp > 1f)
        assertTrue("the marks stretch: ${s.push}", s.push > 0.2f)
        assertTrue("the vanishing point comes lower: ${s.vanishY} > $steady", s.vanishY > steady + 0.005f)
        s.drive(10f, 70f)
        assertEquals("a steady speed is no acceleration", 0f, s.accel, 0.05f)
        var braking = 0f
        s.drive(1.5f, 20f) { braking = minOf(braking, accel) }
        assertTrue("braking: $braking m/s²", braking < -1f)
        assertTrue(s.push < -0.2f)
    }

    @Test
    fun theMapHidesItAndTheTextKeepsItToTheEdges() {
        val s = FlowState()
        s.drive(10f, 60f)
        assertEquals(1f, s.strength, 0.01f)
        val home = s.clearing
        s.drive(4f, 60f, scene = FlowScene.FOCUS)
        assertEquals(FlowScene.FOCUS.strength, s.strength, 0.01f)
        assertEquals(FlowScene.FOCUS.edges, s.edges, 0.01f)
        assertTrue("at home the middle is cleared, not all of it: $home", home > 0.8f && home < 1f)
        // The whole box the large time (90 % x 70 %) or address (86 % x 74 %) can fill is cut in full.
        assertEquals("a large text clears the middle in full", 1f, s.clearing, 0f)
        assertTrue("its width: ${s.clearHalfWidth}", s.clearHalfWidth >= 0.9f)
        assertTrue("its height: ${s.clearHalfHeight}", s.clearHalfHeight >= 0.74f)
        s.drive(4f, 60f, scene = FlowScene.MOMENT)
        assertEquals("a moment large in the middle too", 1f, s.clearing, 0f)
        assertTrue(s.clearHalfWidth >= 0.9f && s.clearHalfHeight >= 0.74f)
        s.drive(5f, 60f, scene = FlowScene.MAP)
        assertTrue("hidden on the driver's map: ${s.shown}", s.shown < FlowState.HIDDEN)
        assertFalse(s.visible(MotionStyle.AUTO))
        // Back home, it eases in again within a second or so.
        s.drive(0.3f, 60f)
        val easing = s.shown
        assertTrue("it eases back: $easing", easing > 0.1f && easing < 0.9f)
        s.drive(5f, 60f)
        assertTrue(s.shown > 0.99f)
        s.drive(5f, 60f, signs = true)
        assertEquals("the top line's signs dim it a little", FlowState.SIGNS_SHOWN, s.shown, 0.01f)
    }

    @Test
    fun offDrawsNothing() {
        val s = FlowState()
        s.drive(10f, 100f)
        assertTrue(s.visible(MotionStyle.AUTO))
        assertFalse(s.visible(MotionStyle.OFF))
        for (look in MotionStyle.entries) assertEquals(0f, s.alpha(MotionStyle.OFF, look), 0f)
    }

    @Test
    fun theShakingStandsForTheSpeedWithoutGps() {
        val s = FlowState()
        s.drive(5f, null, level = 0f)
        assertEquals("a still tablet without a position: nothing", 0f, s.shown, 0f)
        s.drive(8f, null, level = 0.6f)
        assertTrue("a shaking car is moving: ${s.kmh} km/h", s.kmh > 30f && s.kmh <= 40.01f)
        assertTrue(s.shown > 0.5f)
        assertTrue(s.shake > 0.55f)
        // A GPS speed, once there, counts instead.
        s.drive(8f, 90f, level = 0.6f)
        assertTrue(s.kmh > 85f)
    }

    @Test
    fun itFadesOnceTheCarHasStoodTwoMinutes() {
        val s = FlowState()
        s.drive(10f, null, level = 0.6f)
        assertTrue(s.shown > 0.5f)
        s.drive(10f, null, level = 0.6f, awake = false)
        assertTrue("faded: ${s.shown}", s.shown < FlowState.HIDDEN)
        s.drive(4f, null, level = 0.6f, awake = true)
        assertTrue("back as the car moves: ${s.shown}", s.shown > 0.5f)
    }

    @Test
    fun aLongGapBetweenFramesCountsAsAShortOne() {
        val s = FlowState()
        s.step(0f, 0f, 0f, 25f, true, FlowScene.HOME, false)
        s.step(60f, 60_000f, 0f, 25f, true, FlowScene.HOME, false)
        assertTrue("one step never jumps to the speed: ${s.kmh}", s.kmh < 20f)
        assertTrue(s.travel < 0.2)
    }

    private companion object {
        const val FRAME_MS = 16f
    }

    /** Without a position the shaking stands for the speed, but it is never read as speeding up or braking. */
    @Test
    fun theShakingAndALostPositionAreNoAcceleration() {
        val s = FlowState()
        repeat(20) { i -> s.drive(0.5f, null, level = if (i % 2 == 0) 0.15f else 0.45f) }
        assertTrue("shaking without GPS: ${s.push}", kotlin.math.abs(s.push) < 0.1f)
        s.drive(10f, 90f)
        s.drive(1f, null, level = 0.1f)
        assertTrue("the position lost at 90 km/h: ${s.push}", kotlin.math.abs(s.push) < 0.1f)
        s.drive(2f, 90f)
        assertTrue("the position back at 90 km/h: ${s.push}", kotlin.math.abs(s.push) < 0.1f)
        s.drive(3f, 60f)
        assertTrue("braking by GPS still reads as braking: ${s.push}", s.push < -0.1f)
    }

    /** The driver's map hides the flow within about a second, so it is not drawn under the map. */
    @Test
    fun theMapHidesItWithinASecond() {
        val s = FlowState()
        s.drive(10f, 60f)
        assertTrue(s.visible(MotionStyle.AUTO))
        s.drive(1.2f, 60f, scene = FlowScene.MAP)
        assertFalse("still drawn under the map: ${s.shown}", s.visible(MotionStyle.AUTO))
        assertEquals(0f, s.strength, 0f)
        s.drive(4f, 60f, awake = false)
        s.drive(3f, 60f, awake = false, scene = FlowScene.HOME)
        assertEquals("stood two minutes: it ends at nothing", 0f, s.wake, 0f)
    }
}
