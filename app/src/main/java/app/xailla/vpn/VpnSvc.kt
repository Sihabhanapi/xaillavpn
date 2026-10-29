package app.xailla.vpn
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.lifecycle.MutableLiveData
import hev.htproxy.TProxyService
import java.io.File

class VpnSvc : VpnService() {
    companion object {
        val state = MutableLiveData(0) // 0 putus, 1 menyambung, 2 tersambung, 3 gagal
        @Volatile var err = ""
        const val STOP = "stop"
    }
    private var proc: Process? = null
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == STOP) { stopAll(0); stopSelf(); return START_NOT_STICKY }
        val link = i?.getStringExtra("link") ?: return START_NOT_STICKY
        startFg(); state.postValue(1)
        Thread {
            try { start(link) } catch (e: Throwable) {
                err = e.message ?: "Gagal"; stopAll(3); stopSelf()
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun start(link: String) {
        val dns = Store(this).dns
        val p = Cfg.parse(link)
        val cfg = File(filesDir, "config.json"); cfg.writeText(Cfg.xray(p, dns))
        val log = File(filesDir, "xray.log"); log.delete()
        val pb = ProcessBuilder(File(applicationInfo.nativeLibraryDir, "libxray.so").path, "run", "-c", cfg.path)
            .redirectErrorStream(true).redirectOutput(log)
        pb.environment()["XRAY_LOCATION_ASSET"] = filesDir.path
        proc = pb.start()
        Thread.sleep(1200)
        if (proc?.isAlive != true) throw Exception("Xray berhenti: " + log.readText().takeLast(200))
        tun = Builder().setSession("Xailla VPN").setMtu(8500)
            .addAddress("198.18.0.1", 32).addRoute("0.0.0.0", 0).addDnsServer(dns)
            .addDisallowedApplication(packageName).establish() ?: throw Exception("Izin VPN ditolak")
        val y = File(filesDir, "tun.yml")
        y.writeText("tunnel:\n  mtu: 8500\n  ipv4: 198.18.0.1\nsocks5:\n  port: 10808\n  address: 127.0.0.1\n  udp: 'udp'\n")
        TProxyService.TProxyStartService(y.path, tun!!.fd)
        state.postValue(2)
    }

    @Synchronized private fun stopAll(s: Int) {
        try { TProxyService.TProxyStopService() } catch (_: Throwable) {}
        proc?.destroy(); proc = null
        try { tun?.close() } catch (_: Exception) {}; tun = null
        state.postValue(s)
        stopForeground(true)
    }

    override fun onRevoke() { stopAll(0); stopSelf() }
    override fun onDestroy() { stopAll(state.value ?: 0); super.onDestroy() }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel("v", "VPN", NotificationManager.IMPORTANCE_LOW))
        val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "v") else Notification.Builder(this))
            .setContentTitle("Xailla VPN").setContentText("VPN aktif").setSmallIcon(R.drawable.ic_logo).build()
        startForeground(1, n)
    }
}
