package se.eldebosh.nastastopp.geo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import se.eldebosh.nastastopp.core.parse.TextNorm
import se.eldebosh.nastastopp.route.RouteController

/**
 * Says the street's name each time the vehicle turns into another one.
 * The same street is not said twice in a row, and the area alone never is. Whether it speaks at
 * all is the driver's switch (the panel's speaker button, Settings 137), checked by the controller.
 */
class StreetCaller(street: CurrentStreet, controller: RouteController, scope: CoroutineScope) {
    private var lastSaid: String? = null

    init {
        scope.launch {
            street.state.collect { info ->
                if (info == null) {
                    lastSaid = null // route ended: the first street of the next route is said
                    return@collect
                }
                val name = info.street ?: return@collect
                if (lastSaid?.let { TextNorm.fold(it) == TextNorm.fold(name) } == true) return@collect
                lastSaid = name
                controller.sayStreetChange(name)
            }
        }
    }
}
