package net.sibbl.chatgpt

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.junit.jupiter.api.Assertions.*

/** Structural APK evidence only; never invokes app methods or opens a browser. */
internal fun assertExistingBrowserContracts(classes: Map<String, ClassDef>) {
    fun body(type: String, name: String) = classes.getValue(type).methods.single {
        it.name == name
    }.implementation!!.instructions.toList()
    fun literals(type: String) = body(type, "<clinit>").mapNotNull {
        ((it as? ReferenceInstruction)?.reference as? StringReference)?.string
    }
    // The generic flow and Google provider are distinct existing configurations.
    assertEquals(listOf("", "login_or_signup"), literals("Lub6;"))
    assertEquals(listOf("google-oauth2"), literals("Lvb6;"))
    val browser = body("Lqd80;", "l")
    val refs = browser.mapNotNull { (it as? ReferenceInstruction)?.reference?.toString() }
    assertTrue("Lrj40;->a(Ls7m;)Ljava/lang/Object;" in refs)
    assertTrue("Lehu0;->h(Landroid/content/Context;Lec6;Ljava/lang/String;ZLjava/lang/String;Lfhu0;Lxp1;Ls7m;)Ljava/lang/Object;" in refs)
    assertTrue("Lbb80;->b(Lu96;Ljava/lang/String;)V" in refs)
    // Existing browser completion wraps Unit, rather than a native cf6 page.
    val unitIndices = browser.indices.filter {
        (browser[it] as? ReferenceInstruction)?.reference?.toString() == "Lt4r0;->a:Lt4r0;"
    }
    assertEquals(2, unitIndices.size)
    unitIndices.forEach { index ->
        val register = (browser[index] as OneRegisterInstruction).registerA
        val constructor = browser[index + 1]
        val reference = (constructor as ReferenceInstruction).reference as MethodReference
        assertEquals("Lnnq0;-><init>(Ljava/lang/Object;)V", reference.toString())
        assertEquals(register, (constructor as FiveRegisterInstruction).registerD)
        assertEquals(Opcode.RETURN_OBJECT, browser[index + 2].opcode)
    }
    val nativePassword = body("Lrh80;", "invokeSuspend")
    val nativeCall = nativePassword.indexOfFirst {
        (it as? ReferenceInstruction)?.reference?.toString() ==
            "Ljd80;->i(Ljava/lang/String;Llco0;)Ljava/lang/Object;"
    }
    assertTrue(nativeCall >= 0)
    assertTrue(nativePassword.drop(nativeCall).any {
        it.opcode == Opcode.CHECK_CAST &&
            (it as? ReferenceInstruction)?.reference?.toString() == "Lcf6;"
    }, "Native password continuation expects a cf6 page, not browser Unit")
    val emailEntry = body("Lcom/openai/feature/onboarding/impl/next/viewmodel/b;", "M")
    assertTrue(emailEntry.any {
        (it as? ReferenceInstruction)?.reference?.toString() == "Ltg90;->t:Lgiu;"
    }, "Retain the existing authorize-challenge fallback gate")
}
