package net.sibbl.chatgpt

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Execute only our generated predicate/injection against synthetic values, never app code. */
class InitialBrowserRouteTest {
    data class Scope(val binding: Any?)
    private val helper = compileTraceMethod(browserHelperSmali())
    private val selection = compileTraceMethod("""
        .class public LRouteFixture;
        .super Ljava/lang/Object;
        .method public static select()I
        .registers 41
        ${browserSelectionSmali()}
        :original_selection
        return v4
        .end method
    """.trimIndent())

    private fun evaluate(code: List<Instruction>, regs: Array<Any?>, predicate: Method = helper): Int {
        var offset = 0
        val addresses = code.map { instruction -> offset.also { offset += instruction.codeUnits } }
        var pc = 0; var steps = 0; var result = 0
        fun zero(v: Any?) = v == null || v == 0
        while (true) {
            check(++steps < 100)
            val i = code[pc]
            val a = (i as? OneRegisterInstruction)?.registerA ?: 0
            val b = (i as? TwoRegisterInstruction)?.registerB ?: 0
            val ref = (i as? ReferenceInstruction)?.reference
            fun jump() { pc = addresses.indexOf(addresses[pc] + (i as OffsetInstruction).codeOffset) - 1 }
            when (i.opcode) {
                Opcode.IF_EQZ -> if (zero(regs[a])) jump()
                Opcode.IF_NEZ -> if (!zero(regs[a])) jump()
                Opcode.IF_EQ -> if (regs[a] == regs[b]) jump()
                Opcode.IF_NE -> if (regs[a] != regs[b]) jump()
                Opcode.CONST_4 -> regs[a] = (i as NarrowLiteralInstruction).narrowLiteral
                Opcode.SGET_OBJECT -> regs[a] = ref.toString()
                Opcode.IGET_OBJECT -> {
                    assertEquals("Lyj40;->c:Ljava/lang/String;", (ref as FieldReference).toString())
                    regs[a] = (regs[b] as Scope).binding
                }
                Opcode.MOVE_OBJECT_FROM16 -> regs[a] = regs[b]
                Opcode.INVOKE_STATIC -> {
                    assertEquals(BROWSER_HELPER_REF, ref.toString())
                    val call = i as FiveRegisterInstruction
                    val arguments = listOf(call.registerC, call.registerD, call.registerE, call.registerF, call.registerG)
                    val local = arrayOfNulls<Any?>(predicate.implementation!!.registerCount)
                    arguments.forEachIndexed { n, r -> local[n + 1] = regs[r] }
                    result = evaluate(predicate.implementation!!.instructions.toList(), local, predicate)
                }
                Opcode.MOVE_RESULT -> regs[a] = result
                Opcode.RETURN -> return regs[a] as Int
                else -> error("Predicate must contain no side effects or other calls: ${i.opcode}")
            }
            pc++
        }
    }

    internal fun matrix(code: List<Instruction> = selection.implementation!!.instructions.toList(), predicate: Method = helper): Int {
        var rows = 0
        val providers = listOf("Lub6;->e:Lub6;", "Lvb6;->e:Lvb6;", "Lwb6;->e:Lwb6;", "Lcc6;->e:Lcc6;", null)
        val sources = listOf("Lfa6;->e:Lfa6;", "Lfa6;->b:Lfa6;", "Lfa6;->c:Lfa6;", "Lfa6;->d:Lfa6;",
            "Lfa6;->f:Lfa6;", "Lfa6;->g:Lfa6;", "Lfa6;->h:Lfa6;", "Lfa6;->i:Lfa6;", "Lfa6;->j:Lfa6;", "unknown source", null)
        val matchedSources = mutableMapOf<Any?, Int>()
        for (eligible in listOf(0, 1)) for (provider in providers) for (source in sources)
            for (silent in listOf(0, 1)) for (credential in listOf(null, Any()))
                for (scope in listOf(null, Scope(null), Scope(Any()))) {
                    val regs = arrayOfNulls<Any?>(41)
                    regs[0] = provider; regs[1] = credential; regs[2] = silent
                    regs[4] = eligible; regs[7] = scope; regs[33] = source
                    val snapshot = regs.clone()
                    val actual = evaluate(code, regs, predicate)
                    val selected = provider == providers[0] && source in listOf(sources[0], sources[1]) && silent == 0 &&
                        credential == null && scope != null && scope.binding == null
                    assertEquals(if (selected) 0 else eligible, actual, "Route row $rows source $source")
                    if (selected) matchedSources[source] = matchedSources.getOrDefault(source, 0) + 1
                    listOf(0, 1, 2, 7, 33).forEach { assertEquals(snapshot[it], regs[it], "Original argument changed") }
                    rows++
                }
        assertEquals(mapOf(sources[0] to 2, sources[1] to 2), matchedSources, "Both initial sources must positively select browser with all guards satisfied")
        return rows
    }

    @Test fun `compiled branch matrix changes only generic interactive fresh welcome and modal routes`() {
        assertEquals(1320, matrix())
    }

    @Test fun `browser preference is default off`() {
        assertEquals(false, loginCallbackPatch.options["preferInitialBrowser"].default)
    }

    @Test fun `changed top-level signature fails closed`() {
        val invalid = compileTraceMethod("""
            .class public Lxb80;
            .super Ljava/lang/Object;
            .method public c()Ljava/lang/Object;
            .registers 1
            return-object v0
            .end method
        """.trimIndent())
        assertThrows(IllegalArgumentException::class.java) { browserSelectionAnchor(invalid) }
    }
}
