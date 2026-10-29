package app.xailla.vpn
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

object Cfg {
    class P(val name: String, val host: String, val port: Int, val out: JSONObject)

    fun b64(s: String): String =
        String(Base64.decode(s.trim().replace('-', '+').replace('_', '/'), Base64.DEFAULT))

    fun parse(link: String): P {
        val l = link.trim()
        return when {
            l.startsWith("vmess://") -> vmess(l)
            l.startsWith("vless://") || l.startsWith("trojan://") -> uri(l)
            l.startsWith("ss://") -> ss(l)
            else -> throw IllegalArgumentException("Format tidak didukung")
        }
    }

    private fun stream(net: String, sec: String, sni: String?, host: String?, path: String?,
                       fp: String?, pbk: String?, sid: String?, alpn: String?, svc: String?): JSONObject {
        val s = JSONObject().put("network", net).put("security", sec)
        when (net) {
            "ws" -> s.put("wsSettings", JSONObject().put("path", path ?: "/")
                .put("headers", JSONObject().put("Host", host ?: sni ?: "")))
            "grpc" -> s.put("grpcSettings", JSONObject().put("serviceName", svc ?: path ?: ""))
            "httpupgrade" -> s.put("httpupgradeSettings", JSONObject().put("path", path ?: "/").put("host", host ?: ""))
            "xhttp" -> s.put("xhttpSettings", JSONObject().put("path", path ?: "/").put("host", host ?: ""))
        }
        if (sec == "tls") {
            val t = JSONObject().put("serverName", sni ?: host ?: "").put("fingerprint", fp ?: "chrome")
            if (!alpn.isNullOrEmpty()) t.put("alpn", JSONArray(alpn.split(",")))
            s.put("tlsSettings", t)
        } else if (sec == "reality") {
            s.put("realitySettings", JSONObject().put("serverName", sni ?: "").put("fingerprint", fp ?: "chrome")
                .put("publicKey", pbk ?: "").put("shortId", sid ?: "").put("spiderX", ""))
        }
        return s
    }

    private fun uri(l: String): P {
        val u = Uri.parse(l)
        val proto = u.scheme!!; val host = u.host!!; val port = u.port
        fun q(k: String) = u.getQueryParameter(k)
        val st = stream(q("type") ?: "tcp", q("security") ?: "none", q("sni"), q("host"), q("path"),
            q("fp"), q("pbk"), q("sid"), q("alpn"), q("serviceName"))
        val user = u.userInfo ?: throw IllegalArgumentException("User/ID kosong")
        val o = JSONObject().put("protocol", proto).put("streamSettings", st)
        if (proto == "vless") {
            val usr = JSONObject().put("id", user).put("encryption", q("encryption") ?: "none")
            q("flow")?.let { usr.put("flow", it) }
            o.put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject()
                .put("address", host).put("port", port).put("users", JSONArray().put(usr)))))
        } else {
            o.put("settings", JSONObject().put("servers", JSONArray().put(JSONObject()
                .put("address", host).put("port", port).put("password", user))))
        }
        return P(u.fragment ?: host, host, port, o)
    }

    private fun vmess(l: String): P {
        val j = JSONObject(b64(l.removePrefix("vmess://")))
        val host = j.getString("add"); val port = j.get("port").toString().toInt()
        val net = j.optString("net", "tcp").ifEmpty { "tcp" }
        val sec = if (j.optString("tls") == "tls") "tls" else "none"
        val st = stream(net, sec, j.optString("sni").ifEmpty { null }, j.optString("host").ifEmpty { null },
            j.optString("path").ifEmpty { null }, j.optString("fp").ifEmpty { null }, null, null,
            j.optString("alpn").ifEmpty { null }, j.optString("path").ifEmpty { null })
        val usr = JSONObject().put("id", j.getString("id")).put("alterId", j.optString("aid", "0").toIntOrNull() ?: 0)
            .put("security", j.optString("scy", "auto").ifEmpty { "auto" })
        val o = JSONObject().put("protocol", "vmess").put("streamSettings", st)
            .put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject()
                .put("address", host).put("port", port).put("users", JSONArray().put(usr)))))
        return P(j.optString("ps", host), host, port, o)
    }

    private fun ss(l: String): P {
        val name = Uri.decode(l.substringAfter('#', ""))
        var body = l.removePrefix("ss://").substringBefore('#').substringBefore('?')
        if (!body.contains('@')) body = b64(body)
        val ui = body.substringBeforeLast('@'); val hp = body.substringAfterLast('@')
        val cred = if (ui.contains(':')) ui else b64(ui)
        val host = hp.substringBeforeLast(':'); val port = hp.substringAfterLast(':').toInt()
        val o = JSONObject().put("protocol", "shadowsocks").put("settings", JSONObject().put("servers",
            JSONArray().put(JSONObject().put("address", host).put("port", port)
                .put("method", cred.substringBefore(':')).put("password", cred.substringAfter(':')))))
        return P(name.ifEmpty { host }, host, port, o)
    }

    fun xray(p: P, dns: String): String = JSONObject()
        .put("log", JSONObject().put("loglevel", "warning"))
        .put("dns", JSONObject().put("servers", JSONArray(listOf(dns, "8.8.8.8"))))
        .put("inbounds", JSONArray().put(JSONObject().put("tag", "in").put("listen", "127.0.0.1")
            .put("port", 10808).put("protocol", "socks")
            .put("settings", JSONObject().put("udp", true).put("auth", "noauth"))))
        .put("outbounds", JSONArray().put(p.out.put("tag", "proxy"))
            .put(JSONObject().put("protocol", "freedom").put("tag", "direct")))
        .toString()
}
