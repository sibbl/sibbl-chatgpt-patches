package net.sibbl.chatgpt

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AuthTraceTest {
    @Test fun `tracing is an explicit default-off option`() {
        assertEquals(false, loginCallbackPatch.options["authTrace"].value)
    }

    @Test fun `every compiled logger accepts only constant allowlisted output and catches its failures`() {
        val kinds = traceFixedEvents + traceStages + nativeTraceKinds + routeTraceKinds + "PAGE"
        kinds.forEach { kind ->
            val method = compileTraceHelper("LFixture;", kind, kind)
            val impl = method.implementation!!
            val instructions = impl.instructions.toList()
            val permittedFields = setOf(
                "Lhnq0;->b:Ljava/lang/Throwable;", "Ly480;->a:Ljava/lang/Throwable;",
                "Lmnq0;->c:Ljava/lang/Integer;", "Lc580;->b:I", "Lnhy;->a:I", "Ld580;->a:Ljava/lang/Object;", "Lnnq0;->b:Ljava/lang/Object;",
                "Li6u;->c:Lue6;", "Li6u;->b:Lh6u;", "Lh6u;->d:Lue6;",
                "Ljm40;->c:Lue6;", "Ljm40;->b:Lim40;", "Lim40;->a:Lue6;",
                "Lue6;->d:Ljava/util/List;", "Lre6;->a:Ljava/lang/String;",
                "Lyj40;->c:Ljava/lang/String;", "Lub6;->e:Lub6;",
                "Lfa6;->e:Lfa6;", "Lfa6;->c:Lfa6;", "Lfa6;->d:Lfa6;",
                "Lfa6;->b:Lfa6;", "Lfa6;->f:Lfa6;", "Lfa6;->g:Lfa6;",
                "Lfa6;->h:Lfa6;", "Lfa6;->i:Lfa6;", "Lfa6;->j:Lfa6;"
            )
            val permittedCalls = setOf(
                "Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I",
                "Ljava/lang/Integer;->intValue()I", "Ligy;->f()Lnhy;", "Ljava/util/List;->size()I",
                "Ljava/util/List;->get(I)Ljava/lang/Object;", "Ljava/lang/String;->equals(Ljava/lang/Object;)Z"
            )
            val addresses = mutableListOf<Int>()
            var address = 0
            instructions.forEach { addresses += address; address += it.codeUnits }
            instructions.forEachIndexed { index, instruction ->
                if (instruction is OffsetInstruction) {
                    val destination = addresses.indexOf(addresses[index] + instruction.codeOffset)
                    assertTrue(destination >= 0)
                    val target = (instructions[destination] as? ReferenceInstruction)?.reference as? MethodReference
                    assertFalse(target?.definingClass == "Landroid/util/Log;", "A branch must not skip the literal assignment")
                }
                val reference = (instruction as? ReferenceInstruction)?.reference
                when (reference) {
                    is StringReference -> assertTrue(reference.string == TRACE_TAG || reference.string in traceAllowedMessages || reference.string in nativeCodeCategories)
                    is FieldReference -> assertTrue(reference.toString() in permittedFields)
                    is MethodReference -> {
                        assertTrue(reference.toString() in permittedCalls)
                        if (reference.definingClass == "Landroid/util/Log;") {
                            val call = instruction as FiveRegisterInstruction
                            assertEquals(listOf(0, 1), listOf(call.registerC, call.registerD))
                            assertEquals(2, call.registerCount)
                            val previous = instructions[index - 1]
                            assertEquals(Opcode.CONST_STRING, previous.opcode)
                            assertEquals(1, (previous as OneRegisterInstruction).registerA)
                            val tag = instructions.first()
                            assertEquals(Opcode.CONST_STRING, tag.opcode)
                            assertEquals(0, (tag as OneRegisterInstruction).registerA)
                            assertEquals(TRACE_TAG, ((tag as ReferenceInstruction).reference as StringReference).string)
                        }
                    }
                }
                assertFalse(instruction.opcode.name.startsWith("IPUT") || instruction.opcode.name.startsWith("SPUT"))
                if (instruction.opcode.setsRegister() && instruction is OneRegisterInstruction && instruction.registerA < 2)
                    assertEquals(Opcode.CONST_STRING, instruction.opcode)
            }
            assertEquals(1, impl.tryBlocks.size)
            assertEquals("Ljava/lang/Throwable;", impl.tryBlocks.single().exceptionHandlers.single().exceptionType)
            assertEquals(Opcode.MOVE_EXCEPTION, instructions[instructions.size - 2].opcode)
            assertEquals(Opcode.RETURN_VOID, instructions.last().opcode)
            assertTrue(instructions.none { it.opcode == Opcode.THROW })
        }
    }

    @Test fun `unknown output categories are rejected before compilation`() {
        assertThrows(IllegalStateException::class.java) {
            compileTraceHelper("LFixture;", "bad", "arbitrary server text")
        }
    }
}
