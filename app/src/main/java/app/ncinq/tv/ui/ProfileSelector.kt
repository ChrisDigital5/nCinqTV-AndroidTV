package app.ncinq.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalMovies
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as MaterialText
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.ncinq.tv.data.ViewerProfile
import app.ncinq.tv.data.hashProfilePin

private data class AvatarChoice(val id: String, val icon: ImageVector, val color: Color)

private val avatars = listOf(
    AvatarChoice("face", Icons.Rounded.Face, Brand),
    AvatarChoice("happy", Icons.Rounded.SentimentSatisfied, Color(0xFF2D9CDB)),
    AvatarChoice("rocket", Icons.Rounded.RocketLaunch, Color(0xFF8E5BD9)),
    AvatarChoice("pet", Icons.Rounded.Pets, Color(0xFF2DAA78)),
    AvatarChoice("gamer", Icons.Rounded.SportsEsports, Color(0xFFF2994A)),
    AvatarChoice("kids", Icons.Rounded.ChildCare, Color(0xFFE0568A)),
)

fun profileAvatarIcon(avatarId: String): ImageVector =
    avatars.firstOrNull { it.id == avatarId }?.icon ?: Icons.Rounded.Face

private val movieGenres = listOf("Action", "Comedy", "Drama", "Horror", "Sci-Fi", "Animation")
private val showGenres = listOf("Drama", "Comedy", "Reality", "Crime", "Documentary", "Kids")

@Composable
fun ProfileSelector(
    profiles: List<ViewerProfile>,
    lastProfileId: String?,
    onSelect: (ViewerProfile) -> Unit,
    onSave: (ViewerProfile) -> Unit,
    onDelete: (ViewerProfile) -> Unit,
) {
    var managing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ViewerProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var unlocking by remember { mutableStateOf<ViewerProfile?>(null) }

    unlocking?.let { lockedProfile ->
        PinEntry(
            profile = lockedProfile,
            onCancel = { unlocking = null },
            onUnlocked = { unlocking = null; onSelect(lockedProfile) },
        )
        return
    }

    if (editing != null || creating) {
        ProfileEditor(
            profile = editing,
            canDelete = editing != null && profiles.size > 1,
            onCancel = { editing = null; creating = false },
            onSave = { onSave(it); editing = null; creating = false },
            onDelete = { editing?.let(onDelete); editing = null; creating = false },
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(AppBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (managing) "Manage profiles" else "Who's watching?", color = TextPrimary, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Text(
            if (managing) "Select a profile to edit it" else "Choose a profile to continue",
            color = TextSecondary,
            fontSize = 17.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 38.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.Top) {
            profiles.take(5).forEach { profile ->
                ProfileCard(profile, preferred = profile.id == lastProfileId, managing = managing) {
                    if (managing) editing = profile
                    else if (profile.pinHash.isNullOrBlank()) onSelect(profile) else unlocking = profile
                }
            }
            if (profiles.size < 5) AddProfileCard { creating = true }
        }
        Row(modifier = Modifier.padding(top = 42.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusButton(if (managing) "Done" else "Manage profiles", onClick = { managing = !managing })
        }
    }
}

@Composable
private fun ProfileEditor(
    profile: ViewerProfile?,
    canDelete: Boolean,
    onCancel: () -> Unit,
    onSave: (ViewerProfile) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(profile) { mutableStateOf(profile?.name.orEmpty()) }
    var avatar by remember(profile) { mutableStateOf(profile?.avatar ?: "face") }
    var movies by remember(profile) { mutableStateOf(profile?.moviePreferences.orEmpty().toSet()) }
    var shows by remember(profile) { mutableStateOf(profile?.showPreferences.orEmpty().toSet()) }
    var kidsMode by remember(profile) { mutableStateOf(profile?.kidsMode ?: false) }
    var lockEnabled by remember(profile) { mutableStateOf(!profile?.pinHash.isNullOrBlank()) }
    var pin by remember(profile) { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(AppBackground).verticalScroll(rememberScrollState()).padding(horizontal = 100.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(if (profile == null) "Create profile" else "Edit profile", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            label = { MaterialText("Profile name") },
            singleLine = true,
            modifier = Modifier.width(430.dp),
        )
        PreferenceHeading("Choose an icon", Icons.Rounded.Person)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            avatars.forEach { choice ->
                AvatarButton(choice, selected = avatar == choice.id) { avatar = choice.id }
            }
        }
        PreferenceHeading("Movie preferences", Icons.Rounded.LocalMovies)
        ChoiceRow(movieGenres, movies) { movies = toggleChoice(movies, it) }
        PreferenceHeading("TV show preferences", Icons.Rounded.Favorite)
        ChoiceRow(showGenres, shows) { shows = toggleChoice(shows, it) }
        PreferenceHeading("Profile controls", Icons.Rounded.Person)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusButton("Kids profile", onClick = { kidsMode = !kidsMode }, selected = kidsMode)
            FocusButton("PIN lock", onClick = { lockEnabled = !lockEnabled; if (!lockEnabled) pin = "" }, selected = lockEnabled)
        }
        if (lockEnabled) {
            OutlinedTextField(
                value = pin,
                onValueChange = { value -> pin = value.filter(Char::isDigit).take(4) },
                label = { MaterialText(if (profile?.pinHash.isNullOrBlank()) "Set 4-digit PIN" else "New PIN (leave blank to keep current)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.width(430.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            val pinValid = !lockEnabled || pin.length == 4 || !profile?.pinHash.isNullOrBlank()
            FocusButton("Save profile", onClick = {
                if (name.isNotBlank()) onSave(
                    ViewerProfile(
                        id = profile?.id.orEmpty(),
                        name = name.trim(),
                        avatar = avatar,
                        moviePreferences = movies.toList(),
                        showPreferences = shows.toList(),
                        kidsMode = kidsMode,
                        pinHash = when {
                            !lockEnabled -> null
                            pin.length == 4 -> hashProfilePin(pin)
                            else -> profile?.pinHash
                        },
                    )
                )
            }, selected = true, enabled = name.isNotBlank() && pinValid)
            FocusButton("Cancel", onClick = onCancel)
            if (canDelete) FocusButton("Delete profile", onClick = onDelete)
        }
    }
}

@Composable
private fun PinEntry(profile: ViewerProfile, onCancel: () -> Unit, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(AppBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Unlock ${profile.name}", color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(4); error = false },
            label = { MaterialText("4-digit PIN") },
            isError = error,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.width(350.dp).padding(vertical = 24.dp),
        )
        if (error) Text("Incorrect PIN", color = BrandBright, modifier = Modifier.padding(bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusButton("Unlock", onClick = {
                if (profile.pinHash == hashProfilePin(pin)) onUnlocked() else error = true
            }, selected = true, enabled = pin.length == 4)
            FocusButton("Back", onClick = onCancel)
        }
    }
}

@Composable
private fun PreferenceHeading(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = BrandBright, modifier = Modifier.size(24.dp))
        Text(text, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChoiceRow(options: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { FocusButton(it, onClick = { onToggle(it) }, selected = it in selected) }
    }
}

private fun toggleChoice(current: Set<String>, value: String) =
    if (value in current) current - value else current + value

@Composable
private fun ProfileCard(profile: ViewerProfile, preferred: Boolean, managing: Boolean, onClick: () -> Unit) {
    val avatar = avatars.firstOrNull { it.id == profile.avatar } ?: avatars.first()
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AvatarSurface(avatar, focused, onClick)
        Text(
            profile.name,
            color = if (focused || preferred) TextPrimary else TextSecondary,
            fontSize = 17.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(top = 13.dp),
        )
        if (managing) Text("Edit", color = BrandBright, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AddProfileCard(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(138.dp).clip(RoundedCornerShape(12.dp))
                .background(if (focused) Color.White else Panel)
                .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, "Add profile", tint = if (focused) Color.Black else TextSecondary, modifier = Modifier.size(64.dp)) }
        Text("Add profile", color = if (focused) TextPrimary else TextSecondary, fontSize = 17.sp, modifier = Modifier.padding(top = 13.dp))
    }
}

@Composable
private fun AvatarButton(choice: AvatarChoice, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.size(78.dp).clip(RoundedCornerShape(10.dp))
            .background(if (focused || selected) Color.White else Color.Transparent)
            .padding(4.dp).clip(RoundedCornerShape(8.dp)).background(choice.color)
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(choice.icon, choice.id, tint = Color.White, modifier = Modifier.size(43.dp)) }
}

@Composable
private fun AvatarSurface(choice: AvatarChoice, focused: Boolean, onClick: () -> Unit) {
    var hasFocus by remember(focused) { mutableStateOf(focused) }
    Box(
        Modifier.size(if (hasFocus) 148.dp else 138.dp).clip(RoundedCornerShape(12.dp))
            .background(if (hasFocus) Color.White else choice.color).padding(if (hasFocus) 5.dp else 0.dp)
            .clip(RoundedCornerShape(10.dp)).background(choice.color)
            .onFocusChanged { hasFocus = it.isFocused }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(choice.icon, null, tint = Color.White, modifier = Modifier.size(72.dp)) }
}
