// Copyright 2026 sibbl. GPL-3.0; see LICENSE and NOTICE.
package net.sibbl.chatgpt

internal val nativeTraceKinds = setOf("NATIVE_RAW_HTTP", "NATIVE_BEFORE_MAP", "NATIVE_AFTER_MAP")
internal val nativeHttpStatuses = listOf(200, 201, 202, 204, 301, 302, 400, 401, 403, 404, 408, 409, 422, 429, 500, 502, 503, 504)
// Comparison dictionary, NOT a claim that these codes were observed from ChatGPT.
// Unrecognized strings are never logged, hashed, interpolated, or retained.
internal val nativeCodeCategories = linkedMapOf(
    "invalid_credentials" to "CREDENTIALS", "invalid_password" to "CREDENTIALS",
    "incorrect_password" to "CREDENTIALS", "wrong_email_or_password" to "CREDENTIALS",
    "invalid_username_or_password" to "CREDENTIALS",
    "invalid_request" to "REQUEST", "invalid_auth_request" to "REQUEST",
    "invalid_grant" to "GRANT", "invalid_client" to "CLIENT", "unauthorized_client" to "CLIENT",
    "rate_limit_exceeded" to "RATE_LIMIT", "too_many_attempts" to "RATE_LIMIT",
    "access_denied" to "ACCESS_DENIED"
)
internal val nativePageStages = listOf("NATIVE_BEFORE_MAP", "NATIVE_AFTER_MAP")
internal val nativeTraceMessages = (nativeHttpStatuses.map { "NATIVE_RAW_HTTP_$it" } + "NATIVE_RAW_HTTP_OTHER" +
    nativePageStages.flatMap { stage ->
        listOf("PAGE_ERROR", "PAGE_PASSWORD", "PAGE_OTHER").map { "${stage}_$it" } +
            listOf("TOP", "NESTED").flatMap { location ->
                (listOf("ABSENT", "PRESENT", "METADATA_EMPTY", "METADATA_TRUNCATED", "CODE_NONE", "CODE_OTHER") +
                    nativeCodeCategories.values.map { "CODE_$it" }).map { "${stage}_${location}_$it" }
            }
    }).toSet()

/** Only status, page type, and exact comparisons of structured error codes. No display text. */
internal fun nativeTraceBody(kind: String): String = when (kind) {
    "NATIVE_RAW_HTTP" -> buildString {
        append("""
            instance-of v2, p0, Ligy;
            if-eqz v2, :done
            move-object v3, p0
            check-cast v3, Ligy;
            invoke-virtual {v3}, Ligy;->f()Lnhy;
            move-result-object v3
            if-eqz v3, :done
            iget v3, v3, Lnhy;->a:I
        """)
        nativeHttpStatuses.forEach { status ->
            append("const/16 v2, $status\nif-ne v2, v3, :raw_next_$status\n")
            append(logConstant("NATIVE_RAW_HTTP_$status"))
            append("\ngoto/16 :done\n:raw_next_$status\n")
        }
        append(logConstant("NATIVE_RAW_HTTP_OTHER"))
    }
    in nativePageStages -> buildString {
        val wrapper = if (kind == "NATIVE_BEFORE_MAP") "Ld580;" else "Lnnq0;"
        val payload = if (kind == "NATIVE_BEFORE_MAP") "a" else "b"
        append("""
            instance-of v2, p0, $wrapper
            if-eqz v2, :done
            move-object v3, p0
            check-cast v3, $wrapper
            iget-object v3, v3, $wrapper->$payload:Ljava/lang/Object;
            instance-of v2, v3, Li6u;
            if-eqz v2, :password_page
            ${logConstant("${kind}_PAGE_ERROR")}
            check-cast v3, Li6u;
            iget-object v8, v3, Li6u;->c:Lue6;
            iget-object v3, v3, Li6u;->b:Lh6u;
            if-eqz v3, :no_nested
            iget-object v3, v3, Lh6u;->d:Lue6;
            goto :errors
            :password_page
            instance-of v2, v3, Ljm40;
            if-eqz v2, :other_page
            ${logConstant("${kind}_PAGE_PASSWORD")}
            check-cast v3, Ljm40;
            iget-object v8, v3, Ljm40;->c:Lue6;
            iget-object v3, v3, Ljm40;->b:Lim40;
            if-eqz v3, :no_nested
            iget-object v3, v3, Lim40;->a:Lue6;
            goto :errors
            :other_page
            ${logConstant("${kind}_PAGE_OTHER")}
            goto/16 :done
            :no_nested
            const/4 v3, 0
            :errors
        """)
        // Nested errors in v3; top-level errors preserved in v8 across the first bounded loop.
        append(metadataBody(kind, "NESTED"))
        append("\nmove-object v3, v8\n")
        append(metadataBody(kind, "TOP"))
    }
    else -> error("Unknown native trace kind")
}

private fun metadataBody(stage: String, location: String): String = buildString {
    val prefix = "${stage}_$location"
    fun label(s: String) = "${location.lowercase()}_$s"
    append("""
        if-nez v3, :${label("present")}
        ${logConstant("${prefix}_ABSENT")}
        goto/16 :${label("done")}
        :${label("present")}
        ${logConstant("${prefix}_PRESENT")}
        iget-object v3, v3, Lue6;->d:Ljava/util/List;
        if-eqz v3, :${label("empty")}
        invoke-interface {v3}, Ljava/util/List;->size()I
        move-result v4
        if-eqz v4, :${label("empty")}
        const/4 v5, 0
        :${label("loop")}
        if-ge v5, v4, :${label("done")}
        const/16 v2, 16
        if-lt v5, v2, :${label("item")}
        ${logConstant("${prefix}_METADATA_TRUNCATED")}
        goto/16 :${label("done")}
        :${label("item")}
        invoke-interface {v3, v5}, Ljava/util/List;->get(I)Ljava/lang/Object;
        move-result-object v6
        instance-of v2, v6, Lre6;
        if-eqz v2, :${label("code_other")}
        check-cast v6, Lre6;
        iget-object v6, v6, Lre6;->a:Ljava/lang/String;
        if-eqz v6, :${label("code_none")}
    """)
    nativeCodeCategories.entries.forEachIndexed { index, (code, category) ->
        append("""
            const-string v7, "$code"
            invoke-virtual {v7, v6}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
            move-result v2
            if-eqz v2, :${label("code_$index")}
            ${logConstant("${prefix}_CODE_$category")}
            goto/16 :${label("advance")}
            :${label("code_$index")}
        """)
    }
    append("""
        :${label("code_other")}
        ${logConstant("${prefix}_CODE_OTHER")}
        goto :${label("advance")}
        :${label("code_none")}
        ${logConstant("${prefix}_CODE_NONE")}
        :${label("advance")}
        add-int/lit8 v5, v5, 1
        goto/16 :${label("loop")}
        :${label("empty")}
        ${logConstant("${prefix}_METADATA_EMPTY")}
        :${label("done")}
    """)
}
