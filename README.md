# NotesX – app note Android (Kotlin + Jetpack Compose + Room)

## Compilare l'APK
**A) Senza PC – GitHub Actions (consigliato)**
1. Crea un repo GitHub e carica tutto il contenuto di questa cartella.
2. Scheda *Actions* → "Build APK" → *Run workflow*.
3. A fine build scarica l'artifact `NotesX-apk` (contiene `app-debug.apk`) e installalo.

**B) Android Studio**
1. File → Open → cartella `NotesX` (Android Studio scarica SDK e Gradle).
2. Build → Build APK(s) → `app/build/outputs/apk/debug/app-debug.apk`.

**C) Riga di comando** (JDK 17 + Android SDK 34 + Gradle 8.9): `gradle assembleDebug`

## Struttura
- `MainActivity.kt` – ingresso, tema, edge-to-edge
- `data/` – Room (Note, Folder, Tag, sticker), Settings, Markup (rich text), Media (galleria/URL/ricerca), Export (PDF/PNG/MD/TXT) e Backup
- `ui/` – NotesViewModel (auto-save), Screens (NavigationBar/Rail + lista-dettaglio su schermi grandi), Editor, Stickers, Components
