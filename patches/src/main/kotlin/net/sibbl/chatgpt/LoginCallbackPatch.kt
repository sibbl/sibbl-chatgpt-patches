// Copyright 2026 sibbl. GPL-3.0; see LICENSE and NOTICE.
package net.sibbl.chatgpt

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.booleanOption
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal fun callbackReads(method: Method): List<Int> {
    require(method.definingClass == "Li280;" && method.name == "invoke" &&
        method.parameterTypes.isEmpty() && method.returnType == "Ljava/lang/Object;") {
        "Unexpected OAuth configuration method."
    }
    val instructions = requireNotNull(method.implementation).instructions.toList()
    fun literal(index: Int) = ((instructions[index] as? ReferenceInstruction)?.reference as? StringReference)?.string
    val domain = instructions.indices.single { literal(it) == "://auth.openai.com/android/" }
    val suffix = instructions.indices.single { literal(it) == "/callback" }
    val reads = instructions.indices.filter { index ->
        val ref = (instructions[index] as? ReferenceInstruction)?.reference as? MethodReference
        ref != null && ref.name == "getPackageName" && ref.parameterTypes.isEmpty() &&
            ref.returnType == "Ljava/lang/String;" &&
            ref.definingClass in setOf("Landroid/app/Application;", "Landroid/content/Context;") &&
            instructions[index].opcode in setOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE)
    }
    require(reads.size == 3 && reads[0] < reads[1] && reads[1] < domain && domain < suffix && suffix < reads[2]) {
        "OAuth configuration fingerprint changed; refusing to rewrite unrelated package reads."
    }
    require(reads.all { instructions.getOrNull(it + 1)?.opcode == Opcode.MOVE_RESULT_OBJECT })
    return reads.take(2)
}

@Suppress("unused")
val loginCallbackPatch = bytecodePatch(
    name = "Preserve ChatGPT login callback (experimental)",
    description = "Keeps the original OAuth redirect URI when cloning. Includes the clone baseline. " +
        "Login and server acceptance remain unverified. Optional default-off auth diagnostics log fixed categories only.",
    default = false
) {
    compatibleWith(Compatibility(
        name = "ChatGPT", packageName = ORIGINAL_PACKAGE, apkFileType = ApkFileType.APKM,
        targets = listOf(AppTarget(version = CANDIDATE_VERSION, versionCode = CANDIDATE_CODE.toInt(),
            isExperimental = true, minSdk = 32, description = CANDIDATE_DESCRIPTION))
    ))
    dependsOn(cloneChatGptPatch)
    val authTrace = booleanOption(
        key = "authTrace", default = false, title = "Diagnostic auth tracing (no secrets)",
        description = "Opt in to fixed phase/error categories under SibblAuthTrace. No credentials, server text, URLs or uploads. Diagnostic only; does not fix login."
    )
    execute {
        validateOriginalInput(packageMetadata.packageName, packageMetadata.versionName, packageMetadata.versionCode)
        if (authTrace.value == true) installAuthTrace()
        val method = mutableClassDefBy("Li280;").methods.single {
            it.name == "invoke" && it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/Object;"
        }
        val original = requireNotNull(method.implementation).instructions.toList()
        val reads = callbackReads(method)
        // The third package read identifies the actual app elsewhere and must stay dynamic.
        reads.asReversed().forEach { index ->
            val register = (original[index + 1] as OneRegisterInstruction).registerA
            method.replaceInstruction(index + 1, "const-string v$register, \"$ORIGINAL_PACKAGE\"")
        }
    }
}
