package com.example.darkmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.darkmusic.core.designsystem.DarkMusicTheme
import com.example.darkmusic.ui.MainScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Punto de entrada único de la actividad.
 * @AndroidEntryPoint permite que Hilt inyecte dependencias en esta Activity.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Habilita el diseño de borde a borde (transparencia en barra de estado y navegación)
        enableEdgeToEdge()
        
        setContent {
            // Aplicamos nuestro tema personalizado basado en Apple Music
            DarkMusicTheme {
                // Cargamos el contenedor principal que tiene la navegación
                MainScreen()
            }
        }
    }
}
