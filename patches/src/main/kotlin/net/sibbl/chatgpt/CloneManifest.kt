/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * Original code hard forked from:
 * https://github.com/ReVanced/revanced-patches/blob/724e6d61b2ecd868c1a9a37d465a688e83a74799/patches/src/main/kotlin/app/revanced/patches/all/misc/packagename/ChangePackageNamePatch.kt
 *
 * File-Specific License Notice (GPLv3 Section 7 Terms)
 *
 * This file is part of the Morphe project and is licensed under
 * the GNU General Public License version 3 (GPLv3), with the Additional
 * Terms under Section 7 described in the LICENSE file.
 *
 * https://www.gnu.org/licenses/gpl-3.0.html
 *
 * Section 7b: Notice Preservation
 * -------------------------------
 * This entire comment block must be preserved in all copies,
 * distributions, and derivative works of this file, in both
 * original and modified source forms.
 *
 * Portions of this software are provided "AS IS" by the Morphe software project.
 * Any express or implied warranties, including the implied warranties of
 * merchantability and fitness for a particular purpose, are disclaimed.
 */

// Modified by sibbl, 2026-10-02: ChatGPT-only extraction for fixture testing.
// Also updates permission-protected components and leaves external provider queries intact.
// Original algorithm: Morphe Patches v1.45.0, commit 7387cc19e7d43b5dc35c6ecbfac0bbc2282a094e.
package net.sibbl.chatgpt

import org.w3c.dom.Document
import org.w3c.dom.Element

internal const val ORIGINAL_PACKAGE = "com.openai.chatgpt"
internal const val CLONE_PACKAGE = "app.sibbl.chatgpt.private"
internal const val CANDIDATE_VERSION = "1.2026.265"
internal const val CANDIDATE_CODE = "2626541"

internal fun validateInput(packageName: String, version: String, code: String,
                           replacement: String, permissions: Boolean, providers: Boolean) {
    require(packageName == ORIGINAL_PACKAGE) { "Only the original com.openai.chatgpt input is accepted." }
    require(version == CANDIDATE_VERSION && code == CANDIDATE_CODE) {
        "Experimental baseline is restricted to 1.2026.265 (2626541); this is not a login compatibility claim."
    }
    require(replacement != ORIGINAL_PACKAGE && replacement.matches(Regex("^[a-z]\\w*(\\.[a-z]\\w*)+$"))) {
        "Choose a valid package name different from the original."
    }
    require(permissions && providers) { "Both Update permissions and Update providers must remain enabled." }
}

private fun Document.elements(tag: String): List<Element> = getElementsByTagName(tag).let { nodes ->
    (0 until nodes.length).map { nodes.item(it) as Element }
}

internal fun renamed(value: String, replacement: String): String =
    if (value.startsWith("$ORIGINAL_PACKAGE.")) value.replaceFirst(ORIGINAL_PACKAGE, replacement)
    else "${replacement}_$value"

/** Same permission/provider renaming rules as the official universal Clone app patch.
 * OAuth intent filters and bytecode are deliberately not inferred or rewritten.
 */
internal fun cloneManifest(document: Document, replacement: String): Set<String> {
    require(document.documentElement.getAttribute("package") == ORIGINAL_PACKAGE) {
        "Input manifest was already renamed; do not also select the universal Clone app patch."
    }
    val stringResources = mutableSetOf<String>()
    document.documentElement.setAttribute("package", replacement)
    val allElements = document.elements("*")
    document.elements("permission").forEach { permission ->
        val oldName = permission.getAttribute("android:name")
        if (!oldName.startsWith('.')) {
            val newName = renamed(oldName, replacement)
            permission.setAttribute("android:name", newName)
            allElements.forEach { element ->
                if (element.tagName.startsWith("uses-permission") && element.getAttribute("android:name") == oldName)
                    element.setAttribute("android:name", newName)
                listOf("android:permission", "android:readPermission", "android:writePermission").forEach { attribute ->
                    if (element.getAttribute(attribute) == oldName) element.setAttribute(attribute, newName)
                }
            }
        }
    }
    document.elements("provider").filter { it.parentNode.nodeName == "application" }.forEach { provider ->
        val authorities = provider.getAttribute("android:authorities").split(';').map {
            if (it.startsWith('@')) {
                stringResources.add(it.removePrefix("@string/"))
                it
            } else renamed(it, replacement)
        }
        provider.setAttribute("android:authorities", authorities.joinToString(";"))
    }
    return stringResources
}

internal fun cloneProviderResources(document: Document, names: Set<String>, replacement: String) {
    document.elements("string").filter { it.getAttribute("name") in names }.forEach {
        it.textContent = renamed(it.textContent, replacement)
    }
}
