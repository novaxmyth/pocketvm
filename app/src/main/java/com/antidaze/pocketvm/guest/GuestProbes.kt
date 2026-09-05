package com.antidaze.pocketvm.guest

import java.net.InetSocketAddress
import java.net.Socket

/** Tiny health probes for the main screen's status indicators. */
object GuestProbes {

    /**
     * True when the Android guest's adbd answers (kernel + init are up).
     * slirp always accepts the host-side connect, so we must see the ADB
     * CNXN greeting bytes, not just a successful connect.
     */
    fun adbAlive(host: String = "127.0.0.1", port: Int = 5556): Boolean = try {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 1200)
        s.soTimeout = 1500
        val head = ByteArray(4)
        val inp = s.getInputStream()
        var n = 0
        while (n < 4) {
            val r = inp.read()
            if (r < 0) break
            head[n] = r.toByte()
            n++
        }
        try { s.close() } catch (e: Exception) { }
        n == 4 && String(head, 0, 4) == "CNXN"
    } catch (e: Exception) {
        false
    }
}
