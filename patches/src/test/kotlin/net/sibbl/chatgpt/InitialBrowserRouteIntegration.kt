package net.sibbl.chatgpt

import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.jupiter.api.Assertions.*

/** Verify the entire delta, then undo ONLY that exact delta for the ordinary differential. */
internal fun checkedBrowserBaseline(before: ClassDef, after: ClassDef): ClassDef {
    assertEquals(browserFingerprints.getValue(BROWSER_OWNER), traceClassHash(before))
    val cls = MutableClass(after)
    val helper = cls.methods.single { it.name == BROWSER_HELPER }
    val expected = compileTraceMethod(browserHelperSmali())
    assertEquals(expected.accessFlags, helper.accessFlags)
    assertEquals(expected.parameterTypes, helper.parameterTypes)
    assertEquals(expected.returnType, helper.returnType)
    assertEquals(expected.implementation!!.registerCount, helper.implementation!!.registerCount)
    assertEquals(normalized(expected), normalized(helper))
    val method = cls.methods.single { it.name == "c" }
    assertEquals(41, method.implementation!!.registerCount)
    val code = method.implementation!!.instructions.toList()
    var address = 0
    val offsets = code.map { instruction -> address.also { address += instruction.codeUnits } }
    val call = code.indices.single {
        (code[it] as? ReferenceInstruction)?.reference?.toString() == BROWSER_HELPER_REF
    }
    val start = call - 1
    assertEquals(Opcode.MOVE_OBJECT_FROM16, code[start].opcode)
    assertEquals(10, (code[start] as TwoRegisterInstruction).registerA)
    assertEquals(33, (code[start] as TwoRegisterInstruction).registerB)
    assertEquals(Opcode.INVOKE_STATIC, code[call].opcode)
    val args = code[call] as FiveRegisterInstruction
    assertEquals(5, args.registerCount)
    assertEquals(listOf(0, 1, 2, 7, 10), listOf(args.registerC, args.registerD, args.registerE, args.registerF, args.registerG))
    assertEquals(Opcode.MOVE_RESULT, code[start + 2].opcode)
    assertEquals(10, (code[start + 2] as OneRegisterInstruction).registerA)
    assertEquals(Opcode.IF_EQZ, code[start + 3].opcode)
    assertEquals(10, (code[start + 3] as OneRegisterInstruction).registerA)
    assertEquals(offsets[start + 5], offsets[start + 3] + (code[start + 3] as OffsetInstruction).codeOffset)
    assertEquals(Opcode.CONST_4, code[start + 4].opcode)
    assertEquals(4, (code[start + 4] as OneRegisterInstruction).registerA)
    assertEquals(0, (code[start + 4] as NarrowLiteralInstruction).narrowLiteral)
    assertEquals(Opcode.IF_EQZ, code[start + 5].opcode)
    assertEquals(4, (code[start + 5] as OneRegisterInstruction).registerA)

    // Evaluate the actual re-encoded APK injection against the same 960-row matrix.
    val fixture = compileTraceMethod("""
        .class public LRouteFixture;
        .super Ljava/lang/Object;
        .method public static select()I
        .registers 41
        ${browserSelectionSmali()}
        :original_selection
        return v4
        .end method
    """.trimIndent())
    assertEquals(960, InitialBrowserRouteTest().matrix(code.subList(start, start + 5) + fixture.implementation!!.instructions.last(), helper))

    fun reachable(entry: Int, wanted: Int): Boolean {
        val seen = mutableSetOf<Int>(); val pending = ArrayDeque<Int>(); pending += entry
        while (pending.isNotEmpty()) {
            val pc = pending.removeFirst()
            if (pc == wanted) return true
            if (pc !in code.indices || !seen.add(pc)) continue
            val i = code[pc]
            fun target(delta: Int) = offsets.indexOf(offsets[pc] + delta).also { assertTrue(it >= 0) }
            when {
                i.opcode in setOf(Opcode.RETURN, Opcode.RETURN_OBJECT, Opcode.RETURN_VOID, Opcode.THROW) -> Unit
                i.opcode in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32) -> pending += target((i as OffsetInstruction).codeOffset)
                i.opcode in setOf(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH) -> {
                    val payload = code[target((i as OffsetInstruction).codeOffset)] as SwitchPayload
                    payload.switchElements.forEach { pending += target(it.offset) }
                    pending += pc + 1
                }
                i is OffsetInstruction -> { pending += target(i.codeOffset); pending += pc + 1 }
                i !is PayloadInstruction -> pending += pc + 1
            }
        }
        return false
    }
    val switch = code.indices.single { code[it].opcode == Opcode.PACKED_SWITCH }
    val payloadIndex = offsets.indexOf(offsets[switch] + (code[switch] as OffsetInstruction).codeOffset)
    val states = (code[payloadIndex] as SwitchPayload).switchElements
    assertEquals((0..8).toSet(), states.map { it.key }.toSet())
    states.forEach {
        val entry = offsets.indexOf(offsets[switch] + it.offset)
        assertEquals(it.key == 0, reachable(entry, start), "Only fresh invocation may select a route; state ${it.key}")
    }
    cls.methods.remove(helper)
    method.removeInstructions(start, 5)
    browserSelectionAnchor(method)
    val old = before.methods.single { it.name == "c" }
    val oldShape = normalized(old)
    val newShape = normalized(method)
    assertTrue(oldShape == newShape, "Restored operands/branches/catches differ at body index " +
        oldShape.first.indices.firstOrNull { oldShape.first.getOrNull(it) != newShape.first.getOrNull(it) })
    // The Morphe proxy changes canonical representation even for an untouched copy.
    // Validate every actual body first, then hash actual metadata with original verified bodies.
    val originals = before.methods.associateBy { it.toString() }
    assertEquals(originals.keys, cls.methods.map { it.toString() }.toSet())
    val verifiedMethods = cls.methods.map { actual ->
        val original = originals.getValue(actual.toString())
        assertEquals(original.implementation?.registerCount, actual.implementation?.registerCount)
        assertTrue(normalized(original) == normalized(actual), "Unexpected original body/catch change: ${actual.name}")
        ImmutableMethod(actual.definingClass, actual.name, actual.parameters, actual.returnType,
            actual.accessFlags, actual.annotations, actual.hiddenApiRestrictions, original.implementation)
    }
    val baseline = ImmutableClassDef(cls.type, cls.accessFlags, cls.superclass, cls.interfaces,
        cls.sourceFile, cls.annotations, cls.fields, verifiedMethods)
    assertEquals(browserFingerprints.getValue(BROWSER_OWNER), traceClassHash(baseline), "Original class/method metadata must be retained")
    return baseline
}
