package com.diary.app.ui.diary

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.diary.app.data.BodyImages

/**
 * Plain-text body editor where image and timestamp markers render as small
 * non-editable tags: the cursor can never enter one, so pressing Enter or
 * typing next to it can never split or corrupt the marker. Tags sit inline
 * with the text; the thumbnail strip below the editor shows the images.
 */
@Composable
fun RichBodyEditor(
    body: String,
    onBodyChange: (String) -> Unit,
    onEditText: (EditText) -> Unit = {},
    /** Body font size in sp from the block format config. */
    fontSizeSp: Float = 16f,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            EditText(ctx).apply {
                isSingleLine = false
                gravity = Gravity.TOP or Gravity.START
                setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                typeface = Typeface.DEFAULT
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                hint = "写下今天…"
                inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
                setPadding(0, 0, 0, 0)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        if (s != null) onBodyChange(spansToMarkers(s))
                    }
                })
                // Tapping anywhere on a marker tag's line moves the cursor
                // to the next line: the tag's line can never hold the caret.
                setOnTouchListener { _, event ->
                    if (event.action != android.view.MotionEvent.ACTION_UP) return@setOnTouchListener false
                    val layout = this.layout ?: return@setOnTouchListener false
                    val x = event.x - totalPaddingLeft
                    val y = event.y - totalPaddingTop
                    val offset = layout.getOffsetForHorizontal(
                        layout.getLineForVertical(y.toInt()),
                        x,
                    )
                    val text = text ?: return@setOnTouchListener false
                    val line = layout.getLineForOffset(offset)
                    val hit = text.getSpans(0, text.length, BlockMarkerSpan::class.java).firstOrNull { s ->
                        layout.getLineForOffset(text.getSpanStart(s)) == line
                    }
                    if (hit != null) {
                        setSelection(text.getSpanEnd(hit).coerceAtMost(text.length))
                        true
                    } else {
                        false
                    }
                }
                onEditText(this)
            }
        },
        update = { view ->
            // Only rewrite the text when the source changed externally
            // (initial load, image import, marker removal). Typing flows
            // back through the TextWatcher, so view text already matches.
            if (spansToMarkers(view.text as Editable) != body) {
                view.setText(markersToSpans(body), android.widget.TextView.BufferType.SPANNABLE)
                view.setSelection(view.length())
            }
        },
        modifier = modifier,
    )
}

/**
 * Inserts a block marker (subheading / timestamp / image) at the caret, on
 * its own line, rendered as a non-editable tag. The change flows through
 * the normal TextWatcher path, so the ViewModel body stays in sync and the
 * caret lands on the line after the new block.
 */
fun EditText.insertBlockMarker(marker: String) {
    val editable = text as Editable
    val sel = selectionStart.coerceIn(0, editable.length)
    var insert = marker
    if (sel > 0 && editable[sel - 1] != '\n') insert = "\n$insert"
    if (sel < editable.length && editable[sel] != '\n') insert += "\n"
    val tagStart = sel + insert.length - marker.length -
        (if (insert.endsWith('\n')) 1 else 0)
    editable.insert(sel, insert)
    editable.setSpan(
        spanFor(marker),
        tagStart,
        tagStart + marker.length,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
    setSelection((tagStart + marker.length + 1).coerceAtMost(editable.length))
}

/** The tag span for a raw marker string. */
private fun spanFor(marker: String): BlockMarkerSpan = when {
    marker.startsWith("![time]") -> TimestampMarkerSpan(marker)
    marker.startsWith("![sub]") -> SubheadingMarkerSpan(marker)
    else -> ImageMarkerSpan(marker)
}

/**
 * Normalizes [body] for the editor: every marker gets exactly one
 * newline before and after it (its own line), and extra blank lines
 * around markers collapse. Blank lines between unrelated paragraphs stay.
 */
internal fun normalizeEditorText(body: String): String {
    val sb = StringBuilder(body.length + 8)
    var idx = 0
    BodyImages.ALL_MARKER_PATTERN.findAll(body).forEach { match ->
        // Segment before the marker: strip leading newlines (blank lines
        // right after the previous marker) — the marker already owns its
        // line via the newline appended after it.
        sb.append(body.substring(idx, match.range.first).trimStart('\n'))
        // Collapse the run of newlines before the marker to a single one.
        while (sb.length > 1 && sb[sb.length - 1] == '\n' && sb[sb.length - 2] == '\n') {
            sb.deleteCharAt(sb.length - 1)
        }
        if (sb.isNotEmpty() && !sb.endsWith('\n')) sb.append('\n')
        sb.append(match.value)
        sb.append('\n')
        idx = match.range.last + 1
    }
    if (idx < body.length) {
        // Trailing text after the last marker: leading newlines collapse too.
        sb.append(body.substring(idx).trimStart('\n'))
    }
    return sb.toString()
}

/**
 * Renders the markers with every tag on its own line (see
 * [normalizeEditorText]); the caret lands on the next line after an
 * import and no reader-side special handling is needed.
 */
private fun markersToSpans(body: String): Spannable {
    val normalized = normalizeEditorText(body)
    val result = SpannableString(normalized)
    BodyImages.ALL_MARKER_PATTERN.findAll(normalized).forEach { match ->
        result.setSpan(
            spanFor(match.value),
            match.range.first,
            match.range.last + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    return result
}

/** Converts the editor content back to marker text (tags win). */
private fun spansToMarkers(s: Editable): String {
    val sb = StringBuilder(s.toString())
    val spans = s.getSpans(0, s.length, BlockMarkerSpan::class.java)
    for (span in spans.sortedByDescending { s.getSpanStart(it) }) {
        val start = s.getSpanStart(span)
        val end = s.getSpanEnd(span)
        if (start < 0 || end < 0 || end > sb.length) continue
        sb.replace(start, end, span.marker)
    }
    return sb.toString()
}

/** A non-editable marker tag; the caret can never enter it. */
private interface BlockMarkerSpan {
    val marker: String
}

/**
 * Draws an image marker as a rounded "图片" tag. As a ReplacementSpan the
 * caret cannot enter it, so it behaves like an atomic unit: delete it as a
 * whole or leave it, but never edit inside.
 */
private class ImageMarkerSpan(override val marker: String) : ReplacementSpan(), BlockMarkerSpan {

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: android.graphics.Paint.FontMetricsInt?): Int {
        if (fm != null) {
            val metrics = paint.fontMetricsInt
            fm.ascent = metrics.ascent
            fm.descent = metrics.descent
        }
        return (paint.measureText(LABEL) + PADDING * 2).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val width = paint.measureText(LABEL) + PADDING * 2
        canvas.drawRoundRect(RectF(x, top.toFloat(), x + width, bottom.toFloat()), 8f, 8f, BG_PAINT)
        val labelPaint = Paint(paint).apply { color = FG_COLOR }
        canvas.drawText(LABEL, x + PADDING, y.toFloat(), labelPaint)
    }

    companion object {
        private const val LABEL = "图片"
        private const val PADDING = 10f
        private val BG_PAINT = Paint().apply {
            color = 0xFFE3EBF0.toInt()
            style = Paint.Style.FILL
        }
        private const val FG_COLOR = 0xFF4A6572.toInt()
    }
}

/**
 * Draws a subheading marker as a rounded tag showing the heading text
 * itself, in a slightly heavier weight. Same atomic semantics as
 * [ImageMarkerSpan]: the caret cannot enter it, delete removes the whole
 * block.
 */
private class SubheadingMarkerSpan(override val marker: String) : ReplacementSpan(), BlockMarkerSpan {

    private val label: String =
        BodyImages.SUB_MARKER_PATTERN.matchEntire(marker)?.groupValues?.get(1)?.trim() ?: marker

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: android.graphics.Paint.FontMetricsInt?): Int {
        if (fm != null) {
            val metrics = paint.fontMetricsInt
            fm.ascent = metrics.ascent
            fm.descent = metrics.descent
        }
        return (paint.measureText(label) + PADDING * 2).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val width = paint.measureText(label) + PADDING * 2
        canvas.drawRoundRect(RectF(x, top.toFloat(), x + width, bottom.toFloat()), 8f, 8f, BG_PAINT)
        val labelPaint = Paint(paint).apply {
            color = FG_COLOR
            isFakeBoldText = true
        }
        canvas.drawText(label, x + PADDING, y.toFloat(), labelPaint)
    }

    companion object {
        private const val PADDING = 10f
        private val BG_PAINT = Paint().apply {
            color = 0xFFF1EEF8.toInt()
            style = Paint.Style.FILL
        }
        private const val FG_COLOR = 0xFF5B4B8A.toInt()
    }
}

/**
 * Draws a timestamp marker as a rounded tag showing the time itself
 * (e.g. "10:30:00"). Same atomic semantics as [ImageMarkerSpan]: the
 * caret cannot enter it, delete removes the whole block.
 */
private class TimestampMarkerSpan(override val marker: String) : ReplacementSpan(), BlockMarkerSpan {

    private val label: String =
        BodyImages.TIME_MARKER_PATTERN.matchEntire(marker)?.groupValues?.get(1) ?: marker

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: android.graphics.Paint.FontMetricsInt?): Int {
        if (fm != null) {
            val metrics = paint.fontMetricsInt
            fm.ascent = metrics.ascent
            fm.descent = metrics.descent
        }
        return (paint.measureText(label) + PADDING * 2).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val width = paint.measureText(label) + PADDING * 2
        canvas.drawRoundRect(RectF(x, top.toFloat(), x + width, bottom.toFloat()), 8f, 8f, BG_PAINT)
        val labelPaint = Paint(paint).apply { color = FG_COLOR }
        canvas.drawText(label, x + PADDING, y.toFloat(), labelPaint)
    }

    companion object {
        private const val PADDING = 10f
        private val BG_PAINT = Paint().apply {
            color = 0xFFFFF3E2.toInt()
            style = Paint.Style.FILL
        }
        private const val FG_COLOR = 0xFF8A6D3B.toInt()
    }
}
