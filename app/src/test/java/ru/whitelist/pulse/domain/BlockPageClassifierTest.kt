package ru.whitelist.pulse.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.whitelist.pulse.domain.usecase.BlockPageClassifier

class BlockPageClassifierTest {

    @Test
    fun detectsRussianBlockPage() {
        assertTrue(
            BlockPageClassifier.isBlockPage(
                "<html><body>Доступ к информационному ресурсу ограничен</body></html>",
                200,
            ),
        )
    }

    @Test
    fun detectsCaptivePortalMarker() {
        assertTrue(BlockPageClassifier.isBlockPage("captiveportal login required", 302))
    }

    @Test
    fun ignoresOrdinaryHtml() {
        assertFalse(
            BlockPageClassifier.isBlockPage(
                "<html><title>Яндекс</title><body>Поиск</body></html>",
                200,
            ),
        )
    }

    @Test
    fun ignoresEmptyAndErrorCodes() {
        assertFalse(BlockPageClassifier.isBlockPage(null, 200))
        assertFalse(BlockPageClassifier.isBlockPage("доступ ограничен", 500))
    }
}
