package com.example.darkmusic.ui.settings

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.darkmusic.core.designsystem.*
import com.example.darkmusic.core.preferences.SettingsManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager = hiltViewModel<SettingsViewModel>().settingsManager
) {
    val context = LocalContext.current
    var downloadPath by remember { mutableStateOf(settingsManager.getDownloadPath()) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Nota: En Android moderno (SAF), obtener la ruta real de un URI de árbol puede ser complejo.
            // Para simplificar esta implementación y que sea funcional con File, usaremos el path del URI si es posible,
            // o guardaremos el URI mismo. Aquí intentaremos extraer una representación legible.
            val path = it.path ?: it.toString()
            settingsManager.setDownloadPath(path)
            downloadPath = path
        }
    }

    val storageStatus = remember {
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            "Acceso concedido"
        } else {
            "Sin acceso"
        }
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
                .padding(16.dp)
        ) {
            item {
                SettingsCategory("Descargas")
                SettingsItem(
                    title = "Ubicación de descarga",
                    subtitle = downloadPath,
                    icon = Icons.Default.Folder,
                    onClick = { folderPickerLauncher.launch(null) }
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                SettingsCategory("Almacenamiento")
                SettingsItem(
                    title = "Estado del almacenamiento",
                    subtitle = storageStatus,
                    icon = Icons.Default.Storage
                )
            }
        }
    }
}

@Composable
fun SettingsCategory(title: String) {
    Text(
        text = title.uppercase(),
        color = AppleMusicRed,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
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
        Column {
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
    }
}
