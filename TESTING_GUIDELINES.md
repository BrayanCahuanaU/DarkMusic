# Guía de Pruebas - Correcciones de Audio

## ✅ Cambios Implementados

### 1. **Mostrar información de la canción en el reproductor**
- **Archivo:** `MusicServiceConnection.kt` (línea 148-204)
- **Cambio:** Función `playSong()` ahora:
  - Recibe parámetro `queue: List<Song> = emptyList()`
  - Actualiza `currentQueue` inmediatamente para que `onMediaItemTransition` encuentre la canción
  - Esto asegura que `_currentSong` se actualice correctamente

### 2. **Habilitar navegación siguiente/anterior**
- **Archivos:** 
  - `HomeViewModel.kt` (línea 91-105)
  - `SearchViewModel.kt` (línea 78-98)
  - `LibraryViewModel.kt` (línea 93-112)
- **Cambio:** Todos los ViewModels ahora pasan la lista de canciones como cola a `playSong()`:
  - HomeViewModel → pasa `recentSongs`
  - SearchViewModel → pasa `searchResults`
  - LibraryViewModel → pasa `allSongs`

### 3. **Reproducir contenido descargado sin conexión**
- **Archivos:**
  - `SearchViewModel.kt` (línea 82-86)
  - `HomeViewModel.kt` (línea 95-98)
  - `LibraryViewModel.kt` (línea 97-100)
- **Cambio:** Todos los ViewModels ahora verifican primero si está descargada:
  ```kotlin
  val streamUrl = if (song.isDownloaded && song.localPath != null) {
      song.localPath  // Usar archivo local
  } else {
      repository.getStreamUrl(song.id)  // Obtener URL de stream online
  }
  ```

---

## 🧪 Plan de Pruebas

### Test 1: Información de la Canción
**Objetivo:** Verificar que el reproductor muestra título y artista

**Pasos:**
1. Abre la app
2. Ve a Búsqueda y busca cualquier canción
3. Haz clic en una canción para reproducir
4. **✓ Esperado:** El reproductor muestra título y artista (NO "No se está reproduciendo nada")

**Si falla:**
- Verifica Logcat: `MusicServiceConnection: onMediaItemTransition`
- Asegúrate de que `currentQueue` está siendo actualizado

---

### Test 2: Navegación de Cola
**Objetivo:** Verificar que funcionen los botones siguiente/anterior

**Pasos:**
1. En la pantalla de búsqueda, busca "music hits" (debe haber múltiples resultados)
2. Haz clic en la segunda canción de la lista
3. En el reproductor, pulsa el botón ⏭️ (siguiente)
4. **✓ Esperado:** La canción cambia a la siguiente de la lista de búsqueda
5. Pulsa el botón ⏮️ (anterior)
6. **✓ Esperado:** Vuelve a la canción anterior

**Si falla:**
- Verifica que la lista de búsqueda no esté vacía
- Logcat: `Player: mediaItemCount` debe ser > 1

---

### Test 3: Reproducción Offline
**Objetivo:** Verificar que usa el archivo local si está descargado

**Pasos:**
1. Busca y abre una canción
2. Haz clic en el botón ⬇️ (Download)
3. Espera a que se descargue (estado cambiará a ✓)
4. **Desactiva internet** (apaga WiFi, desactiva datos móviles)
5. En el reproductor, pulsa ⏹️ para pausar
6. Busca la misma canción
7. Pulsa para reproducir
8. **✓ Esperado:** Se reproduce sin conexión (usando archivo local)

**Cómo verificar que usa local:**
- Abre Logcat con filtro: `RetryableHttpDataSource`
- No deberías ver intentos de descarga de streaming

---

### Test 4: Cola desde Inicio
**Objetivo:** Verificar que la cola funciona en la pantalla de inicio

**Pasos:**
1. Ve a "Inicio"
2. Haz clic en cualquier canción de "Canciones Recientes"
3. En el reproductor, pulsa ⏭️
4. **✓ Esperado:** Va a la siguiente canción de "Canciones Recientes"

---

### Test 5: Cola desde Biblioteca
**Objetivo:** Verificar que la cola funciona en la biblioteca

**Pasos:**
1. Ve a "Biblioteca"
2. Haz clic en cualquier canción de "Todas las canciones"
3. En el reproductor, pulsa ⏭️
4. **✓ Esperado:** Va a la siguiente canción de la biblioteca

---

## 📝 Logcat Filters para Debugging

```bash
# Filtro 1: Información de reproducción
adb logcat -v time MusicServiceConnection:V Player:V *:S

# Filtro 2: Descarga y streams
adb logcat -v time RetryableHttpDataSource:V MusicRepository:V *:S

# Filtro 3: Todos los MusicService logs
adb logcat -v time "*MusicService*:V" "*Music*:V" "*Player*:V" *:S
```

---

## 🔍 Puntos Críticos del Código

### Punto 1: Actualización de currentQueue
**Archivo:** `MusicServiceConnection.kt:154`
```kotlin
currentQueue = if (queue.isNotEmpty()) queue else listOf(selectedSong)
```
- **Por qué es importante:** Sin esto, `onMediaItemTransition` no encuentra las canciones

### Punto 2: onMediaItemTransition
**Archivo:** `MusicServiceConnection.kt:74-86`
```kotlin
override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    mediaItem?.let { item ->
        val song = currentQueue.find { it.id == item.mediaId }  // ← Busca en currentQueue
        _currentSong.value = song  // ← Actualiza el State
        ...
    }
}
```

### Punto 3: Verificación de localPath
**Archivo:** `SearchViewModel.kt:82-86`
```kotlin
val streamUrl = if (song.isDownloaded && song.localPath != null) {
    song.localPath  // ← Prioridad 1: archivo local
} else {
    repository.getStreamUrl(song.id)  // ← Fallback: stream online
}
```

---

## 📊 Resumen de Archivos Modificados

| Archivo | Líneas Modificadas | Cambio Principal |
|---------|-------------------|------------------|
| `MusicServiceConnection.kt` | 148-204 | Agregar parámetro `queue` a `playSong()` |
| `SearchViewModel.kt` | 78-98 | Priorizar local; pasar `searchResults` como cola |
| `HomeViewModel.kt` | 91-105 | Pasar `recentSongs` como cola |
| `LibraryViewModel.kt` | 93-112 | Pasar `allSongs` como cola |

---

## ✨ Esperado Después de los Cambios

✅ Reproductor muestra título/artista de la canción actual
✅ Botones ⏭️/⏮️ funcionan para navegar entre canciones
✅ Cola se muestra correctamente y se actualiza
✅ Contenido descargado se reproduce sin conexión
✅ streaming online funciona cuando no está descargado

