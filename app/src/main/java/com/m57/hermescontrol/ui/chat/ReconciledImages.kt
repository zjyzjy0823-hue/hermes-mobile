package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.Attachment

internal data class ReconciledImages(
    val attachments: List<Attachment>?,
    val content: String,
)

/**
 * #1459: a local `file:`/`content:` image is only a preview source. Once history confirms the SAME message
 * with a complete gateway image set, the gateway copy replaces the local images, because the local file
 * can be released or revoked and would then fail on every lazy re-entry. Non-image attachments are kept.
 * A partial confirmed set never drops a local image (no filename/caption guessing across images).
 *
 * The matching `@image:` reference lines are carried into the content so the Room row and cold-cache
 * restore can rebuild the attachment; the model-facing `[screenshot]` placeholder is not.
 */
internal fun reconcileUserImages(
    local: List<Attachment>?,
    localContent: String,
    confirmed: List<Attachment>?,
    confirmedContent: String,
): ReconciledImages {
    val localList = local.orEmpty()
    val confirmedList = confirmed.orEmpty()
    val confirmedImages = confirmedList.filter { it.isImage && it.isGateway }
    val confirmedFiles = confirmedList.filter { !it.isImage && it.isGateway }
    val replaceImages =
        confirmedImages.isNotEmpty() && confirmedImages.size >= localList.count { it.isImage }
    val replaceFiles =
        confirmedFiles.isNotEmpty() && confirmedFiles.size >= localList.count { !it.isImage }
    if (!replaceImages && !replaceFiles) {
        return ReconciledImages(localList.takeIf { it.isNotEmpty() } ?: confirmed, localContent)
    }
    val missingRefs = mutableListOf<String>()
    if (replaceImages) {
        val knownImageRefs = imageRefLines(localContent).toSet()
        missingRefs += imageRefLines(confirmedContent).filterNot { it in knownImageRefs }
    }
    if (replaceFiles) {
        val knownFileRefs = fileRefLines(localContent).toSet()
        missingRefs += fileRefLines(confirmedContent).filterNot { it in knownFileRefs }
    }
    val content =
        if (missingRefs.isEmpty()) localContent else localContent.trimEnd() + "\n" + missingRefs.joinToString("\n")
    val attachments =
        if (localList.isEmpty()) {
            confirmedList.takeIf { it.isNotEmpty() }
        } else {
            val resolvedImages = if (replaceImages) confirmedImages else localList.filter { it.isImage }
            val resolvedFiles = if (replaceFiles) confirmedFiles else localList.filterNot { it.isImage }
            (resolvedImages + resolvedFiles).takeIf { it.isNotEmpty() }
        }
    return ReconciledImages(attachments, content)
}

private fun imageRefLines(content: String): List<String> =
    if (!content.contains("@image:")) {
        emptyList()
    } else {
        content
            .lines()
            .map { it.trim() }
            .filter { it.startsWith("@image:") && it.removePrefix("@image:").trim().startsWith("/") }
    }

private fun fileRefLines(content: String): List<String> =
    if (!content.contains("@file:")) {
        emptyList()
    } else {
        content
            .lines()
            .map { it.trim() }
            .filter { line ->
                line.startsWith("@file:") &&
                    line
                        .removePrefix("@file:")
                        .trim()
                        .removeSurrounding("\"")
                        .removeSurrounding("'")
                        .removeSurrounding("`")
                        .trim()
                        .startsWith("/")
            }
    }
