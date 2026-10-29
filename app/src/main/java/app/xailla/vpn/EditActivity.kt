package app.xailla.vpn
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class EditActivity : AppCompatActivity() {
    private val inp = LinkedHashMap<String, EditText>()
    private var spNet: Spinner? = null
    private var spSec: Spinner? = null
    private lateinit var st: Store
    private lateinit var list: MutableList<Srv>
    private var idx = -1
    private var f: F? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        st = Store(this); list = st.load()
        idx = intent.getIntExtra("i", -1)
        if (idx !in list.indices) { finish(); return }
        val srv = list[idx]
        f = Fields.read(srv.link)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        val root = ScrollView(this).apply { setBackgroundColor(0xFF0F1115.toInt()); addView(box) }
        setContentView(root)

        box.addView(TextView(this).apply {
            text = "Edit server"; textSize = 22f; setTextColor(0xFFFFFFFF.toInt()); setPadding(0, 0, 0, pad)
        })

        val cur = f
        if (cur == null) {
            field(box, "Nama", "remarks", srv.name)
            field(box, "Link", "raw", srv.link, multi = true)
        } else {
            field(box, "Nama (remarks)", "remarks", cur.remarks)
            field(box, "Alamat (address)", "addr", cur.addr)
            field(box, "Port", "port", cur.port, number = true)
            field(box, "ID / Password", "id", cur.id)
            spNet = spinner(box, "Jaringan (network)", listOf("tcp", "ws", "grpc", "httpupgrade"), cur.net)
            spSec = spinner(box, "Keamanan (security)", listOf("none", "tls", "reality"), cur.sec)
            field(box, "Host", "host", cur.host)
            field(box, "Path / serviceName", "path", cur.path)
            field(box, "SNI", "sni", cur.sni)
            field(box, "Fingerprint", "fp", cur.fp)
            field(box, "Public key (reality)", "pbk", cur.pbk)
            field(box, "Short ID (reality)", "sid", cur.sid)
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, pad, 0, 0) }
        fun btn(t: String, click: () -> Unit) = Button(this).apply {
            text = t; textSize = 13f; minWidth = 0; setOnClickListener { click() }
        }.also { row.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
        btn("Batal") { finish() }
        btn("Salin URL") {
            val l = list[idx].link
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("url", l))
            toast("URL disalin")
        }
        btn("Simpan") { save() }
        box.addView(row)
    }

    private fun get(k: String) = inp[k]!!.text.toString().trim()

    private fun save() {
        try {
            val name: String
            val link: String
            val cur = f
            if (cur == null) {
                name = get("remarks"); link = get("raw")
            } else {
                cur.remarks = get("remarks"); cur.addr = get("addr"); cur.port = get("port")
                cur.id = get("id"); cur.host = get("host"); cur.path = get("path")
                cur.sni = get("sni"); cur.fp = get("fp"); cur.pbk = get("pbk"); cur.sid = get("sid")
                cur.net = spNet!!.selectedItem.toString(); cur.sec = spSec!!.selectedItem.toString()
                link = Fields.write(cur); name = cur.remarks.ifEmpty { cur.addr }
            }
            Cfg.parse(link)
            list[idx] = Srv(name.ifEmpty { "server" }, link)
            st.save(list)
            toast("Tersimpan")
            finish()
        } catch (_: Exception) {
            toast("Data tidak valid")
        }
    }

    private fun field(box: LinearLayout, label: String, key: String, value: String,
                      multi: Boolean = false, number: Boolean = false) {
        box.addView(TextView(this).apply { text = label; setTextColor(0xFF8A93A6.toInt()); textSize = 13f })
        val e = EditText(this).apply {
            setText(value); setTextColor(0xFFFFFFFF.toInt())
            inputType = when {
                number -> InputType.TYPE_CLASS_NUMBER
                multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
            if (multi) minLines = 3
        }
        inp[key] = e
        box.addView(e)
    }

    private fun spinner(box: LinearLayout, label: String, items: List<String>, cur: String): Spinner {
        box.addView(TextView(this).apply { text = label; setTextColor(0xFF8A93A6.toInt()); textSize = 13f })
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        sp.setSelection(items.indexOf(cur).coerceAtLeast(0))
        box.addView(sp)
        return sp
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
