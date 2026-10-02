package net.sibbl.chatgpt

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

/** Execute the actual generated DEX branches against synthetic objects; no APK code or network. */
class NativeResponseTraceTest {
    private data class Obj(val type: String, val fields: Map<String, Any?> = emptyMap())
    private fun run(kind: String, input: Any?): List<String> {
        val impl = compileTraceHelper("LFixture;", kind, kind).implementation!!
        val code = impl.instructions.toList()
        val addresses = mutableListOf<Int>(); var address = 0
        code.forEach { addresses += address; address += it.codeUnits }
        val regs = arrayOfNulls<Any?>(impl.registerCount)
        regs[regs.lastIndex] = input
        val result = mutableListOf<String>(); var returned: Any? = null; var pc = 0; var steps = 0
        fun field(obj: Any?, name: String) = (obj as Obj).fields[name]
        while (true) {
            check(++steps < 6000) { "Unbounded diagnostic execution" }
            val i = code[pc]
            val a = (i as? OneRegisterInstruction)?.registerA ?: 0
            val b = (i as? TwoRegisterInstruction)?.registerB ?: 0
            val ref = (i as? ReferenceInstruction)?.reference
            fun jump() { pc = addresses.indexOf(addresses[pc] + (i as OffsetInstruction).codeOffset) - 1 }
            fun zero(value: Any?) = value == null || value == 0 || value == false
            try {
                when (i.opcode) {
                    Opcode.CONST_STRING -> regs[a] = (ref as StringReference).string
                    Opcode.CONST_4, Opcode.CONST_16 -> regs[a] = (i as NarrowLiteralInstruction).narrowLiteral
                    Opcode.MOVE_OBJECT -> regs[a] = regs[b]
                    Opcode.INSTANCE_OF -> regs[a] = if ((regs[b] as? Obj)?.type == ref.toString()) 1 else 0
                    Opcode.CHECK_CAST -> check((regs[a] as? Obj)?.type == ref.toString() || regs[a] == null)
                    Opcode.IGET, Opcode.IGET_OBJECT -> regs[a] = field(regs[b], (ref as FieldReference).name)
                    Opcode.MOVE_RESULT, Opcode.MOVE_RESULT_OBJECT -> regs[a] = returned
                    Opcode.IF_EQZ -> if (zero(regs[a])) jump()
                    Opcode.IF_NEZ -> if (!zero(regs[a])) jump()
                    Opcode.IF_NE -> if (regs[a] != regs[b]) jump()
                    Opcode.IF_GE -> if ((regs[a] as Int) >= (regs[b] as Int)) jump()
                    Opcode.IF_LT -> if ((regs[a] as Int) < (regs[b] as Int)) jump()
                    Opcode.GOTO, Opcode.GOTO_16 -> jump()
                    Opcode.ADD_INT_LIT8 -> regs[a] = (regs[b] as Int) + (i as NarrowLiteralInstruction).narrowLiteral
                    Opcode.INVOKE_STATIC, Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_INTERFACE -> {
                        val call = i as FiveRegisterInstruction
                        val args = listOf(call.registerC, call.registerD, call.registerE).take(call.registerCount).map { regs[it] }
                        returned = when (ref.toString()) {
                            "Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I" -> {
                                assertEquals(TRACE_TAG, args[0]); assertTrue(args[1] in traceAllowedMessages)
                                result += args[1] as String; 0
                            }
                            "Ligy;->f()Lnhy;" -> field(args[0], "status")
                            "Ljava/util/List;->size()I" -> (args[0] as List<*>).size
                            "Ljava/util/List;->get(I)Ljava/lang/Object;" -> (args[0] as List<*>)[args[1] as Int]
                            "Ljava/lang/String;->equals(Ljava/lang/Object;)Z" -> if (args[0] == args[1]) 1 else 0
                            else -> error("Unexpected invocation")
                        }
                    }
                    Opcode.RETURN_VOID -> return result
                    Opcode.MOVE_EXCEPTION -> regs[a] = RuntimeException("synthetic")
                    else -> error("Unsupported fixture opcode ${i.opcode}")
                }
            } catch (e: Exception) {
                // Match the generated catch boundary, including deliberate synthetic list failure.
                val handler = impl.tryBlocks.single()
                check(addresses[pc] in handler.startCodeAddress until handler.startCodeAddress + handler.codeUnitCount)
                pc = addresses.indexOf(handler.exceptionHandlers.single().handlerCodeAddress) - 1
            }
            pc++
        }
    }
    private fun errors(codes: List<String?>) = Obj("Lue6;", mapOf("d" to codes.map { Obj("Lre6;", mapOf("a" to it)) }))
    private fun page(error: Boolean, nested: Any?, top: Any?) = if (error)
        Obj("Li6u;", mapOf("b" to Obj("Lh6u;", mapOf("d" to nested)), "c" to top)) else
        Obj("Ljm40;", mapOf("b" to Obj("Lim40;", mapOf("a" to nested)), "c" to top))
    private fun wrapped(kind: String, page: Any?) = if (kind == "NATIVE_BEFORE_MAP") Obj("Ld580;", mapOf("a" to page))
        else Obj("Lnnq0;", mapOf("b" to page))

    @Test fun `actual raw response status and suspension are distinguished`() {
        (nativeHttpStatuses + listOf(418, 0, 599)).forEach { status ->
            assertEquals(listOf("NATIVE_RAW_HTTP_${if (status in nativeHttpStatuses) status else "OTHER"}"),
                run("NATIVE_RAW_HTTP", Obj("Ligy;", mapOf("status" to Obj("Lnhy;", mapOf("a" to status))))))
        }
        assertTrue(run("NATIVE_RAW_HTTP", Obj("Lqeo;" )).isEmpty())
        assertTrue(run("NATIVE_RAW_HTTP", null).isEmpty())
    }

    @Test fun `every code bucket is literal and unknown sensitive strings stay private at both stages`() {
        nativePageStages.forEach { stage ->
            nativeCodeCategories.forEach { (code, category) ->
                val output = run(stage, wrapped(stage, page(true, errors(listOf(code)), null)))
                assertEquals(listOf("${stage}_PAGE_ERROR", "${stage}_NESTED_PRESENT", "${stage}_NESTED_CODE_$category", "${stage}_TOP_ABSENT"), output)
            }
            val output = run(stage, wrapped(stage, page(false, errors(listOf("private@example.test secret URL token", null)), errors(listOf("invalid_request")))))
            assertEquals(listOf("${stage}_PAGE_PASSWORD", "${stage}_NESTED_PRESENT", "${stage}_NESTED_CODE_OTHER", "${stage}_NESTED_CODE_NONE", "${stage}_TOP_PRESENT", "${stage}_TOP_CODE_REQUEST"), output)
        }
    }

    @Test fun `missing metadata large lists and helper exceptions are bounded`() {
        nativePageStages.forEach { stage ->
            val empty = run(stage, wrapped(stage, page(true, errors(emptyList()), Obj("Lue6;"))))
            assertTrue("${stage}_NESTED_METADATA_EMPTY" in empty)
            assertTrue("${stage}_TOP_METADATA_EMPTY" in empty)
            val bounded = run(stage, wrapped(stage, page(true, errors(List(100) { "unknown" }), null)))
            assertEquals(16, bounded.count { it == "${stage}_NESTED_CODE_OTHER" })
            assertTrue("${stage}_NESTED_METADATA_TRUNCATED" in bounded)
            val broken = object: AbstractList<Any?>() {
                override val size = 1
                override fun get(index: Int): Any? = throw IllegalStateException("secret must never be logged")
            }
            assertEquals(listOf("${stage}_PAGE_ERROR", "${stage}_NESTED_PRESENT"), run(stage, wrapped(stage, page(true, Obj("Lue6;", mapOf("d" to broken)), null))))
            assertTrue(run(stage, Obj("Lqeo;")).isEmpty())
            assertEquals(listOf("${stage}_PAGE_OTHER"), run(stage, wrapped(stage, Obj("LOther;"))))
        }
    }

    @Test fun `published collector allowlist exactly matches compiled diagnostic vocabulary`() {
        val file = sequenceOf(File("scripts/trace-allowlist.txt"), File("../scripts/trace-allowlist.txt")).first { it.exists() }
        assertEquals(traceAllowedMessages.toSet(), file.readLines().filter { it.isNotBlank() }.toSet())
    }
}
