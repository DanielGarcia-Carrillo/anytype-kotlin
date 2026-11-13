package com.anytypeio.anytype.core_ui.features.editor.table

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.anytypeio.anytype.core_ui.R
import com.anytypeio.anytype.core_ui.databinding.ItemBlockTableCellBinding
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
class TableCellHeightTest {

    private val context: Context = ApplicationProvider.getApplicationContext<Context>().apply {
        setTheme(R.style.Theme_MaterialComponents)
    }

    @Test
    fun `a cell has no minimum height of its own`() {
        val binding = givenCell()
        assertEquals(0, binding.root.minimumHeight)
    }

    @Test
    fun `an empty cell is as tall as its text field`() {
        val binding = givenCell()

        val width = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
        val height = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        binding.root.measure(width, height)

        assertTrue(binding.textContent.measuredHeight > 0)
        assertEquals(binding.textContent.measuredHeight, binding.root.measuredHeight)
    }

    private fun givenCell(): ItemBlockTableCellBinding =
        ItemBlockTableCellBinding.inflate(LayoutInflater.from(context), FrameLayout(context), false)
}
