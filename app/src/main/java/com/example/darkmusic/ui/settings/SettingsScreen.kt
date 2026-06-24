package com.example.darkmusic.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.darkmusic.core.designsystem.*
import com.example.darkmusic.core.preferences.AccentColor
import com.example.darkmusic.core.preferences.DownloadFormat
import com.example.darkmusic.core.preferences.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state = viewModel.state

    var showFormatDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAccentDialog by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.onFolderPicked(it) }
    }

    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) { "1.0" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CanvasBlack)
            )
        },
        containerColor = CanvasBlack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // ── DESCARGA ────────────────────────────────────────────
            item { SettingsCategory("Descargas") }

            item {
                SettingsItem(
                    title = "Ubicación de descarga",
                    subtitle = state.downloadPath,
                    icon = Icons.Default.Folder,
                    onClick = { folderPickerLauncher.launch(null) }
                )
            }
            item {
                SettingsItem(
                    title = "Formato de descarga",
                    subtitle = formatLabel(state.downloadFormat),
                    icon = Icons.Default.AudioFile,
                    onClick = { showFormatDialog = true }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // ── REPRODUCCIÓN ─────────────────────────────────────────
            item { SettingsCategory("Reproducción") }

            item {
                SettingsToggleItem(
                    title = "Saltar silencios",
                    subtitle = "Omitir partes sin audio",
                    icon = Icons.Default.VolumeUp,
                    checked = state.skipSilence,
                    onCheckedChange = { viewModel.onSkipSilenceChanged(it) }
                )
            }
            item {
                SettingsToggleItem(
                    title = "Autoreproducción",
                    subtitle = "Reproducir canciones relacionadas al terminar",
                    icon = Icons.Default.Repeat,
                    checked = state.autoPlayRelated,
                    onCheckedChange = { viewModel.onAutoPlayRelatedChanged(it) }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // ── APARIENCIA ──────────────────────────────────────────
            item { SettingsCategory("Apariencia") }

            item {
                SettingsItem(
                    title = "Tema",
                    subtitle = themeLabel(state.themeMode),
                    icon = Icons.Default.DarkMode,
                    onClick = { showThemeDialog = true }
                )
            }
            item {
                SettingsItem(
                    title = "Color de acento",
                    subtitle = accentLabel(state.accentColor),
                    icon = Icons.Default.Palette,
                    onClick = { showAccentDialog = true }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // ── ALMACENAMIENTO ──────────────────────────────────────
            item { SettingsCategory("Almacenamiento") }

            item {
                SettingsItem(
                    title = "Estado del almacenamiento",
                    subtitle = state.storageStatus,
                    icon = Icons.Default.Storage
                )
            }
            item {
                SettingsItem(
                    title = "Caché",
                    subtitle = state.cacheSize,
                    icon = Icons.Default.CleaningServices
                )
            }
            item {
                SettingsClickItem(
                    title = "Limpiar caché",
                    icon = Icons.Default.Delete,
                    onClick = { viewModel.clearAppCache() }
                )
            }
            item {
                SettingsClickItem(
                    title = "Limpiar historial de búsqueda",
                    icon = Icons.Default.DeleteSweep,
                    onClick = { viewModel.clearSearchHistory() }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // ── ACERCA DE ───────────────────────────────────────────
            item { SettingsCategory("Acerca de") }

            item {
                SettingsItem(
                    title = "Versión",
                    subtitle = appVersion,
                    icon = Icons.Default.Info
                )
            }
            item {
                SettingsClickItem(
                    title = "Código abierto",
                    icon = Icons.Default.Code,
                    onClick = {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // ── Diálogos ───────────────────────────────────────────────────
    if (showFormatDialog) {
        FormatDialog(
            current = state.downloadFormat,
            onSelect = { viewModel.onDownloadFormatChanged(it); showFormatDialog = false },
            onDismiss = { showFormatDialog = false }
        )
    }
    if (showThemeDialog) {
        ThemeDialog(
            current = state.themeMode,
            onSelect = { viewModel.onThemeModeChanged(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false }
        )
    }
    if (showAccentDialog) {
        AccentDialog(
            current = state.accentColor,
            onSelect = { viewModel.onAccentColorChanged(it); showAccentDialog = false },
            onDismiss = { showAccentDialog = false }
        )
    }
}

// ── Componentes de diálogo ─────────────────────────────────────────

@Composable
private fun FormatDialog(
    current: DownloadFormat,
    onSelect: (DownloadFormat) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1Dark,
        title = { Text("Formato de descarga", color = Color.White) },
        text = {
            Column {
                DownloadFormat.entries.forEach { format ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(format) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = format == current,
                            onClick = { onSelect(format) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = LabelSecondaryDark
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = formatLabel(format),
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                    Text("Cerrar", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun ThemeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1Dark,
        title = { Text("Tema", color = Color.White) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == current,
                            onClick = { onSelect(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = LabelSecondaryDark
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = themeLabel(mode),
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                    Text("Cerrar", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun AccentDialog(
    current: AccentColor,
    onSelect: (AccentColor) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1Dark,
        title = { Text("Color de acento", color = Color.White) },
        text = {
            Column {
                AccentColor.entries.forEach { color ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(color) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(accentColorValue(color))
                                .border(
                                    width = if (color == current) 3.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = accentLabel(color),
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        if (color == current) {
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                    Text("Cerrar", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

// ── Componentes de UI ──────────────────────────────────────────────

@Composable
fun SettingsCategory(title: String) {
    Text(
        text = title.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
    )
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LabelSecondaryDark,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = LabelSecondaryDark,
                    fontSize = 14.sp
                )
            }
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = LabelTertiaryDark,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LabelSecondaryDark,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = LabelSecondaryDark,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = LabelSecondaryDark,
                uncheckedTrackColor = Surface2Dark
            )
        )
    }
}

@Composable
fun SettingsClickItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LabelSecondaryDark,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = LabelTertiaryDark,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────────

private fun formatLabel(format: DownloadFormat): String = when (format) {
    DownloadFormat.M4A -> "M4A (AAC) — Buena calidad"
    DownloadFormat.WEBM -> "WEBM (Opus) — Mejor compresión"
    DownloadFormat.MP3 -> "MP3 — Compatibilidad universal"
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.DARK -> "Oscuro"
    ThemeMode.LIGHT -> "Claro"
    ThemeMode.SYSTEM -> "Sistema"
}

private fun accentLabel(color: AccentColor): String = when (color) {
    AccentColor.RED -> "Rojo"
    AccentColor.BLUE -> "Azul"
    AccentColor.GREEN -> "Verde"
    AccentColor.PURPLE -> "Púrpura"
    AccentColor.ORANGE -> "Naranja"
}

private fun accentColorValue(color: AccentColor): Color = when (color) {
    AccentColor.RED -> AppleMusicRed
    AccentColor.BLUE -> Color(0xFF007AFF)
    AccentColor.GREEN -> Color(0xFF34C759)
    AccentColor.PURPLE -> Color(0xFFAF52DE)
    AccentColor.ORANGE -> Color(0xFFFF9500)
}
