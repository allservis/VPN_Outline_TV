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

@Composable
fun PairByQrScreen(onKeyReady: (String) -> Unit) {
    val ctx = LocalContext.current
    var token by remember { mutableStateOf(genToken()) }
    var url by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var server: TvLocalServer? by remember { mutableStateOf(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(token) {
        val ip = ctx.getLocalIp()
        if (ip == null) { error = "Не удалось получить IP адрес"; return@LaunchedEffect }
        val port = 45789
        url = "http://$ip:$port/session/$token"
        server?.stop()
        server = TvLocalServer(port, token) { key ->
            // получили ключ -> закрываем сервер и отдаём наверх
            server?.stop()
            onKeyReady(key)
        }.also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        qr = makeQr(url!!).toImageBitmap()
    }

    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Сканируйте QR с телефона (ту же Wi-Fi сеть)", fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        qr?.let { Image(it, contentDescription = null, modifier = Modifier.size(260.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(url ?: "", fontSize = 14.sp)
        error?.let { Text(it, color = Color.Red) }
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            // Сгенерировать новый токен/перезапустить сессию
            token = genToken()
        }) { Text("Сгенерировать новый QR") }
    }
}