package net.sibbl.chatgpt

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import java.net.URI
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import org.junit.jupiter.api.Assertions.*

/** These are APK symbol references, not distributed APK code or account/session data. */
internal val authComparisonTypes = setOf(
    "Li280;", "Ll690;", "Lv6k0;", "Lw6k0;", "Lugc;", "Lv7k0;", "Lkdl;",
    "Lehu0;", "Lofu0;", "Lwxd0;", "Lo8n0;", "Lwud0;", "Lgdr;", "Ljdr;",
    "Looo;", "Lvgx;", "Lamq0;", "Lfto0;", "Lt1r0;",
    "Lzh7;", "Lf280;", "Lx6k0;", "Lm8n0;", "Lxhv;", "Lu56;",
    "Laq1;", "Lcom/openai/valdi/integrity/b;",
    "Lcom/openai/feature/auth/impl/web/WebAuthenticationActivity;",
    "Lcom/openai/feature/auth/impl/web/WebRedirectActivity;",
    // Native password serialization, request/client wiring, and the existing browser route.
    NATIVE_REPOSITORY, "Ljd80;", "Lqd80;", "Lpa80;", "Lxl80;", "Lii7;", "Lfy0;",
    "Lrd6;", "Lh8o;", "Lpb6;", "Lnb6;", "Lpm40;", "Lnm40;", "Lgf80;",
    "Lrj40;", "Luj40;", "Lyj40;", "Lw780;", "Ltt1;", "Lib80;",
    "Lk56;", "Lln8;", "Lnit;", "Law;", "Le280;", "Lbi40;", "Lnw4;"
)

/** Re-encode each class separately so unrelated DEX pool indices cannot affect comparison. */
private fun canonicalClass(value: ClassDef): ByteArray {
    val store = MemoryDataStore()
    try {
        DexPool.writeTo(store, ImmutableDexFile(Opcodes.forApi(35), setOf(value)))
        return store.data
    } finally {
        store.close()
    }
}

private data class ConfigProjection(
    val clientIdSource: String,
    val googleClientIdSource: String,
    val redirect: String,
    val runtimePackage: String
)

/**
 * Offline string-dataflow projection of the inspected, straight-line configuration method.
 * Reads the actual original/patched instructions. Models only the string operations and
 * constructor arguments; never executes APK code, Android APIs, networking or a login.
 */
private fun projectConfig(method: Method, runtimePackage: String): ConfigProjection {
    val registers = mutableMapOf<Int, Any?>()
    var result: Any? = null
    var redirect: String? = null
    var clientId: String? = null
    var googleClientId: String? = null
    var installedPackage: String? = null
    for (instruction in requireNotNull(method.implementation).instructions) {
        require(instruction !is OffsetInstruction) { "Configuration now has control flow; review projection." }
        val reference = (instruction as? ReferenceInstruction)?.reference
        when (instruction.opcode) {
            Opcode.IGET_OBJECT, Opcode.SGET_OBJECT ->
                registers[(instruction as OneRegisterInstruction).registerA] = "field:$reference"
            Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16 -> {
                val move = instruction as TwoRegisterInstruction
                registers[move.registerA] = registers[move.registerB]
            }
            Opcode.MOVE_RESULT_OBJECT -> registers[(instruction as OneRegisterInstruction).registerA] = result
            Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO ->
                registers[(instruction as OneRegisterInstruction).registerA] = (reference as StringReference).string
            Opcode.NEW_INSTANCE -> if (reference.toString() == "Ljava/lang/StringBuilder;") {
                registers[(instruction as OneRegisterInstruction).registerA] = StringBuilder()
            }
            else -> if (reference is MethodReference) {
                val args = when (instruction) {
                    is FiveRegisterInstruction -> listOf(instruction.registerC, instruction.registerD,
                        instruction.registerE, instruction.registerF, instruction.registerG).take(instruction.registerCount)
                    is RegisterRangeInstruction -> (instruction.startRegister until
                        instruction.startRegister + instruction.registerCount).toList()
                    else -> error("Unexpected method instruction: ${instruction.opcode}")
                }
                result = when {
                    reference.name == "getPackageName" -> runtimePackage
                    reference.definingClass == "Ljava/lang/StringBuilder;" && reference.name == "append" ->
                        (registers[args[0]] as StringBuilder).append(registers[args[1]] as String)
                    reference.definingClass == "Ljava/lang/StringBuilder;" && reference.name == "toString" ->
                        (registers[args[0]] as StringBuilder).toString()
                    reference.definingClass == "Lv6k0;" && reference.name == "<init>" -> {
                        clientId = registers[args[1]] as String
                        googleClientId = registers[args[2]] as String
                        redirect = registers[args[3]] as String
                        null
                    }
                    reference.definingClass == "Lw6k0;" && reference.name == "<init>" -> {
                        installedPackage = registers[args.last()] as String
                        null
                    }
                    else -> null
                }
            }
        }
    }
    return ConfigProjection(requireNotNull(clientId), requireNotNull(googleClientId),
        requireNotNull(redirect), requireNotNull(installedPackage))
}

/** Verify the Auth Tab argument is derived from redirect.c, not from runtime getPackageName. */
private fun assertAuthTabScheme(classes: Map<String, ClassDef>, redirect: String) {
    val instructions = classes.getValue("Lamq0;").methods.single { it.name == "invoke" }
        .implementation!!.instructions.toList()
    fun reference(index: Int) = (instructions[index] as? ReferenceInstruction)?.reference
    val keyIndex = instructions.indices.single {
        (reference(it) as? StringReference)?.string == "androidx.browser.auth.extra.REDIRECT_SCHEME"
    }
    val loadIndex = (0 until keyIndex).last {
        val field = reference(it) as? FieldReference
        field?.definingClass == "Lv6k0;" && field.name == "c"
    }
    val sourceRegister = (instructions[loadIndex] as OneRegisterInstruction).registerA
    val parse = reference(loadIndex + 1) as MethodReference
    assertEquals("Landroid/net/Uri;", parse.definingClass)
    assertEquals("parse", parse.name)
    assertEquals(sourceRegister, (instructions[loadIndex + 1] as FiveRegisterInstruction).registerC)
    val uriRegister = (instructions[loadIndex + 2] as OneRegisterInstruction).registerA
    assertEquals(Opcode.MOVE_RESULT_OBJECT, instructions[loadIndex + 2].opcode)
    val scheme = reference(loadIndex + 3) as MethodReference
    assertEquals("Landroid/net/Uri;", scheme.definingClass)
    assertEquals("getScheme", scheme.name)
    assertEquals(uriRegister, (instructions[loadIndex + 3] as FiveRegisterInstruction).registerC)
    val schemeRegister = (instructions[loadIndex + 4] as OneRegisterInstruction).registerA
    assertEquals(Opcode.MOVE_RESULT_OBJECT, instructions[loadIndex + 4].opcode)
    // Only the null check and setData call precede the key: neither writes the scheme register.
    assertEquals(loadIndex + 7, keyIndex)
    assertEquals(Opcode.IF_EQZ, instructions[loadIndex + 5].opcode)
    assertEquals("setData", (reference(loadIndex + 6) as MethodReference).name)
    val put = reference(keyIndex + 1) as MethodReference
    assertEquals("Landroid/content/Intent;", put.definingClass)
    assertEquals("putExtra", put.name)
    val call = instructions[keyIndex + 1] as FiveRegisterInstruction
    assertEquals((instructions[keyIndex] as OneRegisterInstruction).registerA, call.registerD)
    assertEquals(schemeRegister, call.registerE)
    assertEquals(ORIGINAL_PACKAGE, URI(redirect).scheme)
}

internal fun assertAuthDifferential(original: Map<String, ClassDef>, patched: Map<String, ClassDef>) {
    assertEquals(authComparisonTypes, original.keys, "All original auth classes must be found")
    assertEquals(authComparisonTypes, patched.keys, "All patched auth classes must be found")
    (authComparisonTypes - "Li280;").forEach { type ->
        assertArrayEquals(canonicalClass(original.getValue(type)), canonicalClass(patched.getValue(type)),
            "Unexpected class change in $type")
    }
    fun config(classes: Map<String, ClassDef>) = classes.getValue("Li280;").methods.single {
        it.name == "invoke" && it.parameterTypes.isEmpty()
    }
    val expected = "com.openai.chatgpt://auth.openai.com/android/com.openai.chatgpt/callback"
    val stock = projectConfig(config(original), ORIGINAL_PACKAGE)
    val cloneWithoutFix = projectConfig(config(original), CLONE_PACKAGE)
    val clone = projectConfig(config(patched), CLONE_PACKAGE)
    assertEquals(expected, stock.redirect)
    assertNotEquals(expected, cloneWithoutFix.redirect)
    assertEquals(expected, clone.redirect)
    assertAuthTabScheme(original, stock.redirect)
    assertAuthTabScheme(patched, clone.redirect)
    assertEquals(stock.clientIdSource, clone.clientIdSource)
    assertEquals(stock.googleClientIdSource, clone.googleClientIdSource)
    assertEquals(CLONE_PACKAGE, clone.runtimePackage)
    assertEquals(ORIGINAL_PACKAGE, stock.runtimePackage)
    // Custom package settings must preserve callback identity without changing runtime identity.
    val custom = projectConfig(config(patched), "example.private.chatgpt")
    assertEquals(expected, custom.redirect)
    assertEquals("example.private.chatgpt", custom.runtimePackage)
}
