package se.eldebosh.nastastopp.overlay

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import se.eldebosh.nastastopp.core.link.LinkMessage
import se.eldebosh.nastastopp.core.parse.TripKind
import se.eldebosh.nastastopp.core.route.Announcement
import se.eldebosh.nastastopp.geo.CurrentStreet
import se.eldebosh.nastastopp.link.DisplayLinkClient
import se.eldebosh.nastastopp.route.RouteController
import se.eldebosh.nastastopp.tts.Announcer

/** The next trip as the floating panel shows it. */
data class PanelTrip(
    /** Scheduled time ("14:33") or null. */
    val time: String?,
    /** The stop's street and number (a tap says them). */
    val street: String,
    val town: String?,
    /** The passenger's first + last name: only on the driver's own phone, never on a tablet. */
    val name: String?,
    val kind: TripKind?,
    /** Trips done, and trips left with this one. */
    val done: Int,
    val left: Int,
    /** The stop's area, for the street bar while no street is known. */
    val area: String?,
)

/** Where the floating panel's trip comes from and what its buttons do. */
interface PanelSource {
    /** Emits whenever what the panel shows may have changed. */
    val changes: Flow<Unit>

    /** The next trip of an active route, or null for no panel. */
    fun trip(): PanelTrip?

    /** The vehicle's street and speed: on the phone that has the location, null anywhere else. */
    val street: CurrentStreet?

    fun next()

    /** False when there is no trip to go back to. */
    fun back(): Boolean

    fun repeat()

    /** Says the next stop's street and number (a tap on them). */
    fun sayStop()

    /** Says the street the vehicle is on, or repeats the announcement while none is known. */
    fun sayStreet()
}

/** The phone: its own route, its location's street and speed, and the passenger's name. */
class RoutePanelSource(private val controller: RouteController, override val street: CurrentStreet) : PanelSource {
    override val changes: Flow<Unit> = combine(controller.route, street.state) { _, _ -> }

    override fun trip(): PanelTrip? {
        val r = controller.route.value ?: return null
        if (!r.active) return null
        val current = r.stops.firstOrNull() ?: return null
        return PanelTrip(
            time = current.time,
            street = controller.streetOf(current),
            // The town only, without the postal code.
            town = current.displayText.substringAfter(',', "").replace(POSTAL_CODE, "").trim(' ', ',').ifEmpty { null },
            name = current.name,
            kind = current.kind,
            done = r.completedCount,
            left = r.stops.size,
            area = controller.spokenName(current),
        )
    }

    override fun next() = controller.next()

    override fun back(): Boolean = controller.back()

    override fun repeat() = controller.repeat()

    override fun sayStop() {
        controller.speakStopStreet()
    }

    override fun sayStreet() {
        if (!controller.speakStreet(street.state.value)) controller.repeat()
    }

    private companion object {
        val POSTAL_CODE = Regex("""\b\d{3}\s?\d{2}\b""")
    }
}

/**
 * A tablet: the phone's route as its passenger display receives it (never the passenger's name,
 * never the vehicle's street or speed). Next, Back and Repeat go to the phone over the link.
 */
class LinkPanelSource(private val client: DisplayLinkClient, private val announcer: Announcer) : PanelSource {
    override val changes: Flow<Unit> = combine(client.snapshot, client.state) { _, _ -> }

    override val street: CurrentStreet? = null

    override fun trip(): PanelTrip? {
        if (client.state.value.status != DisplayLinkClient.Status.CONNECTED) return null
        val s = client.snapshot.value?.takeIf { it.active } ?: return null
        val current = s.current ?: return null
        return PanelTrip(
            time = current.time,
            street = current.title,
            town = current.subtitle,
            name = null,
            kind = current.kind,
            done = s.completed,
            left = s.remaining,
            area = null,
        )
    }

    override fun next() = client.send(LinkMessage.Command.Action.NEXT)

    override fun back(): Boolean {
        if ((client.snapshot.value?.completed ?: 0) == 0) return false
        client.send(LinkMessage.Command.Action.BACK)
        return true
    }

    override fun repeat() = client.send(LinkMessage.Command.Action.REPEAT)

    override fun sayStop() {
        trip()?.let { announcer.speak(Announcement(it.street, null)) }
    }

    override fun sayStreet() = repeat()
}
