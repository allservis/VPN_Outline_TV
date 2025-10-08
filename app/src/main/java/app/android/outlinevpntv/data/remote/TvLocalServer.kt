package app.android.outlinevpntv.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import fi.iki.elonen.NanoHTTPD
import java.util.UUID

class TvLocalServer(
    private val port: Int = 45789,
    private val token: String,
    private val onKeyReceived: (String) -> Unit,
) : NanoHTTPD(port) {

    @Volatile private var used = false

    override fun serve(session: IHTTPSession): Response {
        // Простая маршрутизация
        val uri = session.uri // e.g. /session/<token>  or  /api/session/<token>/submit
        return when {
            session.method == Method.GET && uri == "/session/$token" && !used -> {
                // HTML форма для вставки ключа (отдаём прямо с ТВ)
                newFixedLengthResponse(Response.Status.OK, "text/html", """
                  <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
                  <h3>Вставьте ключ</h3>
                  <input id="key" style="width:100%;padding:8px" placeholder="Ключ">
                  <button onclick="send()">Отправить</button>
                  <p id="msg"></p>
                  <script>
                  async function send(){
                    const key = document.getElementById('key').value.trim();
                    if(!key){ alert('Введите ключ'); return; }
                    const r = await fetch('/api/session/$token/submit',{method:'POST',headers:{'Content-Type':'application/json'}, body: JSON.stringify({key})});
                    const j = await r.json();
                    document.getElementById('msg').innerText = j.ok ? 'Ключ отправлен. Можно вернуться к ТВ.' : ('Ошибка: '+(j.error||'unknown'));
                  }
                  </script>
                """.trimIndent())
            }

            session.method == Method.POST && uri == "/api/session/$token/submit" && !used -> {
                val body = HashMap<String, String>()
                session.parseBody(body)
                val json = body["postData"] ?: ""
                val key = Regex("\"key\"\\s*:\\s*\"(.*?)\"").find(json)?.groupValues?.get(1) ?: ""
                if (key.isBlank()) {
                    newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"ok":false,"error":"empty_key"}""")
                } else {
                    used = true
                    // передаём наверх и можно выключать сервер (по желанию)
                    onKeyReceived(key)
                    newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true}""")
                }
            }

            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }
}
