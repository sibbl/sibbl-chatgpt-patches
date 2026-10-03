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
    private fun run(kind: String, input: Any?, arguments: List<Any?>? = null): List<String> {
        val impl = compileTraceHelper("LFixture;", kind, kind).implementation!!
        val code = impl.instructions.toList()
        val addresses = mutableListOf<Int>(); var address = 0
        code.forEach { addresses += address; address += it.codeUnits }
        val regs = arrayOfNulls<Any?>(impl.registerCount)
        if (arguments == null) regs[regs.lastIndex] = input
        else arguments.forEachIndexed { index, value -> regs[regs.size - arguments.size + index] = value }
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
                    Opcode.SGET_OBJECT -> regs[a] = ref.toString()
                    Opcode.IGET, Opcode.IGET_OBJECT -> regs[a] = field(regs[b], (ref as FieldReference).name)
                    Opcode.MOVE_RESULT, Opcode.MOVE_RESULT_OBJECT -> regs[a] = returned
                    Opcode.IF_EQZ -> if (zero(regs[a])) jump()
                    Opcode.IF_NEZ -> if (!zero(regs[a])) jump()
                    Opcode.IF_EQ -> if (regs[a] == regs[b]) jump()
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
                    Opcode.RETURN_VOID -> {
                        arguments?.forEachIndexed { index, value ->
                            assertEquals(value, regs[regs.size - arguments.size + index], "Diagnostic changed an argument")
                        }
                        return result
                    }
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
                assertEquals(listOf("${stage}_PAGE_ERROR", "${stage}_NESTED_PRESENT", "${stage}_NESTED_CODE_$category") +
                    listOfNotNull(nativeCredentialVariants[code]?.let { "${stage}_NESTED_CREDENTIAL_VARIANT_$it" }) +
                    "${stage}_TOP_ABSENT", output)
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

    @Test fun `fresh and resumed response branches both reach the diagnostic helper`() {
        val method = compileTraceMethod("""
            .class public LFixture;
            .super Ljava/lang/Object;
            .method public static response(Ljava/lang/Object;Z)Ljava/lang/Object;
            .registers 3
            if-eqz p1, :fresh
            goto :response
            :fresh
            nop
            :response
            return-object p0
            .end method
        """.trimIndent())
        val original = method.implementation!!.instructions.toList()
        val returnIndex = original.indexOfFirst { it.opcode == Opcode.RETURN_OBJECT }
        insertTraceAtReturn(method, returnIndex, "LFixture;->morpheTrace_Native(Ljava/lang/Object;)V")
        val instructions = method.implementation!!.instructions.toList()
        val addresses = mutableListOf<Int>(); var address = 0
        instructions.forEach { addresses += address; address += it.codeUnits }
        val branch = instructions.indexOfFirst { it.opcode == Opcode.GOTO }
        val target = addresses.indexOf(addresses[branch] + (instructions[branch] as OffsetInstruction).codeOffset)
        assertEquals(returnIndex, target)
        assertEquals(Opcode.INVOKE_STATIC_RANGE, instructions[target].opcode)
        assertEquals(Opcode.NOP, instructions[target - 1].opcode)
        assertEquals(Opcode.RETURN_OBJECT, instructions[target + 1].opcode)
        assertEquals((original[returnIndex] as OneRegisterInstruction).registerA,
            (instructions[target + 1] as OneRegisterInstruction).registerA)
        assertEquals(3, method.implementation!!.registerCount)
    }


    @Test fun `MFA response and rendered page are distinct without reading challenge fields`() {
        nativePageStages.forEach { stage ->
            assertEquals(listOf("${stage}_PAGE_MFA"), run(stage, wrapped(stage, Obj("Luw60;"))))
        }
        assertEquals(listOf("PAGE_MFA"), run("PAGE", Obj("Luw60;")))
        assertEquals(listOf("PAGE_OTHER"), run("PAGE", Obj("LOther;")))
    }

    @Test fun `route guards use enums only and preserve every argument across both preferences`() {
        for (kind in routeTraceKinds) for (provider in listOf("Lub6;->e:Lub6;", "Lvb6;->e:Lvb6;", null))
            for (source in routeTraceSources.keys.map { "Lfa6;->$it:Lfa6;" } + null)
                for (credential in listOf(null, Obj("Lxgx;"))) for (silent in listOf(0, 1))
                    for (scope in listOf(null, Obj("Lyj40;", mapOf("c" to null)), Obj("Lyj40;", mapOf("c" to "private binding")))) {
                        val arguments = listOf(provider, credential, silent, scope, source)
                        val snapshot = arguments.toList()
                        val output = run(kind, null, arguments)
                        val preference = if (kind.endsWith("_ON")) "ON" else "OFF"
                        val expected = when {
                            credential != null -> "REJECT_CREDENTIAL"
                            silent != 0 -> "REJECT_SILENT"
                            provider != "Lub6;->e:Lub6;" -> "REJECT_PROVIDER"
                            source !in listOf("Lfa6;->e:Lfa6;", "Lfa6;->b:Lfa6;") -> "REJECT_SOURCE"
                            scope == null -> "REJECT_SCOPE_ABSENT"
                            scope.fields["c"] != null -> "REJECT_REAUTH"
                            else -> "MATCH_PREFERENCE_$preference"
                        }
                        assertEquals("ROUTE_$expected", output.last())
                        assertEquals("ROUTE_ENTRY", output.first())
                        assertEquals("ROUTE_PREFERENCE_$preference", output[1])
                        val sourceCategory = routeTraceSources.entries.firstOrNull { source == "Lfa6;->${it.key}:Lfa6;" }?.value ?: "NONE"
                        assertEquals("ROUTE_SOURCE_$sourceCategory", output[2])
                        assertEquals(4, output.size)
                        assertEquals(snapshot, arguments)
                        assertTrue(output.all { it in routeTraceMessages })
                    }
    }

    @Test fun `unrecognized source objects remain private and do not match the browser preference`() {
        for (kind in routeTraceKinds) {
            val unknown = Obj("Lfa6;", mapOf("private" to "private@example.test token URL"))
            val output = run(kind, null, listOf("Lub6;->e:Lub6;", null, 0, Obj("Lyj40;", mapOf("c" to null)), unknown))
            assertEquals("ROUTE_SOURCE_OTHER", output[2])
            assertEquals("ROUTE_REJECT_SOURCE", output.last())
            assertTrue(output.all { it in routeTraceMessages })
        }
    }

    @Test fun `credential variants require exact allowlisted matches at both error locations`() {
        for (stage in nativePageStages) {
            for ((code, variant) in nativeCredentialVariants) {
                val output = run(stage, wrapped(stage, page(false, errors(listOf(code)), errors(listOf(code)))))
                assertTrue("${stage}_NESTED_CREDENTIAL_VARIANT_$variant" in output)
                assertTrue("${stage}_TOP_CREDENTIAL_VARIANT_$variant" in output)
            }
            val negative = nativeCredentialVariants.keys.flatMap { listOf(it.uppercase(), "$it secret", " $it", "$it\n") } +
                listOf("private@example.test", "https://private.test/token", "arbitrary server prose")
            negative.forEach { unknown ->
                val output = run(stage, wrapped(stage, page(false, errors(listOf(unknown)), errors(listOf(unknown)))))
                assertFalse(output.any { "CREDENTIAL_VARIANT" in it || it.endsWith("CODE_CREDENTIALS") })
                assertTrue(output.all { it in nativeTraceMessages })
            }
        }
    }

    @Test fun `published collector allowlist exactly matches compiled diagnostic vocabulary`() {
        val file = sequenceOf(File("scripts/trace-allowlist.txt"), File("../scripts/trace-allowlist.txt")).first { it.exists() }
        assertEquals(traceAllowedMessages.toSet(), file.readLines().filter { it.isNotBlank() }.toSet())
    }
}
