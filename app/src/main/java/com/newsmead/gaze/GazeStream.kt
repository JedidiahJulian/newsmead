package com.newsmead.gaze

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Receives gaze samples streamed from the laptop's GazeFollower backend over
 * UDP. Each packet is ASCII "x,y,timestamp" in laptop-screen pixels; we stamp
 * arrival time locally (the laptop clock is meaningless on the phone). Runs on a
 * daemon background thread and delivers each sample via [onSample].
 *
 * Ported verbatim (except package) from gaze-thesis-prototype.
 */
class GazeStream(
    private val port: Int = DEFAULT_PORT,
    private val onSample: (x: Float, y: Float, timestampMs: Long) -> Unit,
) {
    @Volatile private var running = false
    private var socket: DatagramSocket? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            try {
                val sock = DatagramSocket(port)
                socket = sock
                val buffer = ByteArray(256)
                Log.i(TAG, "Listening for gaze on UDP $port")
                while (running) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    sock.receive(packet)
                    val parts = String(packet.data, 0, packet.length).trim().split(",")
                    if (parts.size >= 2) {
                        val x = parts[0].toFloatOrNull()
                        val y = parts[1].toFloatOrNull()
                        if (x != null && y != null) {
                            onSample(x, y, System.currentTimeMillis())
                        }
                    }
                }
            } catch (e: Exception) {
                if (running) Log.e(TAG, "GazeStream error", e)
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        socket?.close()
        socket = null
        thread = null
    }

    companion object {
        const val DEFAULT_PORT = 5005
        private const val TAG = "GazeStream"

        /** Best-effort local IPv4 address (to tell the user what --phone-ip to use). */
        fun localIpv4(): String? =
            try {
                NetworkInterface.getNetworkInterfaces().toList()
                    .flatMap { it.inetAddresses.toList() }
                    .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                    ?.hostAddress
            } catch (e: Exception) {
                null
            }
    }
}
