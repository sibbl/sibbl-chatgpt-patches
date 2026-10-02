package net.sibbl.chatgpt

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element

class AppNameTest {
    private fun xml(value: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(value.byteInputStream())

    @Test fun `Unicode punctuation and whitespace survive XML serialization`() {
        val value = " @privé & \"Familie\" <克隆> 🚀 \\ ' 100%\t\n "
        val document = xml("<resources/>")
        writeAppNameResource(document, value)
        val output = StringWriter()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(output))
        val roundTrip = xml(output.toString()).getElementsByTagName("string").item(0) as Element
        assertEquals(APP_NAME_RESOURCE, roundTrip.getAttribute("name"))
        assertEquals("false", roundTrip.getAttribute("formatted"))
        assertEquals("false", roundTrip.getAttribute("translatable"))
        assertEquals("\" @privé & \\\"Familie\\\" <克隆> 🚀 \\\\ \\' 100%\\t\\n \"", roundTrip.textContent)
        assertTrue(output.toString().contains("&amp;"))
        assertTrue(output.toString().contains("&lt;克隆&gt;"))
        assertEquals("\"@string/not_a_reference\"", androidAppName("@string/not_a_reference"))
    }

    @Test fun `application and all launcher activity and alias labels are changed`() {
        val document = xml("""
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
              <application android:label="@string/original">
                <activity android:name="Main"><intent-filter>
                  <action android:name="android.intent.action.MAIN"/>
                  <category android:name="android.intent.category.LAUNCHER"/>
                </intent-filter></activity>
                <activity-alias android:name="OtherIcon" android:label="Override"><intent-filter>
                  <action android:name="android.intent.action.MAIN"/>
                  <category android:name="android.intent.category.LEANBACK_LAUNCHER"/>
                </intent-filter></activity-alias>
                <activity android:name="NonLauncher" android:label="Keep"/>
                <activity-alias android:name="ShareTarget" android:label="@string/original"/>
              </application>
            </manifest>
        """.trimIndent())
        renameAppLabels(document)
        fun label(tag: String, index: Int = 0) =
            (document.getElementsByTagName(tag).item(index) as Element).getAttribute("android:label")
        assertEquals(APP_NAME_REFERENCE, label("application"))
        assertEquals(APP_NAME_REFERENCE, label("activity"))
        assertEquals(APP_NAME_REFERENCE, label("activity-alias"))
        assertEquals("Keep", label("activity", 1))
        assertEquals(APP_NAME_REFERENCE, label("activity-alias", 1))
    }

    @Test fun `blank invalid Unicode and conflicting resources are rejected`() {
        listOf("", " \t\n ", "bad\u0000name", "bad\uD800name", "bad\uFFFFname").forEach {
            assertThrows(IllegalArgumentException::class.java) { validateAppName(it) }
        }
        val document = xml("<resources/>")
        writeAppNameResource(document, DEFAULT_APP_NAME)
        assertThrows(IllegalArgumentException::class.java) { writeAppNameResource(document, "Different") }
    }

    @Test fun `defaults are original package plus clone and configurable launcher name`() {
        assertEquals("com.openai.chatgpt.clone", CLONE_PACKAGE)
        assertEquals(CLONE_PACKAGE, cloneChatGptPatch.options["packageName"].default)
        assertEquals("ChatGPT clone", cloneChatGptPatch.options["appName"].default)
    }
}
