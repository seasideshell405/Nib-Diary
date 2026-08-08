package com.diary.app.ui.detail

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Per-block-type typography config, editable in Settings as JSON. One
 * entry per block type (paragraph, heading, image, timestamp); adding a
 * new block type is one entry here plus its render branch.
 *
 * Font sizes are in sp, margins in dp, lineHeight is a ratio of the used
 * font size, letterSpacing is the CJK letter spacing in sp (null = none).
 * `fontSize` is absent for blocks without text (image).
 */
@Serializable
data class BlockStyleJson(
    val fontSize: Double? = null,
    val lineHeight: Double = 1.4,
    val marginTop: Double = 12.0,
    val marginBottom: Double = 12.0,
    val letterSpacing: Double? = null,
)

@Serializable
data class BlockStylesConfig(
    val paragraph: BlockStyleJson = BlockStyleJson(
        letterSpacing = 0.75,
    ),
    val heading: BlockStyleJson = BlockStyleJson(
        fontSize = 20.0,
        lineHeight = 1.35,
        marginTop = 16.0,
        marginBottom = 8.0,
        letterSpacing = 0.75,
    ),
    val image: BlockStyleJson = BlockStyleJson(fontSize = null, marginTop = 12.0, marginBottom = 12.0),
    val timestamp: BlockStyleJson = BlockStyleJson(fontSize = 13.0, marginTop = 8.0, marginBottom = 12.0),
)

data class BlockStyle(
    val fontSize: Float?,
    val lineHeightFactor: Float,
    val marginTop: Dp,
    val marginBottom: Dp,
    val letterSpacing: Float?,
)

fun BlockStyleJson.toStyle(): BlockStyle = BlockStyle(
    fontSize = fontSize?.toFloat(),
    lineHeightFactor = lineHeight.toFloat(),
    marginTop = marginTop.dp,
    marginBottom = marginBottom.dp,
    letterSpacing = letterSpacing?.toFloat(),
)

object BlockStyles {
    val defaults: BlockStylesConfig = BlockStylesConfig()

    private val json = Json {
        ignoreUnknownKeys = true
        // Write every parameter, even when equal to the default, so the
        // editor always opens with the full picture (no bare "{}").
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    fun toJson(config: BlockStylesConfig): String =
        json.encodeToString(BlockStylesConfig.serializer(), config)

    /** Parses user-edited JSON; null when malformed or not a config. */
    fun parse(text: String): BlockStylesConfig? = runCatching {
        json.decodeFromString(BlockStylesConfig.serializer(), text)
    }.getOrNull()
}

/**
 * The effective block config from settings, provided at the app root.
 * Falls back to [BlockStyles.defaults] when unset or invalid.
 */
val LocalBlockStyles = staticCompositionLocalOf { BlockStyles.defaults }
