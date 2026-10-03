package se.eldebosh.nastastopp.core.nav

/**
 * The order of a few trips, for the driver to weigh on the tablet's map: when each is reached and
 * how late ([plan]), and the best order ([best]): of every order that keeps each passenger's
 * pick-up before their drop-off, the one with the fewest minutes late, then the shortest. The
 * travel times are Google's between the places ([RoutesApi.parseMatrix]); a stop takes
 * [DWELL_SECONDS] (getting in or out).
 */
object OrderPlanner {
    /** A trip: its booked time (minutes of the day), whether it is a pick-up, and its passenger's number. */
    data class Trip(val booked: Int?, val pickUp: Boolean?, val rider: Int?)

    /**
     * An order ([order]: the trips' indexes) with when each trip is reached ([arrivals]: seconds
     * from now, in the trips' own indexes), its minutes late in all, and its seconds to the last.
     */
    data class Plan(val order: List<Int>, val arrivals: List<Int>, val lateMinutes: Int, val seconds: Int) {
        /** No later and shorter, or less late. */
        fun betterThan(other: Plan): Boolean = lateMinutes < other.lateMinutes || (lateMinutes == other.lateMinutes && seconds < other.seconds)
    }

    /** Getting in or out at a stop. */
    const val DWELL_SECONDS = 120

    /** More trips than this are not tried in every order. */
    const val MAX_TRIPS = 7

    /**
     * [order] timed: [seconds] are the travel times from the vehicle (0) or trip i (i + 1) to trip
     * j, [now] the seconds of the day. Null when a way is missing.
     */
    fun plan(order: List<Int>, trips: List<Trip>, seconds: Array<IntArray>, now: Int): Plan? {
        val arrivals = IntArray(trips.size)
        var t = 0
        var from = 0
        var late = 0
        for (k in order) {
            val leg = seconds.getOrNull(from)?.getOrNull(k) ?: return null
            if (leg == RoutesApi.NO_WAY) return null
            t += leg
            arrivals[k] = t
            late += lateMinutes(trips[k].booked, now + t)
            t += DWELL_SECONDS
            from = k + 1
        }
        return Plan(order, arrivals.toList(), late, t - DWELL_SECONDS)
    }

    /**
     * Every passenger in [order] is picked up before they are dropped off. A drop-off with no
     * pick-up of theirs before it is a passenger already in the car: there are as many of those as
     * the passenger's drop-offs outnumber their pick-ups (two trips of one passenger in a day).
     */
    fun allowed(order: List<Int>, trips: List<Trip>): Boolean {
        val inCar = HashMap<Int, Int>()
        for (k in order) {
            val rider = trips[k].rider ?: continue
            when (trips[k].pickUp) {
                false -> inCar[rider] = (inCar[rider] ?: 0) + 1
                true -> inCar[rider] = (inCar[rider] ?: 0) - 1
                null -> Unit
            }
        }
        inCar.replaceAll { _, n -> n.coerceAtLeast(0) }
        for (k in order) {
            val rider = trips[k].rider ?: continue
            when (trips[k].pickUp) {
                true -> inCar[rider] = (inCar[rider] ?: 0) + 1
                false -> {
                    val n = inCar[rider] ?: 0
                    if (n == 0) return false
                    inCar[rider] = n - 1
                }
                null -> Unit
            }
        }
        return true
    }

    /**
     * The best allowed order of [trips] (at most [MAX_TRIPS]): the one given ([current]) unless
     * another allowed order is better; another one when [current] drops a passenger off before
     * picking them up. Null when no allowed order can be timed.
     */
    fun best(trips: List<Trip>, seconds: Array<IntArray>, now: Int, current: List<Int> = trips.indices.toList()): Plan? {
        var best = if (allowed(current, trips)) plan(current, trips, seconds, now) else null
        if (trips.size > MAX_TRIPS) return best
        permutations(trips.indices.toList()) { order ->
            if (!allowed(order, trips)) return@permutations
            val p = plan(order, trips, seconds, now) ?: return@permutations
            if (best?.let { p.betterThan(it) } != false) best = p
        }
        return best
    }

    /** Minutes after [booked] (minutes of the day) at [at] (seconds of the day), across midnight too. */
    fun lateMinutes(booked: Int?, at: Int): Int {
        if (booked == null) return 0
        var diff = at - booked * 60
        if (diff > DAY / 2) diff -= DAY
        if (diff < -DAY / 2) diff += DAY
        return if (diff <= 0) 0 else (diff + 59) / 60
    }

    private fun permutations(items: List<Int>, each: (List<Int>) -> Unit) {
        fun go(prefix: MutableList<Int>, rest: MutableList<Int>) {
            if (rest.isEmpty()) {
                each(prefix.toList())
                return
            }
            for (i in rest.indices) {
                val x = rest.removeAt(i)
                prefix.add(x)
                go(prefix, rest)
                prefix.removeAt(prefix.size - 1)
                rest.add(i, x)
            }
        }
        go(ArrayList(), items.toMutableList())
    }

    private const val DAY = 24 * 3600
}
