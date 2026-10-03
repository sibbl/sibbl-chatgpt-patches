// Copyright 2026 sibbl. GPL-3.0; see LICENSE and NOTICE.
package net.sibbl.chatgpt

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

internal const val ROUTE_TRACE_PARAMETERS = "Lec6;Lxgx;ZLyj40;Lfa6;"
internal val routeTraceKinds = setOf("ROUTE_SELECTION_ON", "ROUTE_SELECTION_OFF")
internal val routeTraceSources = linkedMapOf("e" to "WELCOME", "c" to "LOGIN_MENU", "d" to "LANDING")
internal val routeTraceMessages = (listOf("ENTRY", "PREFERENCE_ON", "PREFERENCE_OFF", "SOURCE_OTHER",
    "REJECT_CREDENTIAL", "REJECT_SILENT", "REJECT_PROVIDER", "REJECT_SOURCE", "REJECT_SCOPE_ABSENT",
    "REJECT_REAUTH", "MATCH_PREFERENCE_ON", "MATCH_PREFERENCE_OFF") +
    routeTraceSources.values.map { "SOURCE_$it" }).map { "ROUTE_$it" }.toSet()

/** Read only identity/nullness/boolean guards. Logs only literals, never argument values. */
internal fun routeTraceBody(kind: String): String = buildString {
    require(kind in routeTraceKinds)
    val preference = if (kind == "ROUTE_SELECTION_ON") "ON" else "OFF"
    append(logConstant("ROUTE_ENTRY"))
    append("\n" + logConstant("ROUTE_PREFERENCE_$preference") + "\n")
    routeTraceSources.forEach { (field, category) ->
        append("sget-object v2, Lfa6;->$field:Lfa6;\nif-ne p4, v2, :source_next_$field\n")
        append(logConstant("ROUTE_SOURCE_$category"))
        append("\ngoto :guards\n:source_next_$field\n")
    }
    append(logConstant("ROUTE_SOURCE_OTHER"))
    append("""
        :guards
        if-nez p1, :credential
        if-nez p2, :silent
        sget-object v2, Lub6;->e:Lub6;
        if-ne p0, v2, :provider
        sget-object v2, Lfa6;->e:Lfa6;
        if-ne p4, v2, :source
        if-eqz p3, :scope
        iget-object v2, p3, Lyj40;->c:Ljava/lang/String;
        if-nez v2, :reauth
        ${logConstant("ROUTE_MATCH_PREFERENCE_$preference")}
        goto :done
        :credential
        ${logConstant("ROUTE_REJECT_CREDENTIAL")}
        goto :done
        :silent
        ${logConstant("ROUTE_REJECT_SILENT")}
        goto :done
        :provider
        ${logConstant("ROUTE_REJECT_PROVIDER")}
        goto :done
        :source
        ${logConstant("ROUTE_REJECT_SOURCE")}
        goto :done
        :scope
        ${logConstant("ROUTE_REJECT_SCOPE_ABSENT")}
        goto :done
        :reauth
        ${logConstant("ROUTE_REJECT_REAUTH")}
    """)
}

/** Called after original fingerprints were checked and optional browser selection was installed. */
internal fun BytecodePatchContext.installRouteSelectionTrace(preference: Boolean) {
    val cls = mutableClassDefBy(BROWSER_OWNER)
    val method = cls.methods.single { it.name == "c" }
    val code = method.implementation!!.instructions.toList()
    // Before the candidate's five instructions when on, otherwise the original selector.
    // Neither entry has an incoming original branch; only coroutine state 0 reaches it.
    val anchor = if (preference) code.indices.single {
        (code[it] as? ReferenceInstruction)?.reference?.toString() == BROWSER_HELPER_REF
    } - 1 else browserSelectionAnchor(method)
    val kind = if (preference) "ROUTE_SELECTION_ON" else "ROUTE_SELECTION_OFF"
    require(cls.methods.none { it.name.startsWith(TRACE_PREFIX) })
    cls.methods.add(compileTraceHelper(BROWSER_OWNER, kind, kind))
    method.addInstructions(anchor, """
        move-object/from16 v10, v33
        invoke-static {v0, v1, v2, v7, v10}, $BROWSER_OWNER->$TRACE_PREFIX$kind($ROUTE_TRACE_PARAMETERS)V
    """.trimIndent())
}
