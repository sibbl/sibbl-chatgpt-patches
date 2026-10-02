// Copyright 2026 sibbl. GPL-3.0; see LICENSE and NOTICE.
package net.sibbl.chatgpt

import org.w3c.dom.Document
import org.w3c.dom.Element

internal const val DEFAULT_APP_NAME = "ChatGPT clone"
internal const val APP_NAME_RESOURCE = "morphe_chatgpt_clone_label"
internal const val APP_NAME_REFERENCE = "@string/$APP_NAME_RESOURCE"

internal fun validateAppName(value: String) {
    require(value.isNotBlank()) { "App name must contain at least one non-whitespace character." }
    require(value.codePoints().allMatch {
        it == 0x9 || it == 0xA || it == 0xD || it in 0x20..0xD7FF ||
            it in 0xE000..0xFFFD || it in 0x10000..0x10FFFF
    }) { "App name contains invalid XML characters or malformed Unicode." }
}

/** Quote for Android's string-resource parser; the DOM separately handles XML escaping. */
internal fun androidAppName(value: String): String {
    validateAppName(value)
    val escaped = buildString {
        value.forEach { character ->
            append(when (character) {
                '\\' -> "\\\\"
                '\"' -> "\\\""
                '\'' -> "\\'"
                '\n' -> "\\n"
                '\r' -> "\\r"
                '\t' -> "\\t"
                else -> character.toString()
            })
        }
    }
    return "\"$escaped\""
}

internal fun writeAppNameResource(document: Document, value: String) {
    require(document.documentElement.tagName == "resources")
    val strings = document.getElementsByTagName("string")
    require((0 until strings.length).none {
        (strings.item(it) as Element).getAttribute("name") == APP_NAME_RESOURCE
    }) { "Clone app-name resource already exists; refusing to overwrite it." }
    val string = document.createElement("string")
    string.setAttribute("name", APP_NAME_RESOURCE)
    string.setAttribute("translatable", "false")
    string.setAttribute("formatted", "false")
    string.textContent = androidAppName(value)
    document.documentElement.appendChild(string)
}

internal fun renameAppLabels(document: Document) {
    val applications = document.getElementsByTagName("application")
    require(applications.length == 1)
    val application = applications.item(0) as Element
    val originalLabel = application.getAttribute("android:label")
    application.setAttribute("android:label", APP_NAME_REFERENCE)
    listOf("activity", "activity-alias").forEach { tag ->
        val components = document.getElementsByTagName(tag)
        (0 until components.length).forEach { index ->
            val component = components.item(index) as Element
            val filters = component.getElementsByTagName("intent-filter")
            val launcher = (0 until filters.length).any { filterIndex ->
                val filter = filters.item(filterIndex) as Element
                val actions = filter.getElementsByTagName("action")
                val categories = filter.getElementsByTagName("category")
                (0 until actions.length).any {
                    (actions.item(it) as Element).getAttribute("android:name") == "android.intent.action.MAIN"
                } && (0 until categories.length).any {
                    (categories.item(it) as Element).getAttribute("android:name") in setOf(
                        "android.intent.category.LAUNCHER", "android.intent.category.LEANBACK_LAUNCHER")
                }
            }
            if (launcher || (originalLabel.isNotEmpty() && component.getAttribute("android:label") == originalLabel))
                component.setAttribute("android:label", APP_NAME_REFERENCE)
        }
    }
}
