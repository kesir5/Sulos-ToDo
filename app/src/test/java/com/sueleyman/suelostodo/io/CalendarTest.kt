package com.sueleyman.suelostodo.io

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarTest {
    @Test fun parses_timed_allday_and_skips_cancelled() {
        val js = """{"items":[
          {"id":"a","summary":"Zahnarzt","location":"Praxis","start":{"dateTime":"2026-11-03T10:00:00+01:00"},"end":{"dateTime":"2026-11-03T11:30:00+01:00"}},
          {"id":"b","summary":"Urlaub","start":{"date":"2026-11-10"},"end":{"date":"2026-11-12"}},
          {"id":"c","status":"cancelled","summary":"weg","start":{"date":"2026-11-11"}},
          {"id":"d","start":{"date":"2026-11-12"},"description":"Zeile 1<br>Zeile <b>2</b>"}
        ]}"""
        val ev = GoogleCalendar.parseEvents(Json.parseToJsonElement(js).jsonObject, "Privat", 0xFF0000FF)
        assertEquals(listOf("a", "b", "d"), ev.map { it.id })
        assertEquals(false, ev[0].allDay)
        assertEquals(LocalDate.of(2026, 11, 10).toEpochDay(), ev[1].day)
        assertEquals("Ganztägig", ev[1].timeLabel)
        assertEquals("(ohne Titel)", ev[2].title)
        assertTrue(ev[2].toNote().contains("Zeile 1\nZeile 2"))
        assertTrue(ev[0].toNote().contains("📍 Praxis"))
    }
}
