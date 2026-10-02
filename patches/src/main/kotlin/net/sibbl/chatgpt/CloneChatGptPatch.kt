// Copyright 2026 sibbl. GPL-3.0; see LICENSE and NOTICE.
package net.sibbl.chatgpt

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption

@Suppress("unused")
val cloneChatGptPatch = resourcePatch(
    name = "Clone ChatGPT (experimental baseline)",
    description = "Separate package with mandatory permission and provider renaming. " +
        "Authentication is unresolved: this patch does not fix invalid auth request. " +
        "Do not combine with the universal Clone app patch.",
    default = false
) {
    compatibleWith(Compatibility(
        name = "ChatGPT",
        packageName = ORIGINAL_PACKAGE,
        apkFileType = ApkFileType.APKM,
        targets = listOf(AppTarget(
            version = CANDIDATE_VERSION,
            versionCode = CANDIDATE_CODE.toInt(),
            isExperimental = true,
            minSdk = 32,
            description = CANDIDATE_DESCRIPTION
        ))
    ))
    val packageName = stringOption(
        key = "packageName", default = CLONE_PACKAGE, title = "Package name",
        description = "Unique package for the private-account clone.", required = true
    )
    val appName = stringOption(
        key = "appName", default = DEFAULT_APP_NAME, title = "App name",
        description = "Application and launcher name. Unicode and punctuation are supported.", required = true
    )
    val permissions = booleanOption(
        key = "updatePermissions", default = true, title = "Update permissions",
        description = "Required: rename custom permissions to avoid installation conflicts."
    )
    val providers = booleanOption(
        key = "updateProviders", default = true, title = "Update providers",
        description = "Required: rename provider authorities to avoid installation conflicts."
    )
    // Keep upstream's finalize lifecycle; test the final manifest, not execute-time state.
    finalize {
        val replacement = requireNotNull(packageName.value)
        val label = requireNotNull(appName.value)
        validateAppName(label)
        validateInput(packageMetadata.packageName, packageMetadata.versionName,
            packageMetadata.versionCode, replacement, permissions.value == true, providers.value == true)
        val names = document("AndroidManifest.xml").use {
            cloneManifest(it, replacement).also { _ -> renameAppLabels(it) }
        }
        document("res/values/strings.xml").use {
            cloneProviderResources(it, names, replacement)
            writeAppNameResource(it, label)
        }
    }
}
