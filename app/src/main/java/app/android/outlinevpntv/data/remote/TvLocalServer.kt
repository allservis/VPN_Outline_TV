package app.android.outlinevpntv.data.remote

import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.atomic.AtomicBoolean

class TvLocalServer(
    port: Int = 0,
    private val token: String,
    private val onKeyReceived: (String) -> Unit,
) : NanoHTTPD(port) {

    private val used = AtomicBoolean(false)

    override fun start(timeout: Int, daemon: Boolean) {
        used.set(false)
        super.start(timeout, daemon)
    }

    override fun stop() {
        super.stop()
        used.set(false)
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        return when {
            session.method == Method.GET && uri == "/session/$token" && !used.get() -> {
                newFixedLengthResponse(
                    Response.Status.OK,
                    "text/html",
                    """
                    <!doctype html>
                    <meta name="viewport" content="width=device-width,initial-scale=1">
                    <h3>Вставьте ключ</h3>
                    <input id="key" style="width:100%;padding:8px" placeholder="Ключ">
                    <button onclick="send()">Отправить</button>
                    <p id="msg"></p>
                    <script>
                    async function send(){
                      const key = document.getElementById('key').value.trim();
                      if(!key){ alert('Введите ключ'); return; }
                      const r = await fetch('/api/session/$token/submit', {
                        method:'POST',
                        headers:{'Content-Type':'application/json'},
                        body: JSON.stringify({key})
                      });
                      const j = await r.json();
                      document.getElementById('msg').innerText = j.ok ? 
                        'Ключ отправлен. Можно вернуться к ТВ.' : 
                        ('Ошибка: ' + (j.error || 'unknown'));
                    }
                    </script>
                    """.trimIndent()
                )
            }

            session.method == Method.POST && uri == "/api/session/$token/submit" && !used.get() -> {
                val body = HashMap<String, String>()
                return try {
                    session.parseBody(body)
                    val json = body["postData"] ?: ""
                    val key = Regex("\"key\"\\s*:\\s*\"(.*?)\"")
                        .find(json)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.trim()
                        .orEmpty()

                    if (key.isBlank()) {
                        newFixedLengthResponse(
                            Response.Status.BAD_REQUEST,
                            "application/json",
                            """{"ok":false,"error":"empty_key"}"""
                        )
                    } else {
                        used.set(true)
                        onKeyReceived(key)
                        newFixedLengthResponse(
                            Response.Status.OK,
                            "application/json",
                            """{"ok":true}"""
                        )
                    }
                } catch (t: Throwable) {
                    newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR,
                        "application/json",
                        """{"ok":false,"error":"${t.message ?: "parse_error"}"}"""
                    )
                }
            }

            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/plain",
                "Not found"
            )
        }
    }
}

