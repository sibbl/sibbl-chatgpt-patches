// Copyright 2026 sibbl. GPL-3.0; see LICENSE and NOTICE.
package net.sibbl.chatgpt

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*

internal const val BROWSER_OWNER = "Lxb80;"
internal const val BROWSER_HELPER = "morphePreferInitialBrowser"
internal const val BROWSER_HELPER_REF = "$BROWSER_OWNER->$BROWSER_HELPER(Lec6;Lxgx;ZLyj40;Lfa6;)Z"
internal val browserFingerprints = mapOf(
    BROWSER_OWNER to "b33a15909c988406bd1ef43992cf6b97250059e404a6d2dddb46ff2e043abd52",
    "Lyj40;" to "deedbc01e01ddbe42dc44027d9952df6a875ff7e32d83bd20771daa3c6bbde92",
    "Lfa6;" to "429fc96b8dabebc19bd3c45a28de1616e901328bc267c7226c76ed1d68251b93",
    "Lub6;" to "a397aa50efff6886c894b5b2a7c47c1d64ee4e43bba783233735b0b0723baa90",
    "Lvb80;" to "81b74f595a3a7f91197832c47d19a971f49635a855b1e2d4e2171b2fa61523f8"
)

/** Pure route predicate. No auth request, state writes, logging, or success/error conversion. */
internal fun browserHelperSmali() = """
    .class public $BROWSER_OWNER
    .super Ljava/lang/Object;
    .method private static $BROWSER_HELPER(Lec6;Lxgx;ZLyj40;Lfa6;)Z
    .registers 6
    if-nez p1, :original
    if-nez p2, :original
    sget-object v0, Lub6;->e:Lub6;
    if-ne p0, v0, :original
    sget-object v0, Lfa6;->e:Lfa6;
    if-eq p4, v0, :initial_source
    sget-object v0, Lfa6;->b:Lfa6;
    if-ne p4, v0, :original
    :initial_source
    if-eqz p3, :original
    iget-object v0, p3, Lyj40;->c:Ljava/lang/String;
    if-nez v0, :original
    const/4 v0, 0x1
    return v0
    :original
    const/4 v0, 0x0
    return v0
    .end method
""".trimIndent()

/** v10 is dead on both original successors; p4 (v33) is the untouched source enum. */
internal fun browserSelectionSmali() = """
    move-object/from16 v10, v33
    invoke-static {v0, v1, v2, v7, v10}, $BROWSER_HELPER_REF
    move-result v10
    if-eqz v10, :original_selection
    const/4 v4, 0x0
""".trimIndent()

internal fun browserSelectionAnchor(method: Method): Int {
    require(method.definingClass == BROWSER_OWNER && method.name == "c" &&
        method.parameterTypes.map { it.toString() } == listOf("Lew4;", "Landroid/content/Context;", "Lec6;", "Lfa6;",
            "Lxgx;", "Ljava/lang/String;", "Lda6;", "Z", "Lb66;", "Ljava/lang/String;", "Ls7m;") &&
        method.returnType == "Ljava/lang/Object;") { "Unexpected top-level login contract." }
    val impl = requireNotNull(method.implementation)
    require(impl.registerCount == 41)
    val code = impl.instructions.toList()
    var address = 0
    val offsets = code.map { instruction -> address.also { address += instruction.codeUnits } }
    val provider = code.indices.single { (code[it] as? ReferenceInstruction)?.reference?.toString() == "Lub6;->e:Lub6;" }
    val anchor = (provider until code.size).first {
        code[it].opcode == Opcode.IF_EQZ && (code[it] as OffsetInstruction).codeOffset > 500
    }
    require((code[anchor] as OneRegisterInstruction).registerA == 4)
    val targetAddress = offsets[anchor] + (code[anchor] as OffsetInstruction).codeOffset
    val target = offsets.indexOf(targetAddress)
    require(target > anchor)
    // No original branch may jump past the new predicate at this fall-through-only anchor.
    require(code.indices.none {
        val branch = code[it] as? OffsetInstruction
        branch != null && offsets[it] + branch.codeOffset == offsets[anchor]
    })
    // On native path the existing getter immediately replaces v10; it does not consume it.
    require((code[anchor + 1] as ReferenceInstruction).reference.toString() == "Lew4;->l()Lpj40;")
    require((code[anchor + 1] as RegisterRangeInstruction).startRegister == 30)
    require(code[anchor + 2].opcode == Opcode.MOVE_RESULT_OBJECT &&
        (code[anchor + 2] as OneRegisterInstruction).registerA == 10)
    // Web setup replaces scratch v10 before any use; preserve every original setup instruction.
    val setup = code.subList(target, target + 5)
    require(setup.all { it.opcode in setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16) })
    require(setup.dropLast(1).none { (it as TwoRegisterInstruction).registerB == 10 })
    require((setup.last() as TwoRegisterInstruction).registerA == 10 &&
        (setup.last() as TwoRegisterInstruction).registerB == 33)
    return anchor
}

internal fun BytecodePatchContext.installInitialBrowserRoute() {
    browserFingerprints.forEach { (type, hash) ->
        require(traceClassHash(classDefBy(type)) == hash) { "Initial browser route fingerprint changed: $type." }
    }
    val cls = mutableClassDefBy(BROWSER_OWNER)
    require(cls.methods.none { it.name == BROWSER_HELPER })
    val method = cls.methods.single { it.name == "c" }
    val anchor = browserSelectionAnchor(method)
    val original = method.implementation!!.instructions[anchor]
    cls.methods.add(compileTraceMethod(browserHelperSmali()))
    method.addInstructionsWithLabels(anchor, browserSelectionSmali(), ExternalLabel("original_selection", original))
}
