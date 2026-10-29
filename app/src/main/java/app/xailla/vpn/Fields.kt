package app.xailla.vpn
import android.net.Uri
import android.util.Base64
import org.json.JSONObject

class F(
    var remarks: String = "", var proto: String = "vless", var addr: String = "",
    var port: String = "443", var id: String = "", var net: String = "tcp",
    var sec: String = "none", var sni: String = "", var path: String = "",
    var host: String = "", var fp: String = "chrome", var pbk: String = "",
    var sid: String = "", var flow: String = ""
)

object Fields {
    fun read(link: String): F? = try {
        val l = link.trim()
        when {
            l.startsWith("vmess://") -> {
                val j = JSONObject(Cfg.b64(l.removePrefix("vmess://")))
                F(remarks = j.optString("ps"), proto = "vmess", addr = j.getString("add"),
                    port = j.get("port").toString(), id = j.getString("id"),
                    net = j.optString("net", "tcp").ifEmpty { "tcp" },
                    sec = if (j.optString("tls") == "tls") "tls" else "none",
                    sni = j.optString("sni"), path = j.optString("path"), host = j.optString("host"),
                    fp = j.optString("fp", "chrome").ifEmpty { "chrome" })
            }
            l.startsWith("vless://") || l.startsWith("trojan://") -> {
                val u = Uri.parse(l)
                fun q(k: String) = u.getQueryParameter(k) ?: ""
                F(remarks = Uri.decode(u.fragment ?: ""), proto = u.scheme!!, addr = u.host!!,
                    port = if (u.port > 0) u.port.toString() else "443", id = u.userInfo ?: "",
                    net = q("type").ifEmpty { "tcp" }, sec = q("security").ifEmpty { "none" },
                    sni = q("sni"), path = q("path"), host = q("host"),
                    fp = q("fp").ifEmpty { "chrome" }, pbk = q("pbk"), sid = q("sid"), flow = q("flow"))
            }
            else -> null
        }
    } catch (_: Exception) { null }

    fun write(f: F): String {
        if (f.proto == "vmess") {
            val j = JSONObject().put("v", "2").put("ps", f.remarks).put("add", f.addr)
                .put("port", f.port.toInt()).put("id", f.id).put("aid", 0).put("scy", "auto")
                .put("net", f.net).put("type", "none").put("host", f.host).put("path", f.path)
                .put("tls", if (f.sec == "tls") "tls" else "").put("sni", f.sni).put("fp", f.fp)
            return "vmess://" + Base64.encodeToString(j.toString().toByteArray(), Base64.NO_WRAP)
        }
        val q = listOf("type" to f.net, "security" to f.sec, "sni" to f.sni, "host" to f.host,
            "path" to f.path, "fp" to f.fp, "pbk" to f.pbk, "sid" to f.sid, "flow" to f.flow)
            .filter { it.second.isNotEmpty() }
            .joinToString("&") { "${it.first}=${Uri.encode(it.second)}" }
        return "${f.proto}://${Uri.encode(f.id)}@${f.addr}:${f.port}?$q#${Uri.encode(f.remarks)}"
    }
}
