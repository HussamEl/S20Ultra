package se.eldebosh.nastastopp.core.parse

import kotlinx.serialization.Serializable

/** What happens at a stop, as the dispatch list (YouDrive) labels it. Unknown = null. */
@Serializable
enum class TripKind {
    /** Leaving the depot: the day's start point (grey in YouDrive), not a stop to drive to. */
    PULL_OUT,

    /** Picking a passenger up (green in YouDrive). */
    PICK_UP,

    /** Dropping a passenger off (white in YouDrive). */
    DROP_OFF,

    /** Back to the depot at the end of the day (grey). */
    PULL_IN,
}

object TripKinds {

    /**
     * The kind if [line] is a kind label ("Pick-up", "Drop off", "12:48 Pick-up", "Hämtning"),
     * else null. Only whole labels count, so a sentence that merely contains the word does not.
     */
    fun labelIn(line: String): TripKind? = LABELS[TextNorm.fold(line).filter { it.isLetter() }]

    private val LABELS = mapOf(
        "pullout" to TripKind.PULL_OUT,
        "pickup" to TripKind.PICK_UP,
        "dropoff" to TripKind.DROP_OFF,
        "pullin" to TripKind.PULL_IN,
        "hamtning" to TripKind.PICK_UP,
        "upphamtning" to TripKind.PICK_UP,
        "lamning" to TripKind.DROP_OFF,
        "avlamning" to TripKind.DROP_OFF,
    )
}
