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
import java.util.zip.ZipFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Opt-in, local-only static integration test. Never installs or launches the APK. */
@EnabledIfEnvironmentVariable(named = "CHATGPT_TEST_APK", matches = ".+")
class RealApkTest {
    @Test fun `patches exact analyzed base APK and recompiles resources`() = runBlocking<Unit> { verifyApk(false) }
    @Test fun `opt-in diagnostics preserve original instructions and contain only allowlisted logging`() = runBlocking<Unit> { verifyApk(true) }

    private suspend fun verifyApk(trace: Boolean) {
        val input = File(System.getenv("CHATGPT_TEST_APK"))
        val sha = MessageDigest.getInstance("SHA-256").digest(input.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("979d758415b99ecf05118bce536b4e5a9eb9ea68ebdf77bbf064a57bb924d771", sha)
        val originalAuth = mutableMapOf<String, ClassDef>()
        ZipFile(input).use { zip ->
            zip.entries().asSequence().filter { it.name.matches(Regex("classes[0-9]*\\.dex")) }.forEach { entry ->
                val dex = DexBackedDexFile.fromInputStream(null, zip.getInputStream(entry).buffered())
                dex.classes.filter { it.type in authComparisonTypes || it.type in traceFingerprints }.forEach { originalAuth[it.type] = it }
            }
        }
        val temporary = Files.createTempDirectory("sibbl-chatgpt-static-test-").toFile()
        val label = System.getenv("CHATGPT_TEST_APP_NAME") ?: DEFAULT_APP_NAME
        cloneChatGptPatch.options["appName"] = label
        loginCallbackPatch.options["authTrace"] = trace
        try {
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
                        assertTrue(element.getAttribute("android:authorities").split(';').all {
                            it.startsWith("$CLONE_PACKAGE.") || it.startsWith("${CLONE_PACKAGE}_") || it.startsWith('@')
                        })
                    if (element.tagName == "service" && element.getAttribute("android:name").endsWith(".ChatGptAuthTokenService")) {
                        assertEquals("$CLONE_PACKAGE.permission.GET_AUTH_TOKEN", element.getAttribute("android:permission"))
                        tokenServiceFound = true
                    }
                    if (element.tagName == "data" && element.getAttribute("android:pathPrefix") == "/android/com.openai.chatgpt/callback")
                        callbackFound = true
                }
                assertTrue(callbackFound, "Original OAuth callback must remain intact in the baseline")
                assertTrue(tokenServiceFound)
                assertEquals(APP_NAME_REFERENCE,
                    (manifest.getElementsByTagName("application").item(0) as Element).getAttribute("android:label"))
                val main = (0 until manifest.getElementsByTagName("activity").length)
                    .map { manifest.getElementsByTagName("activity").item(it) as Element }
                    .single { it.getAttribute("android:name") == "com.openai.chatgpt.MainActivity" }
                assertEquals(APP_NAME_REFERENCE, main.getAttribute("android:label"))
                val stringsFile = temporary.walkTopDown().first {
                    it.name == "strings.xml" && it.isFile && it.readText().contains(APP_NAME_RESOURCE)
                }
                val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsFile)
                    .getElementsByTagName("string")
                val labelResource = (0 until strings.length).map { strings.item(it) as Element }
                    .single { it.getAttribute("name") == APP_NAME_RESOURCE }
                assertEquals(androidAppName(label), labelResource.textContent)
                val result = patcher.get()
                var patchedMethodFound = false
                val patchedAuth = mutableMapOf<String, ClassDef>()
                result.dexFiles.forEach { dex ->
                    val dexFile = DexBackedDexFile.fromInputStream(null, dex.stream.buffered())
                    dexFile.classes.filter { it.type in authComparisonTypes || it.type in traceFingerprints }.forEach { patchedAuth[it.type] = it }
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
                // Static caller-contract evidence, checked on both the original and clone.
                assertExistingBrowserContracts(originalAuth)
                assertExistingBrowserContracts(patchedAuth)
                if (!trace) {
                    assertAuthDifferential(originalAuth.filterKeys { it in authComparisonTypes }, patchedAuth.filterKeys { it in authComparisonTypes })
                    traceFingerprints.forEach { (type, hash) -> assertEquals(hash, traceClassHash(patchedAuth.getValue(type))) }
                } else {
                    assertTraceIntegration(originalAuth, patchedAuth)
                }
                val compiled = result.resources.resourcesApk
                assertNotNull(compiled)
                assertTrue(compiled!!.length() > 0)
                System.getenv("CHATGPT_TEST_RESOURCE_OUTPUT")?.let { compiled.copyTo(File(it), overwrite = true) }
            }
        } finally {
            cloneChatGptPatch.options["appName"] = DEFAULT_APP_NAME
            loginCallbackPatch.options["authTrace"] = false
        }
    }
}
