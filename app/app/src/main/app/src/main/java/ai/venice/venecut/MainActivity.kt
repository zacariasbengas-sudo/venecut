package ai.venice.venecut

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

// ---- TRAVAS: tudo ligado, nada de plano ----
object Travas {
    const val SEM_MARCA_DAGUA = true
    const val TODOS_EFEITOS = true
    const val SEM_LOGIN = true
    const val SEM_ADS = true
    const val CREDITOS_IA = Int.MAX_VALUE
}

data class Clipe(
    val uri: Uri,
    val nome: String,
    val inicioMs: Long = 0L,
    val fimMs: Long = 10_000L,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = Color(0xFF0C0C10), modifier = Modifier.fillMaxSize()) {
                    Editor()
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun Editor() {
    val ctx = LocalContext.current
    var clipes by remember { mutableStateOf(listOf<Clipe>()) }
    var status by remember { mutableStateOf("nenhum clipe importado") }
    var progresso by remember { mutableStateOf(0f) }
    var texto by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            clipes = clipes + Clipe(uri, uri.lastPathSegment ?: "clipe")
            status = "clipe adicionado"
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("VeneCut", fontWeight = FontWeight.Medium, fontSize = 20.sp, color = Color.White)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .background(Color(0xFF12301D), RoundedCornerShape(99.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("gratis - creditos infinitos", color = Color(0xFF4ADE80), fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color.Black, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (clipes.isEmpty()) "importe um video para comecar"
                else "${clipes.size} clipe(s) na timeline",
                color = Color(0xFF6E6E7A), fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            }) { Text("importar video") }

            OutlinedButton(onClick = {
                clipes = emptyList()
                status = "timeline limpa"
            }) { Text("limpar") }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it },
            label = { Text("texto na tela (opcional)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Text("timeline", color = Color(0xFF9A9AA6), fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(clipes) { c ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF15151C), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(c.nome, color = Color.White, fontSize = 12.sp, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "corte: ${c.inicioMs / 1000}s ate ${c.fimMs / 1000}s",
                        color = Color(0xFF9A9AA6), fontSize = 11.sp
                    )
                    Slider(
                        value = c.fimMs.toFloat(),
                        onValueChange = { v ->
                            clipes = clipes.map {
                                if (it.uri == c.uri) it.copy(fimMs = v.toLong()) else it
                            }
                        },
                        valueRange = 1000f..60_000f
                    )
                }
            }
        }

        if (progresso > 0f) {
            LinearProgressIndicator(progress = { progresso }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
        }

        Text(status, color = Color(0xFF9A9AA6), fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = clipes.isNotEmpty(),
            onClick = {
                exportar(ctx, clipes, texto, { p -> progresso = p }, { msg -> status = msg })
            }
        ) { Text("exportar video") }
    }
}

@OptIn(UnstableApi::class)
fun exportar(
    ctx: android.content.Context,
    clipes: List<Clipe>,
    texto: String,
    onProgresso: (Float) -> Unit,
    onStatus: (String) -> Unit,
) {
    val itens = clipes.map { c ->
        val mi = MediaItem.Builder()
            .setUri(c.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(c.inicioMs)
                    .setEndPositionMs(c.fimMs)
                    .build()
            )
            .build()

        val efeitos: List<Effect> = if (texto.isNotBlank()) {
            listOf(OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(android.text.SpannableString(texto)))))
        } else emptyList()

        EditedMediaItem.Builder(mi).setEffects(efeitos).build()
    }

    val saida = File(ctx.getExternalFilesDir(null), "venecut_saida.mp4")
    if (saida.exists()) saida.delete()

    val transformer = Transformer.Builder(ctx)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(c: Composition, r: ExportResult) {
                onProgresso(1f)
                onStatus("salvo em: ${saida.absolutePath}")
            }
            override fun onError(c: Composition, r: ExportResult, e: ExportException) {
                onStatus("erro: ${e.message}")
            }
        })
        .build()

    val holder = ProgressHolder()
    transformer.start(itens, saida.absolutePath)

    val h = android.os.Handler(android.os.Looper.getMainLooper())
    h.post(object : Runnable {
        override fun run() {
            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                onProgresso(holder.progress / 100f)
                h.postDelayed(this, 250)
            }
        }
    })
}
