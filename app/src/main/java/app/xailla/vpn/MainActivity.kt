package app.xailla.vpn
import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var st: Store
    private var list = mutableListOf<Srv>()
    private lateinit var ad: ArrayAdapter<Srv>
    private lateinit var btn: MaterialButton
    private lateinit var tvS: TextView
    private lateinit var tvN: TextView

    private val vpnReq = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) startVpn()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_main)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        st = Store(this); list = st.load()
        btn = findViewById(R.id.btn); tvS = findViewById(R.id.status); tvN = findViewById(R.id.server)
        ad = object : ArrayAdapter<Srv>(this, 0, list) {
            override fun getView(pos: Int, v: View?, p: ViewGroup): View {
                val r = v ?: layoutInflater.inflate(R.layout.item_server, p, false)
                val s = getItem(pos)!!
                r.findViewById<TextView>(R.id.name).text = s.name
                r.findViewById<TextView>(R.id.ping).text =
                    if (s.ping < 0) "" else if (s.ping == 0L) "timeout" else "${s.ping} ms"
                r.findViewById<TextView>(R.id.more).setOnClickListener { showMenu(it, pos) }
                r.setBackgroundResource(if (pos == st.sel) R.drawable.bg_sel else R.drawable.bg_item)
                return r
            }
        }
        val lv = findViewById<ListView>(R.id.list); lv.adapter = ad
        lv.emptyView = findViewById(R.id.empty)
        lv.setOnItemClickListener { _, _, i, _ -> st.sel = i; refresh() }
        btn.setOnClickListener {
            val s = VpnSvc.state.value ?: 0
            if (s == 1 || s == 2) startService(Intent(this, VpnSvc::class.java).setAction(VpnSvc.STOP))
            else if (list.isEmpty()) toast("Tambahkan server dulu")
            else { val i = VpnService.prepare(this); if (i != null) vpnReq.launch(i) else startVpn() }
        }
        findViewById<View>(R.id.paste).setOnClickListener {
            val t = getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            toast(if (addLinks(t) > 0) "Server ditambahkan" else "Link tidak valid di clipboard")
        }
        findViewById<View>(R.id.sub).setOnClickListener {
            val e = EditText(this); e.hint = "https://..."
            AlertDialog.Builder(this).setTitle("URL Langganan").setView(e)
                .setPositiveButton("Ambil") { _, _ -> fetchSub(e.text.toString().trim()) }
                .setNegativeButton("Batal", null).show()
        }
        findViewById<View>(R.id.ping).setOnClickListener { pingAll() }
        VpnSvc.state.observe(this) { refresh() }
        refresh()
    }

    private fun showMenu(anchor: View, i: Int) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, "Edit")
        pm.menu.add(0, 2, 1, "Salin URL")
        pm.menu.add(0, 3, 2, "Hapus")
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> editDialog(i)
                2 -> copyUrl(list[i].link)
                3 -> deleteDialog(i)
            }
            true
        }
        pm.show()
    }

    private fun editDialog(i: Int) {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, 0) }
        val nm = EditText(this).apply { hint = "Nama server"; setText(list[i].name) }
        val lk = EditText(this).apply { hint = "Link (vless/vmess/trojan/ss)"; setText(list[i].link); setSingleLine(false) }
        box.addView(nm); box.addView(lk)
        AlertDialog.Builder(this).setTitle("Edit server").setView(box)
            .setPositiveButton("Simpan") { _, _ ->
                val link = lk.text.toString().trim()
                try {
                    val p = Cfg.parse(link)
                    val name = nm.text.toString().trim().ifEmpty { p.name }
                    list[i] = Srv(name, link)
                    st.save(list); refresh()
                    toast("Server diperbarui")
                } catch (_: Exception) { toast("Link tidak valid") }
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun copyUrl(link: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("url", link))
        toast("URL disalin")
    }

    private fun deleteDialog(i: Int) {
        AlertDialog.Builder(this).setMessage("Hapus \"${list[i].name}\"?")
            .setPositiveButton("Hapus") { _, _ ->
                list.removeAt(i); st.save(list)
                if (st.sel >= list.size) st.sel = 0
                refresh()
            }.setNegativeButton("Batal", null).show()
    }

    private fun startVpn() {
        startForegroundService(Intent(this, VpnSvc::class.java).putExtra("link", list[st.sel.coerceIn(0, list.size - 1)].link))
    }

    private fun refresh() {
        ad.notifyDataSetChanged()
        tvN.text = list.getOrNull(st.sel)?.name ?: "Belum ada server"
        when (VpnSvc.state.value ?: 0) {
            1 -> { tvS.text = "Menyambungkan…"; btn.text = "BATAL" }
            2 -> { tvS.text = "Terhubung"; btn.text = "PUTUSKAN" }
            3 -> { tvS.text = "Gagal: " + VpnSvc.err; btn.text = "COBA LAGI" }
            else -> { tvS.text = "Terputus"; btn.text = "SAMBUNGKAN" }
        }
    }

    private fun addLinks(t: String): Int {
        var n = 0
        t.lines().map { it.trim() }.filter { it.contains("://") }.forEach { l ->
            try {
                val p = Cfg.parse(l)
                if (list.none { it.link == l }) { list.add(Srv(p.name, l)); n++ }
            } catch (_: Exception) {}
        }
        if (n > 0) st.save(list); refresh(); return n
    }

    private fun fetchSub(u: String) {
        if (!u.startsWith("http")) return toast("URL tidak valid")
        Thread {
            try {
                val c = URL(u).openConnection().apply { connectTimeout = 10000; readTimeout = 15000 }
                val t = c.getInputStream().bufferedReader().readText()
                val d = try { Cfg.b64(t) } catch (_: Exception) { t }
                val n = runOnUiThreadResult { addLinks(if (d.contains("://")) d else t) }
                runOnUiThread { toast("$n server ditambahkan") }
            } catch (e: Exception) { runOnUiThread { toast("Gagal: ${e.message}") } }
        }.start()
    }

    private fun runOnUiThreadResult(f: () -> Int): Int {
        var r = 0; val l = java.util.concurrent.CountDownLatch(1)
        runOnUiThread { r = f(); l.countDown() }; l.await(); return r
    }

    private fun pingAll() {
        list.forEach { s ->
            Thread {
                s.ping = try {
                    val p = Cfg.parse(s.link); val t = System.currentTimeMillis()
                    Socket().use { it.connect(InetSocketAddress(p.host, p.port), 3000) }
                    System.currentTimeMillis() - t
                } catch (_: Exception) { 0 }
                runOnUiThread { ad.notifyDataSetChanged() }
            }.start()
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
