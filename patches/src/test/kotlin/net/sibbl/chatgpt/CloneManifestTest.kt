package net.sibbl.chatgpt

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.xml.parsers.DocumentBuilderFactory

class CloneManifestTest {
    private fun xml(text: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(text.byteInputStream())
    private val fixture = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.openai.chatgpt">
          <permission android:name="com.openai.chatgpt.PRIVATE"/>
          <uses-permission android:name="com.openai.chatgpt.PRIVATE"/>
          <uses-permission android:name="android.permission.INTERNET"/>
          <queries><provider android:authorities="external.store"/></queries>
          <application><service android:name="example.AuthService" android:permission="com.openai.chatgpt.PRIVATE"/>
            <provider android:name="example.Provider"
            android:authorities="com.openai.chatgpt.files;shared.authority;@string/provider_id"/>
            <activity android:name="example.Callback"><intent-filter>
              <data android:scheme="example-login" android:host="callback"/>
            </intent-filter></activity>
          </application>
        </manifest>
    """.trimIndent()

    @Test fun `clones declarations and references without rewriting callback`() {
        val manifest = xml(fixture)
        val dataBefore = manifest.getElementsByTagName("data").item(0).cloneNode(true)
        assertEquals(setOf("provider_id"), cloneManifest(manifest, CLONE_PACKAGE))
        assertEquals(CLONE_PACKAGE, manifest.documentElement.getAttribute("package"))
        fun attr(tag: String, name: String, index: Int = 0) = manifest.getElementsByTagName(tag)
            .item(index).attributes.getNamedItem(name).nodeValue
        assertEquals("$CLONE_PACKAGE.PRIVATE", attr("permission", "android:name"))
        assertEquals("$CLONE_PACKAGE.PRIVATE", attr("uses-permission", "android:name"))
        assertEquals("android.permission.INTERNET", attr("uses-permission", "android:name", 1))
        assertEquals("$CLONE_PACKAGE.files;${CLONE_PACKAGE}_shared.authority;@string/provider_id",
            attr("provider", "android:authorities", 1))
        assertEquals("external.store", attr("provider", "android:authorities"))
        assertEquals("$CLONE_PACKAGE.PRIVATE", attr("service", "android:permission"))
        assertTrue(dataBefore.isEqualNode(manifest.getElementsByTagName("data").item(0)))
    }

    @Test fun `provider string resources change but unrelated strings do not`() {
        val strings = xml("<resources><string name='provider_id'>com.openai.chatgpt.files</string>" +
            "<string name='other'>com.openai.chatgpt</string></resources>")
        cloneProviderResources(strings, setOf("provider_id"), CLONE_PACKAGE)
        assertEquals("$CLONE_PACKAGE.files", strings.getElementsByTagName("string").item(0).textContent)
        assertEquals(ORIGINAL_PACKAGE, strings.getElementsByTagName("string").item(1).textContent)
    }

    @Test fun `guards reject mismatched inputs and disabled mandatory options`() {
        fun valid(pkg: String = ORIGINAL_PACKAGE, version: String = CANDIDATE_VERSION,
                  code: String = CANDIDATE_CODE, replacement: String = CLONE_PACKAGE,
                  permissions: Boolean = true, providers: Boolean = true) =
            validateInput(pkg, version, code, replacement, permissions, providers)
        valid()
        assertThrows(IllegalArgumentException::class.java) { valid(pkg = "other.app") }
        assertThrows(IllegalArgumentException::class.java) { valid(version = "1.0") }
        assertThrows(IllegalArgumentException::class.java) { valid(code = "2626527") }
        assertThrows(IllegalArgumentException::class.java) { valid(replacement = ORIGINAL_PACKAGE) }
        assertThrows(IllegalArgumentException::class.java) { valid(replacement = "bad/package") }
        assertThrows(IllegalArgumentException::class.java) { valid(permissions = false) }
        assertThrows(IllegalArgumentException::class.java) { valid(providers = false) }
    }

    @Test fun `rejects already cloned input`() {
        val manifest = xml(fixture)
        cloneManifest(manifest, CLONE_PACKAGE)
        assertThrows(IllegalArgumentException::class.java) { cloneManifest(manifest, CLONE_PACKAGE) }
    }
}
