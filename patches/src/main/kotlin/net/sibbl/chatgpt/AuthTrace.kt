// Copyright 2026 sibbl. GPL-3.0; see LICENSE and NOTICE.
package net.sibbl.chatgpt

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import com.android.tools.smali.smali.smaliFlexLexer
import com.android.tools.smali.smali.smaliParser
import com.android.tools.smali.smali.smaliTreeWalker
import org.antlr.runtime.CommonTokenStream
import org.antlr.runtime.tree.CommonTreeNodeStream
import java.io.StringReader
import java.security.MessageDigest

internal const val TRACE_TAG = "SibblAuthTrace"
internal const val TRACE_PREFIX = "morpheTrace_"
internal const val NATIVE_REPOSITORY = "Lcom/openai/feature/onboarding/impl/next/repository/a;"

/** Hashes of canonical classes, not APK code. Fail closed on any changed injection target. */
internal val traceFingerprints = mapOf(
    "Luw60;" to "4bd34a83f19af7885e4200950ac85fe0a06cd64af94f32de9353eb3a5c4870a2",
    "Ldf6;" to "936136b7503b10773a61fc665c41ddab057e20931f50ee991af189c8d3141a17",
    "Ld580;" to "115dd7fabc5a4a4b02638c0cb8081a9ea110147db9e257b37bae89dbead29605",
    "Lnnq0;" to "121a49b0fafeb9034c0d3fd1759e318d1f47210c5d2e2bcd239a56c0844d2c02",
    "Ligy;" to "455715af9527a0793ad0de9f987fd432173ef2263d4f373026ace586a5ec29bd",
    "Lnhy;" to "48cb05e123caec84183ec6b8816913aa336216abde380ee01ee99c4317f2b303",
    "Lfy0;" to "a43bf4de476219092ad1f3f830005fec0faa6e1e086944d81e7ab5b55e59c3aa",
    "Lh6u;" to "1c39071b792457c940e6722d688aec843be365ba2ded3e750bdc96dc779e438a",
    "Li6u;" to "84c1a98902ffbd124fbcc6cd315a6639310ccac570fd6c41ea072484d4f9fe41",
    "Lim40;" to "70dd4fa78419b1a023052221891b570a0a7e94159ca51858a41b4c25fdae2181",
    "Ljm40;" to "9f56219ff77aad2df5e25f9fdb96bcc897fb793b154bf3e618077cb30ba630b2",
    "Lre6;" to "8824e363194527bb6c4f19ffe2dc59a82b5c0ef3a37e5d863681b29042f60909",
    "Lue6;" to "ee141d95cb43b9f9470ebc07bcbebf3553eda6b4738f00d3a318b89fe1551c53",
    "Lu56;" to "46e8801366c465e6cefcbe4d65ca239867d8a28c94bed6fd603a93f46bc6d966",
    "Lofu0;" to "abb195a35f9e9ed705ebaee715b057aa416303c3d122889e413369bf60978dd7",
    "Ly480;" to "8f9abd146862e5322a0737de61185dbfd2c22539e3292c376e4f13ab82d710ab",
    "Lc580;" to "2669d8d642a6c0e2e269a5d2789e2abd808b3d321c4e97a3ec0fc863ecdcfbf2",
    "Lhnq0;" to "5f934c2991123a21eb2d409597d9aaafd7baf157f04a359d41e91d555e11f6ef",
    "Lmnq0;" to "5355c6745be8749229ed59211016ded7f4ddbda39276bad178b13242fcf091da",
    "Lcom/openai/auth/a;" to "3b5537300ee6e2a63301efe41e6d6fc28be45f6a232c512e1677c5cfe9045933",
    NATIVE_REPOSITORY to "d0752efd5b4d1051cf14f7365ce9e819621ddfec8a2a6376fb1748b25116f355",
    "Ljd80;" to "f07fb75e54b111bc1b21e95a1c9231bdc06ebb7c091cf88f814596ab725a30e7",
    "Lqd80;" to "62ad3f0e909f6af163e98e01cae1a039b74cb2b1b351e0a4eb9a601b71a12f81"
)
internal fun traceClassHash(value: ClassDef): String {
    val store = MemoryDataStore()
    try {
        DexPool.writeTo(store, ImmutableDexFile(Opcodes.forApi(35), setOf(value)))
        return MessageDigest.getInstance("SHA-256").digest(store.data).joinToString("") { "%02x".format(it) }
    } finally { store.close() }
}

internal val traceErrorTypes = linkedMapOf(
    "Lcom/openai/auth/AuthError\$WebAuthFailed;" to "WEB_AUTH_FAILED",
    "Lcom/openai/auth/AuthError\$PlayIntegrityCheckFailed;" to "INTEGRITY_FAILED",
    "Lcom/openai/auth/AuthError\$SigningKeyUnavailable;" to "SIGNING_KEY_UNAVAILABLE",
    "Lcom/openai/auth/AuthError\$MissingResponse;" to "MISSING_RESPONSE",
    "Lcom/openai/auth/AuthError\$BrowserUnavailable;" to "BROWSER_UNAVAILABLE",
    "Lcom/openai/auth/AuthError\$Cancelled;" to "CANCELLED",
    "Lcom/openai/auth/AuthError\$NoCredentialsAvailable;" to "NO_CREDENTIALS",
    "Lcom/openai/feature/onboarding/impl/next/repository/AuthorizeChallengeException;" to "AUTHORIZE_CHALLENGE",
    "Ljava/io/IOException;" to "IO_ERROR"
)
internal val traceStatusCodes = listOf(400, 401, 403, 404, 408, 409, 422, 429, 500, 502, 503, 504)
internal val traceStages = setOf("NATIVE_BEGIN", "NATIVE_STEP", "BROWSER_RESULT", "TOKEN_HTTP", "AUTH_UI", "NATIVE_TRANSPORT")
internal val traceFixedEvents = setOf(
    "NATIVE_HTTP_DISPATCH", "NATIVE_PASSWORD_SUBMIT", "NATIVE_BEGIN_ENTER", "NATIVE_STEP_ENTER", "BROWSER_DISPATCH_ENTER",
    "CALLBACK_RECEIVED", "CALLBACK_URI_MISMATCH", "CALLBACK_STATE_MISMATCH", "CALLBACK_REMOTE_ERROR",
    "CALLBACK_CODE_PRESENT", "TOKEN_EXCHANGE_ENTER", "PAGE_PASSWORD", "PAGE_ERROR", "PAGE_MFA", "PAGE_OTHER"
)
internal val traceAllowedMessages = routeTraceMessages + nativeTraceMessages + traceFixedEvents + traceStages.flatMap { stage ->
    (listOf("OK", "FAILURE", "HTTP_OTHER") + traceErrorTypes.values + traceStatusCodes.map { "HTTP_$it" })
        .map { "${stage}_$it" }
}

internal fun logConstant(message: String): String {
    require(message in traceAllowedMessages)
    return "const-string v1, \"$message\"\ninvoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I"
}

/** Every logging argument is a literal. No text or identifier from the app reaches Log. */
internal fun traceHelperSmali(owner: String, name: String, kind: String): String {
    val objectArgument = kind == "PAGE" || kind in traceStages || kind in nativeTraceKinds
    val body = when {
        kind in routeTraceKinds -> routeTraceBody(kind)
        kind in nativeTraceKinds -> nativeTraceBody(kind)
        kind == "PAGE" -> """
            instance-of v2, p0, Ljm40;
            if-eqz v2, :page_error
            ${logConstant("PAGE_PASSWORD")}
            goto :done
            :page_error
            instance-of v2, p0, Li6u;
            if-eqz v2, :page_other
            ${logConstant("PAGE_ERROR")}
            goto :done
            :page_other
            instance-of v2, p0, Luw60;
            if-eqz v2, :page_unknown
            ${logConstant("PAGE_MFA")}
            goto :done
            :page_unknown
            ${logConstant("PAGE_OTHER")}
        """
        kind in traceFixedEvents -> logConstant(kind)
        kind in traceStages -> buildString {
            append("""
                instance-of v2, p0, Lnnq0;
                if-nez v2, :success
                instance-of v2, p0, Ld580;
                if-eqz v2, :failure
                :success
                ${logConstant("${kind}_OK")}
                goto :done
                :failure
                instance-of v2, p0, Lhnq0;
                if-eqz v2, :other_wrapper
                move-object v3, p0
                check-cast v3, Lhnq0;
                iget-object v3, v3, Lhnq0;->b:Ljava/lang/Throwable;
                goto :classify
                :other_wrapper
                instance-of v2, p0, Ly480;
                if-eqz v2, :done
                move-object v3, p0
                check-cast v3, Ly480;
                iget-object v3, v3, Ly480;->a:Ljava/lang/Throwable;
                :classify
            """)
            traceErrorTypes.entries.forEachIndexed { i, (type, category) ->
                append("instance-of v2, v3, $type\nif-eqz v2, :error_next_$i\n")
                append(logConstant("${kind}_$category"))
                append("\ngoto :status\n:error_next_$i\n")
            }
            append(logConstant("${kind}_FAILURE"))
            append("""

                :status
                instance-of v2, p0, Lmnq0;
                if-eqz v2, :status_alternate
                move-object v3, p0
                check-cast v3, Lmnq0;
                iget-object v3, v3, Lmnq0;->c:Ljava/lang/Integer;
                if-eqz v3, :done
                invoke-virtual {v3}, Ljava/lang/Integer;->intValue()I
                move-result v3
                goto :status_code
                :status_alternate
                instance-of v2, p0, Lc580;
                if-eqz v2, :done
                move-object v3, p0
                check-cast v3, Lc580;
                iget v3, v3, Lc580;->b:I
                :status_code
            """)
            traceStatusCodes.forEach { status ->
                append("const/16 v2, $status\nif-ne v2, v3, :status_next_$status\n")
                append(logConstant("${kind}_HTTP_$status"))
                append("\ngoto :done\n:status_next_$status\n")
            }
            append(logConstant("${kind}_HTTP_OTHER"))
        }
        else -> error("Unknown trace category")
    }
    return """
        .class public $owner
        .super Ljava/lang/Object;
        .method public static $TRACE_PREFIX$name(${if (kind in routeTraceKinds) ROUTE_TRACE_PARAMETERS else if (objectArgument) "Ljava/lang/Object;" else ""})V
        .registers ${if (kind in nativeTraceKinds || kind in routeTraceKinds) 10 else if (objectArgument) 5 else 4}
        :trace_start
        const-string v0, "$TRACE_TAG"
        $body
        :done
        :trace_end
        return-void
        :trace_failure
        move-exception v2
        return-void
        .catch Ljava/lang/Throwable; {:trace_start .. :trace_end} :trace_failure
        .end method
    """.trimIndent()
}

internal fun compileTraceHelper(owner: String, name: String, kind: String): MutableMethod =
    compileTraceMethod(traceHelperSmali(owner, name, kind))

internal fun compileTraceMethod(smali: String): MutableMethod {
    val lexer = smaliFlexLexer(StringReader(smali), 35)
    val tokens = CommonTokenStream(lexer)
    val parser = smaliParser(tokens)
    val result = parser.smali_file()
    check(parser.numberOfSyntaxErrors == 0 && lexer.numberOfSyntaxErrors == 0)
    val tree = CommonTreeNodeStream(result.tree).also { it.tokenStream = tokens }
    val walker = smaliTreeWalker(tree)
    walker.setDexBuilder(DexBuilder(Opcodes.forApi(35)))
    val cls = walker.smali_file()
    check(walker.numberOfSyntaxErrors == 0)
    return MutableMethod(cls.methods.single())
}

/** Preserve incoming labels on the helper so resumed completion cannot jump past tracing. */
internal fun insertTraceAtReturn(method: MutableMethod, index: Int, helper: String) {
    val instruction = method.implementation!!.instructions[index]
    require(instruction.opcode == Opcode.RETURN_OBJECT)
    val register = (instruction as OneRegisterInstruction).registerA
    method.replaceInstruction(index, "invoke-static/range {v$register .. v$register}, $helper")
    method.addInstruction(index + 1, "return-object v$register")
}

internal fun BytecodePatchContext.installAuthTrace() {
    // Check all originals before making a single change.
    traceFingerprints.forEach { (type, hash) ->
        require(traceClassHash(classDefBy(type)) == hash) { "Diagnostic fingerprint changed: $type. Refusing unsafe injection." }
    }
    fun helper(owner: String, name: String, kind: String): String {
        val cls = mutableClassDefBy(owner)
        require(cls.methods.none { it.name == TRACE_PREFIX + name })
        val method = compileTraceHelper(owner, name, kind)
        cls.methods.add(method)
        return "$owner->$TRACE_PREFIX$name(${method.parameterTypes.joinToString("")})V"
    }
    fun method(owner: String, name: String) = mutableClassDefBy(owner).methods.single { it.name == name }
    fun fixed(owner: String, target: String, event: String) {
        method(owner, target).addInstruction(0, "invoke-static {}, ${helper(owner, event, event)}")
    }
    fun returns(owner: String, target: String, stage: String) {
        val m = method(owner, target)
        val ref = helper(owner, stage, stage)
        val original = m.implementation!!.instructions.toList()
        original.indices.filter { original[it].opcode == Opcode.RETURN_OBJECT }.asReversed().forEach { index ->
            val register = (original[index] as OneRegisterInstruction).registerA
            m.addInstruction(index, "invoke-static/range {v$register .. v$register}, $ref")
        }
    }
    // Original native POST coroutine: the single pa80 branch is hash guarded.
    val request = method("Lfy0;", "invokeSuspend")
    val requestInstructions = request.implementation!!.instructions.toList()
    val requestStart = requestInstructions.indices.single {
        requestInstructions[it].opcode == Opcode.CHECK_CAST &&
            (requestInstructions[it] as ReferenceInstruction).reference.toString() == "Lpa80;"
    }
    val requestReturn = (requestStart until requestInstructions.size).first {
        requestInstructions[it].opcode == Opcode.RETURN_OBJECT
    }
    val dispatch = (requestStart until requestReturn).single {
        (requestInstructions[it] as? ReferenceInstruction)?.reference.toString() == "Lmhy;->c(Lq7m;)Ljava/lang/Object;"
    }
    insertTraceAtReturn(request, requestReturn, helper("Lfy0;", "NATIVE_RAW_HTTP", "NATIVE_RAW_HTTP"))
    request.addInstruction(dispatch, "invoke-static {}, ${helper("Lfy0;", "NATIVE_HTTP_DISPATCH", "NATIVE_HTTP_DISPATCH")}")

    val next = method(NATIVE_REPOSITORY, "e")
    val nextOriginal = next.implementation!!.instructions.toList()
    val nativeResponse = nextOriginal.indices.single {
        nextOriginal[it].opcode == Opcode.CHECK_CAST &&
            (nextOriginal[it] as ReferenceInstruction).reference.toString() == "Le580;"
    }
    val nativeRegister = (nextOriginal[nativeResponse] as OneRegisterInstruction).registerA
    next.addInstruction(nativeResponse + 1, "invoke-static/range {v$nativeRegister .. v$nativeRegister}, ${helper(NATIVE_REPOSITORY, "NATIVE_BEFORE_MAP", "NATIVE_BEFORE_MAP")}")
    next.addInstruction(nativeResponse + 1, "invoke-static/range {v$nativeRegister .. v$nativeRegister}, ${helper(NATIVE_REPOSITORY, "NATIVE_TRANSPORT", "NATIVE_TRANSPORT")}")
    returns(NATIVE_REPOSITORY, "e", "NATIVE_AFTER_MAP")
    fixed("Ljd80;", "i", "NATIVE_PASSWORD_SUBMIT")
    returns(NATIVE_REPOSITORY, "a", "NATIVE_BEGIN")
    fixed(NATIVE_REPOSITORY, "a", "NATIVE_BEGIN_ENTER")
    returns(NATIVE_REPOSITORY, "e", "NATIVE_STEP")
    fixed(NATIVE_REPOSITORY, "e", "NATIVE_STEP_ENTER")
    val page = helper("Lqd80;", "PAGE", "PAGE")
    method("Lqd80;", "a").addInstruction(0, "invoke-static/range {p1 .. p1}, $page")
    val ui = helper("Lcom/openai/auth/a;", "AUTH_UI", "AUTH_UI")
    method("Lcom/openai/auth/a;", "b").addInstruction(0, "invoke-static/range {p0 .. p0}, $ui")
    returns("Lofu0;", "a", "BROWSER_RESULT")
    fixed("Lofu0;", "a", "BROWSER_DISPATCH_ENTER")
    val callback = method("Lofu0;", "b")
    val originalCallback = callback.implementation!!.instructions.toList()
    val callbackAnchors = mapOf(
        "Authorization callback did not match the configured redirect URI" to "CALLBACK_URI_MISMATCH",
        "The received state is invalid. Try again." to "CALLBACK_STATE_MISMATCH",
        "error_description" to "CALLBACK_REMOTE_ERROR"
    )
    val points = callbackAnchors.map { (literal, event) ->
        originalCallback.indices.single { ((originalCallback[it] as? ReferenceInstruction)?.reference as? StringReference)?.string == literal } to event
    } + (originalCallback.indices.single {
        originalCallback[it].opcode == Opcode.NEW_INSTANCE &&
            (originalCallback[it] as ReferenceInstruction).reference.toString() == "Lkfu0;"
    } to "CALLBACK_CODE_PRESENT")
    points.sortedByDescending { it.first }.forEach { (index, event) ->
        callback.addInstruction(index, "invoke-static {}, ${helper("Lofu0;", event, event)}")
    }
    fixed("Lofu0;", "b", "CALLBACK_RECEIVED")
    val token = method("Lu56;", "e")
    val tokenInstructions = token.implementation!!.instructions.toList()
    val response = tokenInstructions.indices.single {
        tokenInstructions[it].opcode == Opcode.CHECK_CAST &&
            (tokenInstructions[it] as ReferenceInstruction).reference.toString() == "Le580;"
    }
    val register = (tokenInstructions[response] as OneRegisterInstruction).registerA
    val tokenHelper = helper("Lu56;", "TOKEN_HTTP", "TOKEN_HTTP")
    token.addInstruction(response + 1, "invoke-static/range {v$register .. v$register}, $tokenHelper")
    fixed("Lu56;", "e", "TOKEN_EXCHANGE_ENTER")
}
