package se.eldebosh.nastastopp.core.nav

import java.net.URI

/**
 * One HTTP request: a POST of [body] when there is one, else a GET of [url]. [viaServer]: it goes
 * to the company's server with the tablet's pass in its [headers] (the transport then checks the
 * address first, [CompanyServer.mayCarryToken], and follows no redirect). Its text form names only
 * the host: never the path (a point may be in it), a header (a key or the pass) or the body.
 */
class HttpAsk(
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
    val readTimeoutMs: Int,
    val viaServer: Boolean,
) {
    val post: Boolean get() = body != null

    override fun toString() = "HttpAsk(${runCatching { URI(url).host }.getOrNull() ?: "?"})"
}

/**
 * What is asked, as the company's server counts it each day ([serverName]), and who answers it
 * ([source]): a way, or the travel times ([matrix]), which are asked only on the driver's tap
 * (Suggest, 248).
 */
enum class WayService(val source: WaySource, val serverName: String, val matrix: Boolean) {
    GOOGLE_ROUTES(WaySource.GOOGLE, "google_routes", matrix = false),
    GOOGLE_MATRIX(WaySource.GOOGLE, "google_matrix", matrix = true),
    MAPMAP_ROUTE(WaySource.MAPMAP, "mapmap_route", matrix = false),
    MAPMAP_MATRIX(WaySource.MAPMAP, "mapmap_matrix", matrix = true),
}

/** A request for a way or travel times, and the service that answers it (whose parser reads the answer). */
class WayRequest(val ask: HttpAsk, val service: WayService) {
    override fun toString() = "WayRequest($service, $ask)"
}

/** With what the tablet may ask for ways and travel times. Each compares by value; none shows a key or the pass in its text form. */
sealed interface WayAccess {
    /** Nothing: no way is asked. */
    data object None : WayAccess

    /** The driver's own keys, typed on the tablet (208, 307), while it is not connected to the company's server. */
    data class Own(val googleKey: String, val mapmapKey: String?) : WayAccess {
        override fun toString() = "Own(***)"
    }

    /**
     * Through the company's server, which asks with the company's keys ([CompanyServer]): [google]
     * and [mapmap] say which ways the server has keys for. A stop without its point always goes to
     * Google, which finds it by its address.
     */
    data class Company(val token: DeviceToken, val google: Boolean, val mapmap: Boolean) : WayAccess {
        override fun toString() = "Company(***, google=$google, mapmap=$mapmap)"
    }
}

/**
 * The request for each ask of the tablet's map, by its access ([WayAccess]), the source the driver
 * chose (304) and the stops: the way ([route]) or the travel times between the stops ([matrix]).
 * mapmap is asked only when chosen, when it can be (every stop has its point; with the driver's own
 * mapmap key, or the server has one); otherwise Google. The bodies are [RoutesApi]'s and
 * [MapmapApi]'s: places only, never a name, a time or a label. The pass goes only in a header, never
 * in an address or a body; the driver's own keys never go to the company's server.
 */
object WayRequests {
    /** The page address the map loads from with the driver's own key, sent with his Google asks for a key restricted to it. */
    const val OWN_PAGE_BASE = "https://nastastopp.app/"

    /** Google or mapmap asked directly. */
    const val OWN_READ_TIMEOUT_MS = 15_000

    /** The company's server: above its own 15 s wait for Google or mapmap, so its answer always comes first. */
    const val SERVER_READ_TIMEOUT_MS = 25_000

    /** Whether mapmap gives this ask's answer (else Google). */
    fun usesMapmap(access: WayAccess, source: WaySource, stops: List<MapWay.Stop>): Boolean =
        source == WaySource.MAPMAP && MapmapApi.canAsk(stops) && when (access) {
            is WayAccess.Own -> MapmapApi.isKey(access.mapmapKey)
            is WayAccess.Company -> access.mapmap
            WayAccess.None -> false
        }

    /** The service that gives the way through [stops]. */
    fun routeService(access: WayAccess, source: WaySource, stops: List<MapWay.Stop>): WayService =
        if (usesMapmap(access, source, stops)) WayService.MAPMAP_ROUTE else WayService.GOOGLE_ROUTES

    /** The service that gives the travel times between [stops]. */
    fun matrixService(access: WayAccess, source: WaySource, stops: List<MapWay.Stop>): WayService =
        if (usesMapmap(access, source, stops)) WayService.MAPMAP_MATRIX else WayService.GOOGLE_MATRIX

    /** The way from the vehicle ([lat], [lng]) through [stops] in turn; null when it cannot be asked. */
    fun route(access: WayAccess, source: WaySource, lat: Double, lng: Double, stops: List<MapWay.Stop>): WayRequest? {
        val service = routeService(access, source, stops)
        val ask = when (access) {
            WayAccess.None -> null
            is WayAccess.Own -> when (service) {
                WayService.MAPMAP_ROUTE -> MapmapApi.routeUrl(lat, lng, stops)?.let { url ->
                    HttpAsk(url, mapmapHeaders(access.mapmapKey!!), null, OWN_READ_TIMEOUT_MS, viaServer = false)
                }
                else -> googleKey(access)?.let { key ->
                    RoutesApi.body(lat, lng, stops)?.let { body ->
                        HttpAsk(RoutesApi.URL, googleHeaders(key, RoutesApi.FIELDS), body, OWN_READ_TIMEOUT_MS, viaServer = false)
                    }
                }
            }
            is WayAccess.Company -> when (service) {
                WayService.MAPMAP_ROUTE -> MapmapApi.serverRouteBody(lat, lng, stops)?.let { body ->
                    HttpAsk(CompanyServer.MAPMAP_ROUTE, CompanyServer.headers(access.token), body, SERVER_READ_TIMEOUT_MS, viaServer = true)
                }
                else -> RoutesApi.body(lat, lng, stops)?.let { body ->
                    HttpAsk(CompanyServer.ROUTES, CompanyServer.headers(access.token, RoutesApi.FIELDS), body, SERVER_READ_TIMEOUT_MS, viaServer = true)
                }
            }
        } ?: return null
        return WayRequest(ask, service)
    }

    /** The travel times from the vehicle and from each stop, to each of [stops]; null when they cannot be asked. */
    fun matrix(access: WayAccess, source: WaySource, lat: Double, lng: Double, stops: List<MapWay.Stop>): WayRequest? {
        val service = matrixService(access, source, stops)
        val ask = when (access) {
            WayAccess.None -> null
            is WayAccess.Own -> when (service) {
                WayService.MAPMAP_MATRIX -> MapmapApi.matrixBody(lat, lng, stops)?.let { body ->
                    HttpAsk(MapmapApi.MATRIX_URL, mapmapHeaders(access.mapmapKey!!), body, OWN_READ_TIMEOUT_MS, viaServer = false)
                }
                else -> googleKey(access)?.let { key ->
                    RoutesApi.matrixBody(lat, lng, stops)?.let { body ->
                        HttpAsk(RoutesApi.MATRIX_URL, googleHeaders(key, RoutesApi.MATRIX_FIELDS), body, OWN_READ_TIMEOUT_MS, viaServer = false)
                    }
                }
            }
            is WayAccess.Company -> when (service) {
                WayService.MAPMAP_MATRIX -> MapmapApi.matrixBody(lat, lng, stops)?.let { body ->
                    HttpAsk(CompanyServer.MAPMAP_MATRIX, CompanyServer.headers(access.token), body, SERVER_READ_TIMEOUT_MS, viaServer = true)
                }
                else -> RoutesApi.matrixBody(lat, lng, stops)?.let { body ->
                    HttpAsk(CompanyServer.MATRIX, CompanyServer.headers(access.token, RoutesApi.MATRIX_FIELDS), body, SERVER_READ_TIMEOUT_MS, viaServer = true)
                }
            }
        } ?: return null
        return WayRequest(ask, service)
    }

    private fun googleKey(access: WayAccess.Own): String? = access.googleKey.takeIf { RoutesApi.isKey(it) }

    private fun googleHeaders(key: String, fields: String) = mapOf(
        "X-Goog-Api-Key" to key,
        "X-Goog-FieldMask" to fields,
        // The same page address the map loads from, for a key restricted to it.
        "Referer" to OWN_PAGE_BASE,
    )

    /** The key goes in a header, never in the address. */
    private fun mapmapHeaders(key: String) = mapOf("Authorization" to "Bearer $key")
}
