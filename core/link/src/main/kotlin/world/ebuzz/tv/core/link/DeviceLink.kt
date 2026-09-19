package world.ebuzz.tv.core.link

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import world.ebuzz.tv.core.data.container
import world.ebuzz.tv.core.playback.HandOff
import world.ebuzz.tv.core.ui.isTv
import world.ebuzz.tv.domain.model.ResumePoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

data class Peer(val id: String, val name: String, val host: String, val port: Int, val kinds: Set<String>, val tv: Boolean)

// Casting goes one way only: from a phone or tablet (either edition) to a TV. A TV never sends and a phone never
// receives; both ends enforce it. Any number of phones and TVs can be on the Wi-Fi: each advertises itself under a
// unique id, and the user always picks the target from a list.
object DeviceLink {
    private const val TYPE = "_ebuzz._tcp."
    val ALL_KINDS = setOf("live", "film", "album")
    // Preferred ports. A restarted app would otherwise come back on a new random port while other devices still hold
    // the old one from the system's mDNS cache ("did not respond"). Two, because both editions can be installed on one
    // device; random only if both are taken. Senders fall back to these when the advertised port is dead.
    private val KNOWN_PORTS = listOf(47811, 47812)
    private const val SEP = "~"                       // service name = "<display name>~<id>"
    private val io = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var app: Application
    private lateinit var selfId: String
    // what this edition deals in: the TV edition is live TV only, so it neither offers, accepts nor lists films and albums
    var kinds: Set<String> = ALL_KINDS
        private set
    private var installed = false
    var isTvDevice = false
        private set
    private var started = 0
    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    private val found = ConcurrentHashMap<String, Peer>()
    val peers: List<Peer> get() = found.values.sortedBy { it.name.lowercase() }
    var onPeersChanged: (() -> Unit)? = null

    private val nsd get() = app.getSystemService(Context.NSD_SERVICE) as NsdManager

    fun install(application: Application, kinds: Set<String> = ALL_KINDS) {
        if (installed) return
        installed = true; app = application; this.kinds = kinds; isTvDevice = app.isTv
        val prefs = app.getSharedPreferences("link", Context.MODE_PRIVATE)
        selfId = prefs.getString("id", null) ?: UUID.randomUUID().toString().take(6).also { prefs.edit().putString("id", it).apply() }
        if (!isTvDevice) HandOff.onSendClick = { activity, payload -> DevicePicker.send(activity, payload) }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) { if (started++ == 0) start() }
            override fun onActivityStopped(a: Activity) { if (--started == 0) stop() }
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityResumed(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
    }

    private fun deviceName(): String =
        (Settings.Global.getString(app.contentResolver, "device_name") ?: Build.MODEL).replace(SEP, " ").take(40)

    // only while the app is on screen: a received "play" opens the player, which Android allows only from the foreground
    private fun start() {
        val socket = (KNOWN_PORTS + 0).firstNotNullOfOrNull { port -> runCatching { ServerSocket(port) }.getOrNull() } ?: return
        server = socket
        io.execute { while (!socket.isClosed) runCatching { socket.accept() }.onSuccess { s -> io.execute { serve(s) } } }
        val info = NsdServiceInfo().apply { serviceName = deviceName() + SEP + selfId; serviceType = TYPE; port = socket.localPort; setAttribute("kinds", kinds.joinToString(",")); setAttribute("tv", if (isTvDevice) "1" else "0") }
        registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) = Unit
            override fun onServiceUnregistered(i: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) = Unit
        }.also { runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, it) } }
        discovery = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(i: NsdServiceInfo) { if (idOf(i.serviceName) != selfId) main.post { resolveQueue.addLast(i); resolveNext() } }
            override fun onServiceLost(i: NsdServiceInfo) { if (found.remove(idOf(i.serviceName)) != null) changed() }
            override fun onDiscoveryStarted(t: String) = Unit
            override fun onDiscoveryStopped(t: String) = Unit
            override fun onStartDiscoveryFailed(t: String, e: Int) = Unit
            override fun onStopDiscoveryFailed(t: String, e: Int) = Unit
        }.also { runCatching { nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, it) } }
    }

    private fun stop() {
        runCatching { server?.close() }; server = null
        registration?.let { runCatching { nsd.unregisterService(it) } }; registration = null
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }; discovery = null
        resolveQueue.clear(); resolving = false
        found.clear(); changed()
    }

    // NsdManager resolves one service at a time; with several devices around the rest must wait their turn
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolving) return
        val next = resolveQueue.removeFirstOrNull() ?: return
        resolving = true
        val done = { main.post { resolving = false; resolveNext() } }
        runCatching {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, e: Int) { done() }
                override fun onServiceResolved(i: NsdServiceInfo) {
                    val host = i.host?.hostAddress
                    if (host != null) { val theirs = i.attributes["kinds"]?.let { String(it).split(",").toSet() } ?: ALL_KINDS      // a copy older than this field handles everything
                        found[idOf(i.serviceName)] = Peer(idOf(i.serviceName), i.serviceName.substringBeforeLast(SEP), host, i.port, theirs, i.attributes["tv"]?.let { String(it) } == "1"); changed() }
                    done()
                }
            })
        }.onFailure { done() }
    }

    private fun idOf(serviceName: String) = serviceName.substringAfterLast(SEP).take(6)
    private fun changed() { main.post { onPeersChanged?.invoke() } }

    // ---- receiving ----

    private fun serve(s: Socket) = runCatching {
        s.use {
            it.soTimeout = 5000
            val msg = JSONObject(BufferedReader(InputStreamReader(it.getInputStream())).readLine() ?: return@use)
            val reply = when (msg.optString("cmd")) {
                // "now" is what is on screen; "resume" is the film this device stopped part-way, so it can be finished elsewhere
                "query" -> if (isTvDevice) JSONObject().put("now", JSONObject.NULL).put("resume", JSONObject.NULL)      // a TV is never a source
                else JSONObject().put("now", HandOff.source?.snapshot()?.takeIf(::handles) ?: JSONObject.NULL)
                    .put("resume", app.container.getResumePoint()?.let { r -> HandOff.film(r.movieId, r.title, r.streamUrl, r.positionMs) }?.takeIf(::handles) ?: JSONObject.NULL)
                "stop" -> { main.post { HandOff.source?.stop() }; JSONObject().put("ok", true) }
                "play" -> JSONObject().put("ok", isTvDevice && play(msg.optJSONObject("item")))                         // only a TV receives
                else -> JSONObject().put("ok", false)
            }
            it.getOutputStream().apply { write((reply.toString() + "\n").toByteArray()); flush() }
        }
    }

    // a peer is just another device on the Wi-Fi, so what it sends goes through the same content policy as the catalogue
    fun handles(item: JSONObject) = item.optString("kind") in kinds

    fun play(item: JSONObject?): Boolean {
        item ?: return false
        if (!handles(item)) return false
        val c = app.container
        if (!c.policy.allows(HandOff.title(item), "", "")) return false
        val names = item.optJSONArray("names")
        if (names != null && (0 until names.length()).any { !c.policy.allows(names.optString(it), "", "") }) return false
        val intent = HandOff.intent(app, item) ?: return false
        if (item.optString("kind") == "film" && item.optLong("pos") > 0)
            c.saveMovieProgress(ResumePoint(item.optInt("id"), item.optString("title"), item.optString("url"), item.optLong("pos")), Long.MAX_VALUE)
        main.post { app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return true
    }

    // ---- sending ----

    fun request(peer: Peer, msg: JSONObject, onReply: (JSONObject?) -> Unit) = io.execute {
        val reply = (listOf(peer.port) + KNOWN_PORTS).distinct().firstNotNullOfOrNull { port ->
            runCatching {
                Socket().use { s ->
                    s.connect(InetSocketAddress(peer.host, port), 1500); s.soTimeout = 4000
                    s.getOutputStream().apply { write((msg.toString() + "\n").toByteArray()); flush() }
                    BufferedReader(InputStreamReader(s.getInputStream())).readLine()?.let(::JSONObject)
                }
            }.getOrNull()
        }
        main.post { onReply(reply) }
    }
}
