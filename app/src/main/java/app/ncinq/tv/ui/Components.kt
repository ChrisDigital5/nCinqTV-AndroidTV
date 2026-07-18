package app.ncinq.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ncinq.tv.data.MediaItem
import app.ncinq.tv.data.MediaRow
import coil3.compose.AsyncImage

private val ControlShape = RoundedCornerShape(6.dp)
private val CardShape = RoundedCornerShape(6.dp)

@Composable
fun FocusButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    emphasis: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "buttonScale")
    val background = when {
        !enabled -> Panel.copy(alpha = 0.5f)
        focused || emphasis -> Color.White
        selected -> Brand
        else -> PanelRaised.copy(alpha = 0.96f)
    }
    val contentColor = when {
        !enabled -> TextSecondary.copy(alpha = 0.6f)
        focused || emphasis -> Color.Black
        else -> TextPrimary
    }

    Row(
        modifier = modifier
            .height(44.dp)
            .scale(scale)
            .graphicsLayer { shadowElevation = if (focused) 14.dp.toPx() else 0f }
            .clip(ControlShape)
            .background(background)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                shape = ControlShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    cardWidth: Dp = 172.dp,
    posterHeight: Dp = 258.dp,
    focusedScale: Float = 1.09f,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "posterScale")

    Column(
        modifier = modifier
            .width(cardWidth)
            .scale(scale)
            .graphicsLayer { shadowElevation = if (focused) 18.dp.toPx() else 0f }
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(width = cardWidth, height = posterHeight)
                .clip(CardShape)
                .background(Panel)
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = CardShape,
                ),
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            ProgressRail(progress, Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = item.title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(
                item.year?.toString(),
                item.rating.takeIf { it > 0 }?.let { "%.1f".format(it) },
            ).joinToString("  |  "),
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun LandscapeMediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    onFocus: (MediaItem) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.09f else 1f, label = "landscapeScale")

    Column(
        modifier = modifier
            .width(242.dp)
            .scale(scale)
            .graphicsLayer { shadowElevation = if (focused) 18.dp.toPx() else 0f }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus(item)
            }
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(width = 242.dp, height = 136.dp)
                .clip(CardShape)
                .background(Panel)
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = CardShape,
                ),
        ) {
            AsyncImage(
                model = item.backdropUrl ?: item.posterUrl,
                contentDescription = item.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            ProgressRail(progress, Modifier.align(Alignment.BottomStart))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val metadata = listOfNotNull(item.year?.toString(), item.type.replaceFirstChar { it.uppercase() }).joinToString("  |  ")
        Text(metadata, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun ProgressRail(progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0f) return
    Box(modifier.fillMaxWidth().height(5.dp).background(Color.White.copy(alpha = 0.28f))) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp)
                .background(Success),
        )
    }
}

@Composable
fun MediaShelf(
    row: MediaRow,
    onOpen: (MediaItem) -> Unit,
    onFocus: (MediaItem) -> Unit = {},
    rowFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
) {
    val cardFocusRequesters = remember(row.title, row.items.map { it.id to it.type }) {
        List(row.items.size) { FocusRequester() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text(
            text = row.title,
            color = TextPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = OverscanHorizontal),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(horizontal = OverscanHorizontal, vertical = 10.dp),
        ) {
            items(row.items, key = { "${it.type}:${it.id}" }) { item ->
                val index = row.items.indexOf(item)
                val focusModifier = Modifier
                    .focusRequester(cardFocusRequesters[index])
                    .then(if (index == 0 && rowFocusRequester != null) Modifier.focusRequester(rowFocusRequester) else Modifier)
                    .focusProperties {
                        if (index > 0) left = cardFocusRequesters[index - 1]
                        if (index < cardFocusRequesters.lastIndex) right = cardFocusRequesters[index + 1]
                        upFocusRequester?.let { up = it }
                        downFocusRequester?.let { down = it }
                    }
                LandscapeMediaCard(item = item, modifier = focusModifier, onClick = { onOpen(item) }, onFocus = onFocus)
            }
        }
    }
}

@Composable
fun EmptyMessage(title: String, detail: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = TextSecondary, fontSize = 14.sp)
        }
    }
}
