package net.sibbl.chatgpt

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.Opcode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Opt-in, local-only static integration test. Never installs or launches the APK. */
@EnabledIfEnvironmentVariable(named = "CHATGPT_TEST_APK", matches = ".+")
class RealApkTest {
    @Test fun `patches exact analyzed base APK and recompiles resources`() = runBlocking {
        val input = File(System.getenv("CHATGPT_TEST_APK"))
        val sha = MessageDigest.getInstance("SHA-256").digest(input.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("979d758415b99ecf05118bce536b4e5a9eb9ea68ebdf77bbf064a57bb924d771", sha)
        val temporary = Files.createTempDirectory("sibbl-chatgpt-static-test-").toFile()
        Patcher(PatcherConfig(input, temporaryFilesPath = temporary)).use { patcher ->
            patcher += setOf(loginCallbackPatch)
            val results = patcher().toList()
            assertTrue(results.isNotEmpty())
            results.forEach { assertNull(it.exception) }
            val manifestFile = temporary.walkTopDown().first { it.name == "AndroidManifest.xml" && it.isFile }
            val manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
            assertEquals(CLONE_PACKAGE, manifest.documentElement.getAttribute("package"))
            val elements = manifest.getElementsByTagName("*")
            var callbackFound = false
            var tokenServiceFound = false
            for (i in 0 until elements.length) {
                val element = elements.item(i) as Element
                if (element.tagName == "provider" && element.parentNode.nodeName == "application")
                    assertFalse(element.getAttribute("android:authorities").contains(ORIGINAL_PACKAGE))
                if (element.tagName == "service" && element.getAttribute("android:name").endsWith(".ChatGptAuthTokenService")) {
                    assertEquals("$CLONE_PACKAGE.permission.GET_AUTH_TOKEN", element.getAttribute("android:permission"))
                    tokenServiceFound = true
                }
                if (element.tagName == "data" && element.getAttribute("android:pathPrefix") == "/android/com.openai.chatgpt/callback")
                    callbackFound = true
            }
            assertTrue(callbackFound, "Original OAuth callback must remain intact in the baseline")
            assertTrue(tokenServiceFound)
            val result = patcher.get()
            var patchedMethodFound = false
            result.dexFiles.forEach { dex ->
                val dexFile = DexBackedDexFile.fromInputStream(null, dex.stream.buffered())
                dexFile.classes.firstOrNull { it.type == "Li280;" }?.let { cls ->
                    val method = cls.methods.single { it.name == "invoke" && it.parameterTypes.isEmpty() }
                    val instructions = method.implementation!!.instructions.toList()
                    val literalCount = instructions.count {
                        ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == ORIGINAL_PACKAGE
                    }
                    assertEquals(2, literalCount, "Only the two redirect components must be fixed")
                    // Both replaced move-result instructions are gone; the third package read remains dynamic.
                    val packageReads = instructions.indices.filter {
                        val ref = (instructions[it] as? ReferenceInstruction)?.reference
                        ref is com.android.tools.smali.dexlib2.iface.reference.MethodReference && ref.name == "getPackageName"
                    }
                    assertEquals(3, packageReads.size)
                    assertEquals(Opcode.CONST_STRING, instructions[packageReads[0] + 1].opcode)
                    assertEquals(Opcode.CONST_STRING, instructions[packageReads[1] + 1].opcode)
                    assertEquals(Opcode.MOVE_RESULT_OBJECT, instructions[packageReads[2] + 1].opcode)
                    patchedMethodFound = true
                }
            }
            assertTrue(patchedMethodFound)
            val compiled = result.resources.resourcesApk
            assertNotNull(compiled)
            assertTrue(compiled!!.length() > 0)
        }
    }
}
