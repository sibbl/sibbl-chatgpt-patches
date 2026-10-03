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
internal fun normalized(method: Method): Pair<List<String>, List<String>> {
    val impl = method.implementation ?: return emptyList<String>() to emptyList()
    val instructions = impl.instructions.toList()
    fun isTrace(i: Instruction): Boolean = ((i as? ReferenceInstruction)?.reference as? MethodReference)
        ?.let { it.name.startsWith(TRACE_PREFIX) } == true
    // DEX writers may add/remove alignment padding immediately before payloads.
    fun routeScratch(index: Int): Boolean {
        val move = instructions[index] as? TwoRegisterInstruction ?: return false
        val ref = (instructions.getOrNull(index + 1) as? ReferenceInstruction)?.reference as? MethodReference
        return instructions[index].opcode == Opcode.MOVE_OBJECT_FROM16 && move.registerA == 10 &&
            move.registerB == 33 && ref?.definingClass == BROWSER_OWNER &&
            ref.name.removePrefix(TRACE_PREFIX) in routeTraceKinds
    }
    fun ignored(index: Int): Boolean = isTrace(instructions[index]) || routeScratch(index) ||
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
    val routeKinds = patched.getValue(BROWSER_OWNER).methods.filter { it.name.startsWith(TRACE_PREFIX) }
        .map { it.name.removePrefix(TRACE_PREFIX) }
    assertEquals(1, routeKinds.size)
    assertTrue(routeKinds.single() in routeTraceKinds)
    val expectedHelpers = routeKinds.toSet() + setOf(
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
                if (type == BROWSER_OWNER && method.name == "c") assertRouteHook(method)
                val old = originals.getValue(method.toString())
                assertEquals(old.implementation?.registerCount, method.implementation?.registerCount)
                assertEquals(normalized(old), normalized(method), "Original operands/control flow changed: $method")
                if (type == "Lfy0;" && method.name == "invokeSuspend") {
                    val beforeCode = old.implementation!!.instructions.toList()
                    val afterCode = method.implementation!!.instructions.toList()
                    fun addresses(code: List<Instruction>): List<Int> {
                        var address = 0
                        return code.map { i -> address.also { address += i.codeUnits } }
                    }
                    val beforeAddresses = addresses(beforeCode)
                    val afterAddresses = addresses(afterCode)
                    val start = beforeCode.indices.single {
                        beforeCode[it].opcode == Opcode.CHECK_CAST &&
                            (beforeCode[it] as ReferenceInstruction).reference.toString() == "Lpa80;"
                    }
                    val originalReturn = (start until beforeCode.size).first { beforeCode[it].opcode == Opcode.RETURN_OBJECT }
                    val rawHook = afterCode.indices.single {
                        ((afterCode[it] as? ReferenceInstruction)?.reference as? MethodReference)?.name == TRACE_PREFIX + "NATIVE_RAW_HTTP"
                    }
                    fun incoming(code: List<Instruction>, offsets: List<Int>, target: Int): Int = code.indices.count {
                        val branch = code[it] as? OffsetInstruction
                        branch != null && offsets[it] + branch.codeOffset == offsets[target]
                    }
                    val originalIncoming = incoming(beforeCode, beforeAddresses, originalReturn)
                    assertTrue(originalIncoming > 0, "Pinned response must have an incoming resumption branch")
                    assertEquals(originalIncoming, incoming(afterCode, afterAddresses, rawHook), "Resumption must reach the raw-status helper")
                    assertEquals(Opcode.RETURN_OBJECT, afterCode[rawHook + 1].opcode)
                }
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

/** Re-encoded APK hook: exact two-instruction delta, untouched arguments, state 0 only. */
private fun assertRouteHook(method: Method) {
    val code = method.implementation!!.instructions.toList()
    var address = 0
    val offsets = code.map { i -> address.also { address += i.codeUnits } }
    val call = code.indices.single {
        ((code[it] as? ReferenceInstruction)?.reference as? MethodReference)?.name?.removePrefix(TRACE_PREFIX) in routeTraceKinds
    }
    val start = call - 1
    val move = code[start] as TwoRegisterInstruction
    assertEquals(Opcode.MOVE_OBJECT_FROM16, code[start].opcode)
    assertEquals(10, move.registerA)
    assertEquals(33, move.registerB)
    assertEquals(Opcode.INVOKE_STATIC, code[call].opcode)
    val args = code[call] as FiveRegisterInstruction
    assertEquals(5, args.registerCount)
    assertEquals(listOf(0, 1, 2, 7, 10), listOf(args.registerC, args.registerD, args.registerE, args.registerF, args.registerG))
    val ref = (code[call] as ReferenceInstruction).reference as MethodReference
    assertEquals(ROUTE_TRACE_PARAMETERS, ref.parameterTypes.joinToString(""))
    assertEquals("V", ref.returnType)
    assertEquals(41, method.implementation!!.registerCount)
    fun reachable(entry: Int): Boolean {
        val seen = mutableSetOf<Int>(); val pending = ArrayDeque<Int>(); pending += entry
        while (pending.isNotEmpty()) {
            val pc = pending.removeFirst()
            if (pc == start) return true
            if (pc !in code.indices || !seen.add(pc)) continue
            val i = code[pc]
            fun target(delta: Int) = offsets.indexOf(offsets[pc] + delta).also { assertTrue(it >= 0) }
            when {
                i.opcode in setOf(Opcode.RETURN, Opcode.RETURN_OBJECT, Opcode.RETURN_VOID, Opcode.THROW) -> Unit
                i.opcode in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32) -> pending += target((i as OffsetInstruction).codeOffset)
                i.opcode in setOf(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH) -> {
                    (code[target((i as OffsetInstruction).codeOffset)] as SwitchPayload).switchElements.forEach { pending += target(it.offset) }
                    pending += pc + 1
                }
                i is OffsetInstruction -> { pending += target(i.codeOffset); pending += pc + 1 }
                i !is PayloadInstruction -> pending += pc + 1
            }
        }
        return false
    }
    val switch = code.indices.single { code[it].opcode == Opcode.PACKED_SWITCH }
    val payload = offsets.indexOf(offsets[switch] + (code[switch] as OffsetInstruction).codeOffset)
    val states = (code[payload] as SwitchPayload).switchElements
    assertEquals((0..8).toSet(), states.map { it.key }.toSet())
    states.forEach {
        assertEquals(it.key == 0, reachable(offsets.indexOf(offsets[switch] + it.offset)), "Only fresh route invocation is traced: state ${it.key}")
    }
}
