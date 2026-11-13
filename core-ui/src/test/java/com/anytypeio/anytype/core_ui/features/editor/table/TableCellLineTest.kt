package com.anytypeio.anytype.core_ui.features.editor.table

import android.content.Context
import android.os.Build
import android.text.SpannableStringBuilder
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.anytypeio.anytype.core_ui.R
import com.anytypeio.anytype.core_ui.features.table.TableEditableCellsAdapter
import com.anytypeio.anytype.core_ui.features.table.insertLineBreak
import com.anytypeio.anytype.core_ui.tools.ClipboardInterceptor
import com.anytypeio.anytype.core_models.Url
import com.anytypeio.anytype.presentation.editor.editor.model.BlockView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
class TableCellLineTest {

    private val context: Context = ApplicationProvider.getApplicationContext<Context>().apply {
        setTheme(R.style.Theme_MaterialComponents)
    }

    @Test
    fun `line break goes in at the cursor`() {
        val text = SpannableStringBuilder("ab")
        text.insertLineBreak(1..1)
        assertEquals("a\nb", text.toString())
    }

    @Test
    fun `line break replaces the selected text`() {
        val text = SpannableStringBuilder("abcd")
        text.insertLineBreak(1..3)
        assertEquals("a\nd", text.toString())
    }

    @Test
    fun `line break accepts a selection made backwards`() {
        val text = SpannableStringBuilder("abcd")
        text.insertLineBreak(3..1)
        assertEquals("a\nd", text.toString())
    }

    @Test
    fun `line break clamps a range past the end`() {
        val text = SpannableStringBuilder("ab")
        text.insertLineBreak(5..5)
        assertEquals("ab\n", text.toString())
    }

    @Test
    fun `enter in a cell adds a line break and saves the cell`() {
        val changes = mutableListOf<String>()
        val adapter = givenAdapter(text = "ab") { changes.add(it.text) }
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.bindViewHolder(holder, 0)

        holder.content.setSelection(1)
        holder.content.onEditorAction(EditorInfo.IME_ACTION_GO)

        assertEquals("a\nb", holder.content.text.toString())
        assertEquals("a\nb", changes.last())
        assertEquals(2, holder.content.selectionStart)
    }

    @Test
    fun `a cell has no minimum height beyond its text`() {
        val adapter = givenAdapter(text = "ab") {}
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        assertEquals(0, holder.root.minimumHeight)
    }

    private fun givenAdapter(
        text: String,
        onTextChanged: (BlockView.Text) -> Unit
    ): TableEditableCellsAdapter {
        val tableId = "table"
        val cell = BlockView.Table.Cell(
            tableId = tableId,
            row = BlockView.Table.Row(
                id = BlockView.Table.RowId("row"),
                index = BlockView.Table.RowIndex(0),
                isHeader = false
            ),
            column = BlockView.Table.Column(
                id = BlockView.Table.ColumnId("column"),
                index = BlockView.Table.ColumnIndex(0)
            ),
            block = BlockView.Text.Paragraph(id = "cell", text = text)
        )
        return TableEditableCellsAdapter(
            items = listOf(cell),
            clicked = {},
            onTextBlockTextChanged = onTextChanged,
            onMentionEvent = {},
            onCellSelectionChanged = { _, _ -> },
            onFocusChanged = { _, _ -> },
            clipboardInterceptor = object : ClipboardInterceptor {
                override fun onClipboardAction(action: ClipboardInterceptor.Action) {}
                override fun onBookmarkPasted(url: Url) {}
            },
            onDragAndDropTrigger = { _, _ -> false }
        )
    }
}
