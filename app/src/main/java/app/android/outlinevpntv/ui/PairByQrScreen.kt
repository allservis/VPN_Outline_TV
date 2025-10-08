package app.android.outlinevpntv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.android.outlinevpntv.data.remote.TvLocalServer
import app.android.outlinevpntv.data.remote.genToken
import app.android.outlinevpntv.data.remote.getLocalIp
import app.android.outlinevpntv.data.remote.makeQr
import app.android.outlinevpntv.data.remote.toImageBitmap
import fi.iki.elonen.NanoHTTPD
import java.net.BindException

@Composable
fun PairByQrScreen(
    onKeyReady: (String) -> Unit
) {
    val ctx = LocalContext.current

    var token by remember { mutableStateOf(genToken()) }
    var url by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var server by remember { mutableStateOf<TvLocalServer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                server?.stop()
            } catch (_: Throwable) { }
            server = null
        }
    }

    LaunchedEffect(token) {
        error = null
        url = null
        qr = null

        val ip = ctx.getLocalIp()
        if (ip == null) {
            error = "Не удалось получить IP адрес устройства"
            return@LaunchedEffect
        }

        try {
            server?.stop()
        } catch (_: Throwable) { }
        server = null

        val srv = TvLocalServer(
            port = 0,
            token = token
        ) { key ->
            try {
                server?.stop()
            } catch (_: Throwable) { }
            server = null
            onKeyReady(key)
        }

        try {
            srv.start(NanoHTTPD.SOCKET_READ_TIMEOUT, /*daemon=*/false)

            val actualPort = srv.getListeningPort()
            if (actualPort <= 0) {
                srv.stop()
                error = "Не удалось занять порт"
                return@LaunchedEffect
            }

            server = srv

            val link = "http://$ip:$actualPort/session/$token"
            url = link
            qr = makeQr(link).toImageBitmap()
        } catch (e: BindException) {
            try {
                srv.stop()
            } catch (_: Throwable) { }
            server = null
            error = "Порт занят, пробуем ещё раз…"
            token = genToken()
        } catch (t: Throwable) {
            try {
                srv.stop()
            } catch (_: Throwable) { }
            server = null
            error = "Ошибка запуска локального сервера: ${t.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Сканируйте QR с телефона (должно быть в той же Wi-Fi/LAN сети)", fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        qr?.let { Image(it, contentDescription = null, modifier = Modifier.size(260.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(url ?: "", fontSize = 14.sp)
        error?.let { Text(it, color = Color.Red) }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { token = genToken() }) {
            Text("Сгенерировать новый QR")
        }
    }
}