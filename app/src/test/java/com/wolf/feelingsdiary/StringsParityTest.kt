package com.wolf.feelingsdiary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringsParityTest {
    private fun names(path: String): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val result = mutableSetOf<String>()
        for (tag in listOf("string", "plurals")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                result += "$tag:${nodes.item(i).attributes.getNamedItem("name").nodeValue}"
            }
        }
        return result
    }

    @Test
    fun englishAndGermanDefineTheSameResources() {
        val en = names("src/main/res/values/strings.xml")
        val de = names("src/main/res/values-de/strings.xml")
        assertTrue(en.isNotEmpty())
        assertEquals(en, de)
    }
}
