package app.wallglass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.wallglass.config.CameraConfig
import app.wallglass.ha.Entity

@Composable
fun CamerasScreen(vm: PanelActions, cameras: List<CameraConfig>, states: Map<String, Entity>, onBack: () -> Unit) {
    var full by remember { mutableStateOf<CameraConfig?>(null) }
    val base = vm.baseUrl ?: return
    Box(Modifier.fillMaxSize()) {
        SubPage("Kamery", onBack, trailing = { Txt("${cameras.size} · dotknij, aby powiększyć", 13, color = Palette.soft) }) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(cameras, key = { it.entity }) { cam ->
                    Box(Modifier.height(132.dp).clip(RoundedCornerShape(16.dp)).tap { full = cam }) {
                        CameraSnapshot(base, cam.entity, vm.http, vm::accessToken, 4_000, Modifier.fillMaxSize())
                        Txt(
                            cam.name ?: states[cam.entity]?.name ?: cam.entity,
                            size = 14, weight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                        )
                    }
                }
            }
        }
        full?.let { cam ->
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                CameraLive(vm, cam.entity, cam.rtsp, Modifier.fillMaxSize())
                Box(
                    Modifier.padding(14.dp).size(46.dp).glass(15.dp, Color(0x73000000)).tap { full = null },
                    contentAlignment = Alignment.Center,
                ) { PanelIcon(PanelIcons.close) }
            }
        }
    }
}
