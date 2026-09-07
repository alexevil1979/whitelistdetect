package ru.whitelist.pulse.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.whitelist.pulse.domain.usecase.DomainListParser

class DomainListParserTest {

    @Test
    fun parsesPlainHostsAndSkipsComments() {
        val raw = """
            # banks
            sberbank.ru
            https://www.tinkoff.ru/cards
            MAIL.RU
            not a domain
            localhost
            avito.ru
        """.trimIndent()
        val result = DomainListParser.parse(raw)
        assertEquals(listOf("sberbank.ru", "www.tinkoff.ru", "mail.ru", "avito.ru"), result.hosts)
        assertTrue(result.rejected.contains("not a domain"))
        assertTrue(result.rejected.contains("localhost"))
    }

    @Test
    fun normalizeStripsSchemePathAndPort() {
        assertEquals("github.com", DomainListParser.normalize("https://github.com:443/org/repo?q=1"))
        assertEquals("ozon.ru", DomainListParser.normalize("ozon.ru/"))
        assertNull(DomainListParser.normalize(""))
        assertNull(DomainListParser.normalize("http://192.168.0.1"))
        assertNull(DomainListParser.normalize("bad_host"))
    }

    @Test
    fun deduplicatesHosts() {
        val result = DomainListParser.parse("yandex.ru\nYandex.ru\nhttps://yandex.ru")
        assertEquals(listOf("yandex.ru"), result.hosts)
    }
}
