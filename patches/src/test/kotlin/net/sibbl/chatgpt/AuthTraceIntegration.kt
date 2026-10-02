package net.sibbl.chatgpt

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import org.junit.jupiter.api.Assertions.*

/** Compare original control flow and operands after removing ONLY our injected calls. */
private fun normalized(method: Method): Pair<List<String>, List<String>> {
    val impl = method.implementation ?: return emptyList<String>() to emptyList()
    val instructions = impl.instructions.toList()
    fun isTrace(i: Instruction): Boolean = ((i as? ReferenceInstruction)?.reference as? MethodReference)
        ?.let { it.name.startsWith(TRACE_PREFIX) } == true
    // DEX writers may add/remove alignment padding immediately before payloads.
    fun ignored(index: Int): Boolean = isTrace(instructions[index]) ||
        (instructions[index].opcode == Opcode.NOP && instructions.getOrNull(index + 1) is PayloadInstruction)
    val addresses = mutableListOf<Int>()
    var address = 0
    instructions.forEach { addresses += address; address += it.codeUnits }
    addresses += address
    val indices = mutableListOf<Int>()
    var originalIndex = 0
    instructions.indices.forEach { indices += originalIndex; if (!ignored(it)) originalIndex++ }
    indices += originalIndex
    fun target(address: Int): Int {
        val position = addresses.indexOf(address)
        assertTrue(position >= 0, "Control-flow target must be an instruction boundary")
        return indices[position]
    }
    val body = instructions.mapIndexedNotNull { index, i ->
        if (isTrace(i)) {
            assertTrue(i.opcode == Opcode.INVOKE_STATIC || i.opcode == Opcode.INVOKE_STATIC_RANGE)
            return@mapIndexedNotNull null
        }
        if (ignored(index)) return@mapIndexedNotNull null
        buildString {
            append(i.opcode)
            if (i is OneRegisterInstruction) append(" a=${i.registerA}")
            if (i is TwoRegisterInstruction) append(" b=${i.registerB}")
            if (i is ThreeRegisterInstruction) append(" c=${i.registerC}")
            if (i is FiveRegisterInstruction) append(" args=" + listOf(i.registerC, i.registerD, i.registerE, i.registerF, i.registerG).take(i.registerCount))
            if (i is RegisterRangeInstruction) append(" range=${i.startRegister}:${i.registerCount}")
            if (i is WideLiteralInstruction) append(" literal=${i.wideLiteral}")
            if (i is ReferenceInstruction) append(" ref=${i.reference}")
            if (i is OffsetInstruction) append(" target=${target(addresses[index] + i.codeOffset)}")
            if (i is SwitchPayload) {
                val switchIndex = instructions.indices.single {
                    instructions[it].opcode in setOf(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH) &&
                        addresses[it] + (instructions[it] as OffsetInstruction).codeOffset == addresses[index]
                }
                i.switchElements.forEach { append(" switch=${it.key}:${target(addresses[switchIndex] + it.offset)}") }
            }
            if (i is ArrayPayload) append(" array=${i.elementWidth}:${i.arrayElements}")
        }
    }
    val catches = impl.tryBlocks.map { block ->
        "${target(block.startCodeAddress)}:${target(block.startCodeAddress + block.codeUnitCount)}:" +
            block.exceptionHandlers.joinToString { "${it.exceptionType}:${target(it.handlerCodeAddress)}" }
    }
    return body to catches
}

internal fun assertTraceIntegration(original: Map<String, ClassDef>, patched: Map<String, ClassDef>) {
    val expectedHelpers = setOf(
        "NATIVE_PASSWORD_SUBMIT", "NATIVE_BEGIN", "NATIVE_BEGIN_ENTER", "NATIVE_STEP", "NATIVE_STEP_ENTER",
        "PAGE", "AUTH_UI", "BROWSER_RESULT", "BROWSER_DISPATCH_ENTER", "CALLBACK_RECEIVED",
        "CALLBACK_URI_MISMATCH", "CALLBACK_STATE_MISMATCH", "CALLBACK_REMOTE_ERROR", "CALLBACK_CODE_PRESENT",
        "TOKEN_HTTP", "TOKEN_EXCHANGE_ENTER", "NATIVE_HTTP_DISPATCH", "NATIVE_RAW_HTTP",
        "NATIVE_BEFORE_MAP", "NATIVE_AFTER_MAP", "NATIVE_TRANSPORT"
    )
    val found = mutableSetOf<String>()
    val called = mutableSetOf<String>()
    original.forEach { (type, before) ->
        if (type == "Li280;") return@forEach // Existing callback fix has its own independent integration test.
        val after = patched.getValue(type)
        val originals = before.methods.associateBy { it.toString() }
        after.methods.forEach { method ->
            if (method.name.startsWith(TRACE_PREFIX)) {
                val kind = method.name.removePrefix(TRACE_PREFIX)
                assertTrue(kind in expectedHelpers)
                found += kind
                method.implementation!!.instructions.forEach { instruction ->
                    val reference = (instruction as? ReferenceInstruction)?.reference
                    if (reference is FieldReference) {
                        assertTrue(original.getValue(reference.definingClass).fields.any { it.toString() == reference.toString() }, "Unresolved diagnostic field: $reference")
                    }
                    if (reference is MethodReference && reference.definingClass == "Ligy;") {
                        assertTrue(original.getValue(reference.definingClass).methods.any { it.toString() == reference.toString() }, "Unresolved status getter")
                    }
                }
                val generated = compileTraceHelper(type, kind, kind)
                assertEquals(normalized(generated), normalized(method), "Unexpected helper bytecode: $kind")
            } else {
                val old = originals.getValue(method.toString())
                assertEquals(old.implementation?.registerCount, method.implementation?.registerCount)
                assertEquals(normalized(old), normalized(method), "Original operands/control flow changed: $method")
                method.implementation?.instructions?.forEach { i ->
                    val ref = (i as? ReferenceInstruction)?.reference as? MethodReference
                    if (ref?.name?.startsWith(TRACE_PREFIX) == true) {
                        assertEquals(type, ref.definingClass, "Helper must remain in already initialized caller class")
                        called += ref.name.removePrefix(TRACE_PREFIX)
                    }
                }
            }
        }
        assertEquals(originals.keys, after.methods.filterNot { it.name.startsWith(TRACE_PREFIX) }.map { it.toString() }.toSet())
    }
    assertEquals(expectedHelpers, found)
    assertEquals(expectedHelpers, called)
    AuthTraceTest().`every compiled logger accepts only constant allowlisted output and catches its failures`()
}
