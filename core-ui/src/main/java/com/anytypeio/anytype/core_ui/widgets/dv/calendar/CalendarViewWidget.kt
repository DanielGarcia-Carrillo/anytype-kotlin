package com.anytypeio.anytype.core_ui.widgets.dv.calendar

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import com.anytypeio.anytype.core_models.Id
import com.anytypeio.anytype.presentation.sets.CalendarViewState
import com.anytypeio.anytype.presentation.sets.model.Viewer
import java.time.LocalDate
import java.time.YearMonth

/**
 * Hosts [CalendarScreen] as one persistent composition, like the board does: the host feeds the
 * view configuration through [setCalendar] and the loaded objects through [setState], and
 * Compose diffs them into the existing composition instead of rebuilding it (which would reset
 * the visible month and the selected day).
 */
class CalendarViewWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var viewer by mutableStateOf<Viewer.Calendar?>(null)
    private var state by mutableStateOf(CalendarViewState())
    private var bottomContentInsetPixels by mutableIntStateOf(0)

    var onMonthChanged: (YearMonth) -> Unit = {}
    var onObjectClicked: (Id) -> Unit = {}
    var onCreateObject: (LocalDate) -> Unit = {}

    /** Fail closed: no create affordance until the host sets it from the permission state. */
    var canCreateObject: Boolean by mutableStateOf(false)

    @Composable
    override fun Content() {
        val current = viewer ?: return
        // Objects loaded for another view must not show on this one while its range loads.
        val shown = if (state.viewerId == current.id) state
            else state.copy(itemsByDate = emptyMap(), isLoading = true, isTruncated = false)
        // A new view starts on its own month and selection.
        key(current.id) {
            CalendarScreen(
                viewer = current,
                state = shown,
                canCreateObject = canCreateObject,
                onMonthChanged = onMonthChanged,
                onObjectClicked = onObjectClicked,
                onCreateObject = onCreateObject,
                bottomContentInset = with(LocalDensity.current) { bottomContentInsetPixels.toDp() }
            )
        }
    }

    fun setCalendar(calendar: Viewer.Calendar) {
        viewer = calendar
    }

    fun setState(calendarState: CalendarViewState) {
        state = calendarState
    }

    fun setBottomContentInset(pixels: Int) {
        bottomContentInsetPixels = pixels.coerceAtLeast(0)
    }

    /** Releases the view when another layout is shown. */
    fun clear() {
        viewer = null
    }
}
