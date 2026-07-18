package app.ncinq.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

data class ViewerProfile(
    val id: String,
    val name: String,
    val color: Color,
    val icon: ImageVector,
)

val DefaultViewerProfiles = listOf(
    ViewerProfile("main", "My Profile", Brand, Icons.Rounded.Face),
    ViewerProfile("kids", "Kids", Color(0xFF2D9CDB), Icons.Rounded.ChildCare),
    ViewerProfile("family", "Family", Color(0xFF8E5BD9), Icons.Rounded.FamilyRestroom),
    ViewerProfile("guest", "Guest", Color(0xFF2DAA78), Icons.Rounded.Person),
)

@Composable
fun ProfileSelector(
    lastProfileId: String?,
    onSelect: (ViewerProfile) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(AppBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Who's watching?", color = TextPrimary, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Text(
            "Choose a profile to continue",
            color = TextSecondary,
            fontSize = 17.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 42.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            DefaultViewerProfiles.forEach { profile ->
                ProfileCard(profile, initiallyPreferred = profile.id == lastProfileId) { onSelect(profile) }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: ViewerProfile,
    initiallyPreferred: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(if (focused) 150.dp else 138.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (focused) Color.White else profile.color)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(126.dp).clip(RoundedCornerShape(9.dp)).background(profile.color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(profile.icon, contentDescription = profile.name, tint = Color.White, modifier = Modifier.size(72.dp))
            }
        }
        Text(
            profile.name,
            color = if (focused || initiallyPreferred) TextPrimary else TextSecondary,
            fontSize = 18.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}
