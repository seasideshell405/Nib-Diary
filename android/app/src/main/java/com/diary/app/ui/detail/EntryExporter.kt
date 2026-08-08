package com.diary.app.ui.detail

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.diary.app.data.BodyImages
import com.diary.app.data.ContentBlock
import com.diary.app.data.DiaryEntry
import com.diary.app.data.ImageFileStore
import com.diary.app.ui.diary.Mood
import com.diary.app.ui.diary.Weather
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Renders a diary entry into a shareable long-image (长图) and saves it to the
 * system gallery via MediaStore.
 *
 * TODO(待做): 导出功能当前已从阅读页/回溯页移除（按钮隐藏），实现保留
 * 以备后续重新启用：蓝色头部 + 标题高亮 + 正文块（所见即所得）、短文章
 * 最小高度、长文章完整长图。
 */
object EntryExporter {

    private const val WIDTH_PX = 1080
    private const val PADDING_PX = 56
    private const val TITLE_SIZE = 44f
    private const val META_SIZE = 30f
    private const val BODY_SIZE = 34f
    private const val LINE_HEIGHT = 1.5f
    // Subheading accent bar: ~4dp wide at 3x density, height follows text.
    private const val BAR_WIDTH = 11f
    private const val BAR_INDENT = 26
    // Title highlight (marker-pen stroke), like the reading view.
    private const val TITLE_HL_COLOR = 0xFFD9E8F7.toInt()
    private const val TITLE_HL_PAD_H = 14
    private const val TITLE_HL_PAD_V = 5
    // Timestamp chip, like the reading view.
    private const val CHIP_COLOR = 0xFFE8ECF1.toInt()
    private const val CHIP_PAD_H = 20
    private const val CHIP_PAD_V = 12
    // Per-kind letter spacing: latin/digits tighten by a fixed amount (the
    // handwritten font's latin glyphs run wide); the CJK spacing comes from
    // the block format config, converted sp -> em at render time. Mirrors
    // the reading view.
    private const val LATIN_LETTER_SPACING = -0.03f
    // Reading-view blue header (year/month, big day, weekday + time).
    private const val HEADER_HEIGHT_PX = 500
    // Short entries still export at least this much body area below the
    // header, so the image never comes out too stubby.
    private const val MIN_BODY_HEIGHT_PX = 520

    fun export(
        context: Context,
        entry: DiaryEntry,
        fileStore: ImageFileStore,
        blockStyles: BlockStylesConfig = BlockStyles.defaults,
        primaryArgb: Int = 0xFF4A90D9.toInt(),
    ): Uri? {
        val bitmap = render(entry, fileStore, blockStyles, primaryArgb) ?: return null
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "diary_${entry.id.take(8)}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Diary")
                }
            }
            val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }
            uri
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Renders the entry to a shareable content URI (cache file via
     * FileProvider) for ACTION_SEND.
     */
    fun shareBitmap(
        context: Context,
        entry: DiaryEntry,
        fileStore: ImageFileStore,
        blockStyles: BlockStylesConfig = BlockStyles.defaults,
        primaryArgb: Int = 0xFF4A90D9.toInt(),
    ): Uri? {
        val bitmap = render(entry, fileStore, blockStyles, primaryArgb) ?: return null
        return try {
            val file = File(context.cacheDir, "diary_share_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun render(
        entry: DiaryEntry,
        fileStore: ImageFileStore,
        blockStyles: BlockStylesConfig,
        primaryArgb: Int,
    ): Bitmap? {
        // CJK letter spacing from the block config, sp -> em relative to
        // the configured font size (null letterSpacing = none).
        val paragraphEm = (blockStyles.paragraph.letterSpacing ?: 0.0) /
            (blockStyles.paragraph.fontSize ?: 16.0)
        val headingEm = (blockStyles.heading.letterSpacing ?: 0.0) /
            (blockStyles.heading.fontSize ?: 20.0)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TITLE_SIZE
            typeface = Typeface.DEFAULT_BOLD
            color = Color.BLACK
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = META_SIZE
            color = Color.rgb(100, 100, 100)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = BODY_SIZE
            color = Color.BLACK
            letterSpacing = paragraphEm.toFloat()
        }
        // Subheadings: slightly larger than body text, normal weight, with
        // a left accent bar — mirrors the reading view (no bold).
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = BODY_SIZE * 1.2f
            color = Color.BLACK
            letterSpacing = headingEm.toFloat()
        }
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(70, 90, 120)
            style = Paint.Style.FILL
        }
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CHIP_COLOR
            style = Paint.Style.FILL
        }
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TITLE_HL_COLOR
            style = Paint.Style.FILL
        }
        // Reading-view blue header.
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryArgb
            style = Paint.Style.FILL
        }
        val headerYmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = META_SIZE
            color = Color.WHITE
            alpha = 230
        }
        val headerDayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 120f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.WHITE
        }
        val headerMetaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = META_SIZE
            color = Color.WHITE
            alpha = 230
        }
        val date = Instant.ofEpochMilli(entry.diaryDate).atZone(ZoneId.systemDefault()).toLocalDate()
        val time = Instant.ofEpochMilli(entry.updatedAt).atZone(ZoneId.systemDefault())
            .toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

        // Body blocks in document order (text and images interleaved),
        // mirroring the reading view. Paragraphs keep inline timestamp/
        // subheading markers expanded (legacy bodies embed them in a line);
        // standalone subheadings and timestamps render as their own parts.
        val blocks = BodyImages.parseBlocks(entry.body)
        val parts = buildList {
            for (block in blocks) {
                when (block) {
                    is ContentBlock.Paragraph -> {
                        val text = block.text
                            .replace(BodyImages.SUB_MARKER_PATTERN) { it.groupValues[1] }
                            .replace(BodyImages.TIME_MARKER_PATTERN) { it.groupValues[1] }
                        if (text.isNotBlank()) add(Part.Text(text))
                    }
                    is ContentBlock.Heading -> if (block.text.isNotBlank()) add(Part.Heading(block.text))
                    is ContentBlock.Timestamp -> add(Part.Timestamp(block.text))
                    is ContentBlock.Image -> add(Part.Image(block.id))
                }
            }
        }

        val contentWidth = WIDTH_PX - PADDING_PX * 2
        // Layout: measure heights.
        val laidOut = mutableListOf<LaidOut>()
        val imageBitmaps = mutableListOf<Bitmap>()
        var cursor = (HEADER_HEIGHT_PX + PADDING_PX).toFloat()
        var measuredHeight = HEADER_HEIGHT_PX + PADDING_PX

        fun lineHeight(paint: Paint): Float = paint.textSize * LINE_HEIGHT

        fun addParagraph(text: String, paint: Paint, indent: Int = 0) {
            for (para in text.split("\n")) {
                if (para.isEmpty()) {
                    cursor += lineHeight(paint) * 0.5f
                    continue
                }
                for (line in wrapText(para, paint, contentWidth - indent)) {
                    laidOut.add(LaidOut.Text(line, paint, cursor.toInt(), PADDING_PX + indent))
                    cursor += lineHeight(paint)
                }
            }
        }

        /** Draws [text] segment by segment with per-kind letter spacing. */
        fun drawSegmented(canvas: Canvas, text: String, paint: Paint, x: Float, baseline: Float) {
            val original = paint.letterSpacing
            val cjkEm = if (paint === bodyPaint) paragraphEm.toFloat() else headingEm.toFloat()
            var cx = x
            for ((segment, latin) in splitTypeRuns(text)) {
                paint.letterSpacing = if (latin) LATIN_LETTER_SPACING else cjkEm
                canvas.drawText(segment, cx, baseline, paint)
                cx += paint.measureText(segment)
            }
            paint.letterSpacing = original
        }

        // Title: highlight block per wrapped line, weather/mood emoji at the
        // end of the last line — mirrors the reading view. Untitled entries
        // fall back to the default date title, like the pages.
        val titleText = entry.title?.takeIf { it.isNotBlank() }
            ?: Instant.ofEpochMilli(entry.diaryDate).atZone(ZoneId.systemDefault())
                .toLocalDate().format(DateTimeFormatter.ofPattern("M月d日"))
        val titleSuffix = buildString {
            Mood.fromKey(entry.mood)?.let { append("  ").append(it.emoji) }
            Weather.fromKey(entry.weather)?.let { append("  ").append(it.emoji) }
        }
        if (titleText != null) {
            val titleLines = titleText.split("\n").flatMap { para ->
                if (para.isEmpty()) listOf("") else wrapText(para, titlePaint, contentWidth)
            }
            for ((i, line) in titleLines.withIndex()) {
                laidOut.add(
                    LaidOut.Text(
                        text = line,
                        paint = titlePaint,
                        top = cursor.toInt(),
                        suffix = if (i == titleLines.lastIndex) titleSuffix.takeIf { it.isNotBlank() } else null,
                    )
                )
                cursor += lineHeight(titlePaint)
            }
        }

        for (part in parts) {
            when (part) {
                is Part.Text -> addParagraph(part.text, bodyPaint)
                is Part.Heading -> {
                    // Accent bar on the left, vertically centered on the
                    // first line; the text is indented past it.
                    val barH = subPaint.textSize * 1.1f
                    val barTop = cursor + (lineHeight(subPaint) - barH) / 2
                    laidOut.add(LaidOut.Bar(PADDING_PX.toFloat(), barTop.toInt(), barH.toInt()))
                    addParagraph(part.text, subPaint, indent = BAR_INDENT)
                }
                is Part.Timestamp -> {
                    // Gray rounded chip with the time, like the reading view.
                    val chipH = (metaPaint.fontMetrics.descent - metaPaint.fontMetrics.ascent) +
                        CHIP_PAD_V * 2
                    laidOut.add(LaidOut.Chip(part.text, cursor.toInt()))
                    cursor += chipH + lineHeight(metaPaint) * 0.3f
                }
                is Part.Image -> {
                    val file: File? = fileStore.existingFull(part.id)
                    if (file == null) continue
                    val bmp = decodeScaled(file, contentWidth) ?: continue
                    imageBitmaps.add(bmp)
                    val scaledH = (bmp.height.toFloat() / bmp.width * contentWidth).toInt()
                    laidOut.add(LaidOut.Image(bmp, PADDING_PX, cursor.toInt(), contentWidth, scaledH))
                    cursor += scaledH + lineHeight(bodyPaint)
                }
            }
        }

        measuredHeight = maxOf(cursor.toInt() + PADDING_PX, HEADER_HEIGHT_PX + MIN_BODY_HEIGHT_PX)

        val bitmap = Bitmap.createBitmap(WIDTH_PX, measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // Reading-view blue header: year/month, big day number, weekday+time,
        // all centered.
        canvas.drawRect(0f, 0f, WIDTH_PX.toFloat(), HEADER_HEIGHT_PX.toFloat(), headerPaint)
        drawCentered(canvas, date.format(DateTimeFormatter.ofPattern("yyyy年M月")), headerYmPaint, HEADER_HEIGHT_PX * 0.34f)
        drawCentered(canvas, date.dayOfMonth.toString(), headerDayPaint, HEADER_HEIGHT_PX * 0.70f)
        drawCentered(canvas, "${weekdayOf(date)}  $time", headerMetaPaint, HEADER_HEIGHT_PX * 0.92f)

        for (item in laidOut) {
            when (item) {
                is LaidOut.Text -> {
                    item.paint.color = if (item.paint === metaPaint) {
                        Color.rgb(100, 100, 100)
                    } else {
                        Color.BLACK
                    }
                    // Title lines get a marker-pen highlight block behind
                    // the glyph box.
                    if (item.paint === titlePaint) {
                        val hlTop = (item.top - item.paint.textSize * 0.9f - TITLE_HL_PAD_V).toInt()
                        val hlBottom = (item.top + item.paint.textSize * 0.24f + TITLE_HL_PAD_V).toInt()
                        val hlRight = item.left + item.paint.measureText(item.text) + TITLE_HL_PAD_H
                        canvas.drawRoundRect(
                            (item.left - TITLE_HL_PAD_H).toFloat(),
                            hlTop.toFloat(),
                            hlRight,
                            hlBottom.toFloat(),
                            12f, 12f, highlightPaint,
                        )
                    }
                    drawSegmented(canvas, item.text, item.paint, item.left.toFloat(), item.top.toFloat())
                    item.suffix?.let {
                        canvas.drawText(
                            it,
                            (item.left + item.paint.measureText(item.text) + 8).toFloat(),
                            item.top.toFloat(),
                            metaPaint,
                        )
                    }
                }
                is LaidOut.Bar -> {
                    canvas.drawRect(
                        item.x,
                        item.top.toFloat(),
                        item.x + BAR_WIDTH,
                        (item.top + item.height).toFloat(),
                        barPaint,
                    )
                }
                is LaidOut.Chip -> {
                    // Gray rounded chip around the time text (baseline = top).
                    val fm = metaPaint.fontMetrics
                    val w = metaPaint.measureText(item.text) + CHIP_PAD_H * 2
                    val h = (fm.descent - fm.ascent) + CHIP_PAD_V * 2
                    val chipTop = item.top - fm.ascent + CHIP_PAD_V
                    canvas.drawRoundRect(
                        PADDING_PX.toFloat(),
                        chipTop,
                        PADDING_PX + w,
                        chipTop + h,
                        24f, 24f, chipPaint,
                    )
                    canvas.drawText(
                        item.text,
                        (PADDING_PX + CHIP_PAD_H).toFloat(),
                        item.top.toFloat(),
                        metaPaint,
                    )
                }
                is LaidOut.Image -> {
                    val src = Rect(0, 0, item.bmp.width, item.bmp.height)
                    val dst = Rect(item.left, item.top, item.left + item.width, item.top + item.height)
                    canvas.drawBitmap(item.bmp, src, dst, null)
                }
            }
        }

        imageBitmaps.forEach { it.recycle() }
        return bitmap
    }

    private sealed class LaidOut {
        data class Text(
            val text: String,
            val paint: Paint,
            val top: Int,
            val left: Int = PADDING_PX,
            val suffix: String? = null,
        ) : LaidOut()
        data class Bar(val x: Float, val top: Int, val height: Int) : LaidOut()
        data class Chip(val text: String, val top: Int) : LaidOut()
        data class Image(val bmp: Bitmap, val left: Int, val top: Int, val width: Int, val height: Int) : LaidOut()
    }

    private sealed class Part {
        data class Text(val text: String) : Part()
        data class Heading(val text: String) : Part()
        data class Timestamp(val text: String) : Part()
        data class Image(val id: String) : Part()
    }

    /** Draws [text] centered horizontally at [baseline]. */
    private fun drawCentered(canvas: Canvas, text: String, paint: Paint, baseline: Float) {
        canvas.drawText(text, (WIDTH_PX - paint.measureText(text)) / 2f, baseline, paint)
    }

    private fun weekdayOf(date: java.time.LocalDate): String =
        when (date.dayOfWeek.value) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }

    private fun wrapText(text: String, paint: Paint, maxWidth: Int): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (ch in text) {
            val candidate = current.toString() + ch
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder()
            }
            current.append(ch)
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private fun decodeScaled(file: File, maxWidth: Int): Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxWidth * 2) sample *= 2
        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        return android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
    }
}
