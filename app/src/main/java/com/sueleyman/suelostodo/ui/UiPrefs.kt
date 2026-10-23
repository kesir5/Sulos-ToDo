package com.sueleyman.suelostodo.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class TaskStyle(val title: String, val description: String) {
    CLASSIC("A · Klassisch", "Farbiger Hintergrund, jede Ebene als eigene Karte"),
    TREE("B · Baum kompakt", "Zeilen mit Baumlinien, Plus rechts mit Anzahl"),
    GROUPS("C · Gruppenkarten", "Unteraufgaben in der Karte, mit Fortschrittsbalken"),
    OUTLINER("D · Outliner", "Minimal, Führungslinien, ohne Karten"),
    STRIPES("E · Ebenen-Streifen", "Farbstreifen zeigt die Ebene, Plus als Zähler-Chip"),
    RING("F · Fortschrittsring", "Plus im Ring, der den Fortschritt zeigt"),
}

enum class DetailStyle(val title: String, val description: String) {
    HERO("1 · Hero-Header", "Farbiger Kopf mit Status-Chips, Inhalte als Karten"),
    TILES("2 · Kachel-Dashboard", "Eigenschaften als Kacheln, Fortschrittsring"),
    DOCUMENT("3 · Dokument", "Eigenschaften-Tabelle, Notiz als Fließtext"),
}

enum class HomeStyle(val title: String, val description: String) {
    BANDS("1 · Schattierte Bänder", "Graues Band je Gruppe, Untergruppen heller"),
    TILES("2 · Kacheln und Karten", "Smart-Listen als Kacheln, Gruppen als Karten"),
    ACCENT("3 · Farbakzent", "Gruppenkopf in Gruppenfarbe mit Akzentbalken"),
    GREETING("4 · Begrüßungskopf", "Kopf mit Begrüßung, Smart-Listen als Chips"),
    DASHBOARD("5 · Dashboard", "Mein-Tag-Fortschritt, Gruppen mit Fortschrittsbalken"),
}

object UiPrefs {
    private val _home = MutableStateFlow(HomeStyle.BANDS)
    val homeStyle: StateFlow<HomeStyle> = _home
    private val _name = MutableStateFlow("")
    val userName: StateFlow<String> = _name

    fun setHomeStyle(c: Context, s: HomeStyle) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putString("home", s.name).apply()
        _home.value = s
    }

    fun setUserName(c: Context, n: String) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putString("name", n).apply()
        _name.value = n
    }

    private val _detail = MutableStateFlow(DetailStyle.HERO)
    val detailStyle: StateFlow<DetailStyle> = _detail

    fun setDetailStyle(c: Context, s: DetailStyle) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putString("detail", s.name).apply()
        _detail.value = s
    }

    private val _style = MutableStateFlow(TaskStyle.CLASSIC)
    val style: StateFlow<TaskStyle> = _style

    fun init(c: Context) {
        val name = c.getSharedPreferences("ui", Context.MODE_PRIVATE).getString("style", null)
        _style.value = TaskStyle.entries.firstOrNull { it.name == name } ?: TaskStyle.CLASSIC
        val d = c.getSharedPreferences("ui", Context.MODE_PRIVATE).getString("detail", null)
        _detail.value = DetailStyle.entries.firstOrNull { it.name == d } ?: DetailStyle.HERO
        val h = c.getSharedPreferences("ui", Context.MODE_PRIVATE).getString("home", null)
        _home.value = HomeStyle.entries.firstOrNull { it.name == h } ?: HomeStyle.BANDS
        _name.value = c.getSharedPreferences("ui", Context.MODE_PRIVATE).getString("name", "") ?: ""
    }

    fun setStyle(c: Context, s: TaskStyle) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putString("style", s.name).apply()
        _style.value = s
    }
}
