package app.xailla.vpn
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Srv(val name: String, val link: String, var ping: Long = -1)

class Store(c: Context) {
    private val p = c.getSharedPreferences("s", 0)
    fun load(): MutableList<Srv> {
        val a = JSONArray(p.getString("l", "[]"))
        return MutableList(a.length()) { val o = a.getJSONObject(it); Srv(o.getString("n"), o.getString("k")) }
    }
    fun save(l: List<Srv>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("n", it.name).put("k", it.link)) }
        p.edit().putString("l", a.toString()).apply()
    }
    var sel: Int
        get() = p.getInt("sel", 0)
        set(v) { p.edit().putInt("sel", v).apply() }
    val dns: String get() = p.getString("dns", "1.1.1.1")!!
}
