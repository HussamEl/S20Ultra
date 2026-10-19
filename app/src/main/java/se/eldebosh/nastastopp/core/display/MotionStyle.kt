package se.eldebosh.nastastopp.core.display

/**
 * The passenger display's flow's looks (297), in the order a long press on its top line (298)
 * steps through them. Every look rises from the screen's sides and bottom toward one vanishing
 * point above the top's middle, the way the car goes:
 * - [AUTO]: the look follows the speed ([FlowState.autoLook]: [WAKE] in town, [ROAD] between,
 *   [LIGHT] on a highway), crossfading from one to the next;
 * - [ROAD]: two edge rails and two dashed lane lines, the dashes running toward the horizon;
 * - [WINGS]: chevrons whose arms reach the side edges, climbing and narrowing with depth;
 * - [AURORA]: two soft ribbons of fine strands from the bottom corners, meeting at the top;
 * - [LIGHT]: streaks from both side edges streaming toward the vanishing point;
 * - [WAKE]: a V of lines with its apex at the top's middle, ripples running up along them;
 * - [OFF]: nothing is drawn.
 */
enum class MotionStyle { AUTO, ROAD, WINGS, AURORA, LIGHT, WAKE, OFF }
