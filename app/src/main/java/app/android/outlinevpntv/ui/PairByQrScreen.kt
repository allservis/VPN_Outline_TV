package app.android.outlinevpntv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    val isPreview = LocalInspectionMode.current

    var token by remember { mutableStateOf(genToken()) }
    var url by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var server by remember { mutableStateOf<TvLocalServer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { server?.stop() }
            server = null
        }
    }

    LaunchedEffect(token, isPreview) {
        error = null
        url = null
        qr = null

        if (isPreview) {
            val demoIp = "192.168.0.10"
            val demoPort = 12345
            val link = "http://$demoIp:$demoPort/session/$token"
            url = link
            qr = makeQr(link).toImageBitmap()
            return@LaunchedEffect
        }

        val ip = ctx.getLocalIp()
        if (ip == null) {
            error = "Не удалось получить IP адрес устройства"
            return@LaunchedEffect
        }

        runCatching { server?.stop() }
        server = null

        val srv = TvLocalServer(
            port = 0,
            token = token
        ) { key ->
            runCatching { server?.stop() }
            server = null
            onKeyReady(key)
        }

        try {
            srv.start(NanoHTTPD.SOCKET_READ_TIMEOUT, /* daemon = */ false)
            val actualPort = srv.listeningPort
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
            runCatching { srv.stop() }
            server = null
            error = "Порт занят, пробуем ещё раз…"
            token = genToken()
        } catch (t: Throwable) {
            runCatching { srv.stop() }
            server = null
            error = "Ошибка запуска локального сервера: ${t.message}"
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Сканируйте QR с телефона (должно быть в той же Wi-Fi/LAN сети)",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    qr?.let {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            modifier = Modifier.size(260.dp)
                        )
                    } ?: run {
                        Text(
                            text = "Генерация QR…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            url?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { token = genToken() },
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Сгенерировать новый QR",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Preview()
@Composable
private fun PairByQrScreenPreview() {
    PairByQrScreen(onKeyReady = { })
}