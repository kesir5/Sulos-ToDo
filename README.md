# Sülos ToDo – Android To-Do-App (Projekt: Sülos ToDo)

Angelehnt an Microsoft To Do, mit einklappbaren Aufgaben über drei Ebenen
(Aufgabe → Unteraufgabe → Unter-Unteraufgabe), Anhängen, Home-Screen-Widget
und optionaler Google-Drive-Synchronisation.

**Stack:** Kotlin · Jetpack Compose (Material 3) · Room · Glance (Widget) · WorkManager · OkHttp · Google Identity

## Funktionen
- Listen mit Farbthema, Smart-Listen *Mein Tag*, *Wichtig*, *Geplant*
- Aufgabenbaum: Aufgaben mit Unteraufgaben haben ein Plus-/Minus-Icon zum Auf- und Zuklappen; Tipp auf die linke Hälfte einer Aufgabe klappt ebenfalls, Tipp auf die rechte Hälfte öffnet die Details; dazu „Alle auf-/zuklappen“ in der Kopfzeile
- Listengruppen (Ordner) über zwei Ebenen, z. B. Achievements → Sport → Marathon; auf-/zuklappbar, Listen per langem Druck verschieben
- Fünf Designs für die Startseite (Einstellungen → Design der Startseite): Schattierte Bänder · Kacheln und Karten · Farbakzent · Begrüßungskopf · Dashboard
- Sechs Designs für die Aufgabenübersicht, umschaltbar unter Einstellungen → Design:
  A Klassisch · B Baum kompakt · C Gruppenkarten · D Outliner · E Ebenen-Streifen · F Fortschrittsring
- Drei Designs für die Aufgabendetails (Einstellungen → Design der Aufgabendetails): 1 Hero-Header · 2 Kachel-Dashboard · 3 Dokument
- Import & Export (Einstellungen → Import & Export oder Listenmenü):
  Microsoft To Do direkt per Konto, Todoist-CSV, iCalendar (.ics/VTODO), Google Tasks (Takeout), Sülos-ToDo-Backup (ZIP inkl. Anhänge)
- Langer Druck auf eine Aufgabe → Unteraufgabe, Verschieben, Löschen
- Detailansicht: Unteraufgaben, Mein Tag, Fälligkeit, Erinnerung (Datum + Uhrzeit), Bild-/Datei-Anhänge, Notiz
- Abhaken einer Aufgabe hakt alle Unteraufgaben mit ab
- Widget: zeigt offene Aufgaben einer Liste (eingerückt), Tippen auf den Listennamen wechselt die Liste, Kreis hakt ab, „+“ öffnet die Eingabe
- Lokale Speicherung (Room-DB, Anhänge im App-Speicher); Drive-Sync in den versteckten App-Ordner, Abgleich pro Eintrag nach „neueste Änderung gewinnt“

Die Tiefe ist über `MAX_DEPTH` in `data/Entities.kt` einstellbar (2 = drei Ebenen).

## Build über GitHub
1. Ordner als neues Repository pushen. Der Workflow `.github/workflows/build.yml` baut bei jedem Push.
2. Die APKs findest du im Actions-Lauf unter **Artifacts → SuelosToDo-apk**.

### Fester Signaturschlüssel (für Drive nötig)
Google erkennt die App am SHA-1 des Signaturschlüssels. Ohne festen Schlüssel ändert sich der bei jedem CI-Build.

```bash
keytool -genkeypair -v -keystore release.jks -alias suelostodo -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks > release.jks.b64      # macOS: base64 -i release.jks
```
GitHub → Settings → Secrets and variables → Actions:
| Secret | Wert |
|---|---|
| `KEYSTORE_BASE64` | Inhalt von `release.jks.b64` |
| `KEYSTORE_PASSWORD` | Keystore-Passwort |
| `KEY_ALIAS` | `suelostodo` |
| `KEY_PASSWORD` | Schlüssel-Passwort |

Der Workflow gibt den SHA-1 im Schritt „Print signing SHA-1“ aus.

### Google Cloud einrichten (einmalig)
1. https://console.cloud.google.com → neues Projekt.
2. **APIs & Dienste → Bibliothek → Google Drive API** aktivieren.
3. **OAuth-Zustimmungsbildschirm**: Typ „Extern“, App-Name eintragen, dich selbst als Testnutzer hinzufügen. Scope: `.../auth/drive.appdata`.
4. **Anmeldedaten → OAuth-Client-ID → Android**: Paketname `com.sueleyman.suelostodo`, SHA-1 aus dem Workflow.
5. Kein Client-Secret oder JSON nötig – die App nutzt den Android-Client automatisch.

In der App: Einstellungen → „Mit Google Drive verbinden“.

### Microsoft To Do einrichten (einmalig, für Import/Export)
1. https://portal.azure.com → **Microsoft Entra ID → App-Registrierungen → Neue Registrierung**.
   Name „Sülos ToDo“, Kontotyp **„Konten in einem beliebigen Organisationsverzeichnis und persönliche Microsoft-Konten“**.
2. **Authentifizierung → Plattform hinzufügen → Mobile und Desktopanwendungen**, benutzerdefinierte Umleitungs-URI: `suelostodo://auth`.
3. **API-Berechtigungen → Microsoft Graph → Delegiert:** `Tasks.ReadWrite`, `User.Read`, `offline_access`.
4. Die **Anwendungs-ID (Client-ID)** als GitHub-Secret `MS_CLIENT_ID` hinterlegen – oder direkt in der App unter Import & Export eintragen.

Bei Firmenkonten kann eine Admin-Zustimmung nötig sein; persönliche Konten (outlook.com/hotmail) funktionieren direkt.

Abbildung der Ebenen: Aufgabe → To-Do-Aufgabe, Unteraufgabe → Schritt, Unter-Unteraufgabe → Schritt mit „↳“.
Beim Import werden Schritte zu Unteraufgaben; erneuter Import aktualisiert statt zu verdoppeln. Anhänge > 3 MB werden nicht zu Microsoft hochgeladen.

### Formate im Überblick
| Format | Import | Export | Hinweis |
|---|---|---|---|
| Microsoft To Do | ✔ | ✔ | direkt per Graph-API |
| Todoist CSV | ✔ | ✔ | eine Liste pro Datei, INDENT 1–3, nur offene Aufgaben |
| iCalendar VTODO | ✔ | ✔ | Ebenen über RELATED-TO, Erinnerung als VALARM |
| Google Tasks | ✔ | – | Tasks.json oder Takeout-ZIP |
| Sülos-ToDo-Backup | ✔ | ✔ | vollständig inkl. Anhänge, Zusammenführung nach neuester Änderung |
