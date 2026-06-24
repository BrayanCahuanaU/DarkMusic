# Settings Wiring Plan

## Changes needed to make all settings functional

---

### 1. `core/designsystem/Color.kt`
Add light mode colors and accent color constants after `SystemBlue`.

```diff
+ val CanvasWhite = Color(0xFFFFFFFF)
+ val Surface1Light = Color(0xFFF2F2F7)
+ val Surface2Light = Color(0xFFE5E5EA)
+ val Surface3Light = Color(0xFFD1D1D6)
+ val LabelPrimaryLight = Color(0xFF000000)
+ val LabelSecondaryLight = Color(0x993C3C43)
+ val LabelTertiaryLight = Color(0x4D3C3C43)
+ val AccentBlue = Color(0xFF007AFF)
+ val AccentGreen = Color(0xFF34C759)
+ val AccentPurple = Color(0xFFAF52DE)
+ val AccentOrange = Color(0xFFFF9500)
```

---

### 2. `core/designsystem/Theme.kt`
- Replace hardcoded `DarkColorScheme` with functions that accept `accentColor` and `isDark`
- Add `themeMode` and `accentColor` parameters to `DarkMusicTheme()`
- Use `isSystemInDarkTheme()` for SYSTEM mode
- Generate light/dark scheme based on mode + accent
- Adjust status bar icons for light mode

New imports:
```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.lightColorScheme
```

---

### 3. `MainActivity.kt`
- Inject `SettingsManager` via `@Inject lateinit var settingsManager: SettingsManager`
- In `setContent`, observe SharedPreferences changes using `DisposableEffect`
- Track `themeMode` and `accentColor` as `mutableStateOf`
- Pass to `DarkMusicTheme(themeMode = themeMode, accentColor = accentColor)`

New imports:
```kotlin
import android.content.SharedPreferences
import androidx.compose.runtime.*
```

---

### 4. `di/PlaybackModule.kt`
- Inject `SettingsManager into providePlayer()`
- After building ExoPlayer, call `player.setSkipSilenceEnabled(settingsManager.getSkipSilence())`
- Add `@OptIn(UnstableApi::class)` (already present)

---

### 5. `playback/manager/MusicServiceConnection.kt`
- Inject `SettingsManager` into constructor
- In `checkAndTriggerRecommendations()`, add early return:
  ```kotlin
  if (!settingsManager.getAutoPlayRelated()) return
  ```

---

### 6. `ui/settings/SettingsViewModel.kt`
- Inject `MusicRepository` into constructor
- Replace `clearSearchHistory()` body:
  ```kotlin
  viewModelScope.launch { repository.clearSearchHistory() }
  ```
- Add `clearAppCache()` method:
  ```kotlin
  fun clearAppCache() {
      viewModelScope.launch {
          withContext(Dispatchers.IO) {
              Coil.imageLoader(context).diskCache?.clear()
              Coil.imageLoader(context).memoryCache?.clear()
              context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
          }
          state = state.copy(cacheSize = getCacheSize())
      }
  }
  ```
- Add `import coil.Coil` and `import coil.ImageLoader`

---

### 7. `ui/settings/SettingsScreen.kt`
- Replace `AppleMusicRed` → `MaterialTheme.colorScheme.primary` in:
  - `SettingsCategory` (title color)
  - `SettingsClickItem` (title color)
  - `RadioButtonDefaults.colors(selectedColor = ...)` in all dialogs
  - `TextButton` text color in dialogs
  - `SwitchDefaults.colors(checkedTrackColor = ...)`
  - Check icon tint in AccentDialog
- Add "Clear cache" clickable item after cache display
- Wire "Código abierto" onClick:
  ```kotlin
  onClick = {
      val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = Uri.fromParts("package", context.packageName, null)
      }
      context.startActivity(intent)
  }
  ```

---

### 8. `ui/MainScreen.kt`
In the `NavigationBarItemDefaults.colors` block:
- Replace `selectedIconColor = AppleMusicRed` → `selectedIconColor = MaterialTheme.colorScheme.primary`
- Replace `selectedTextColor = AppleMusicRed` → `selectedTextColor = MaterialTheme.colorScheme.primary`
