package com.anytypeio.anytype.core_ui.widgets.dv.calendar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anytypeio.anytype.core_models.Id
import com.anytypeio.anytype.core_models.ui.ObjectIcon
import com.anytypeio.anytype.core_ui.R
import com.anytypeio.anytype.core_ui.foundation.Divider
import com.anytypeio.anytype.core_ui.views.BodyRegular
import com.anytypeio.anytype.core_ui.views.Caption1Medium
import com.anytypeio.anytype.core_ui.views.Caption1Regular
import com.anytypeio.anytype.core_ui.views.Caption2Regular
import com.anytypeio.anytype.core_ui.views.Relations3
import com.anytypeio.anytype.core_ui.views.Title2
import com.anytypeio.anytype.core_ui.widgets.ListWidgetObjectIcon
import com.anytypeio.anytype.presentation.sets.CalendarItem
import com.anytypeio.anytype.presentation.sets.CalendarViewState
import com.anytypeio.anytype.presentation.sets.model.Viewer
import com.kizitonwose.calendar.compose.ContentHeightMode
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.OutDateStyle
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.yearMonth
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** How far the calendar scrolls from the month it opened on, in each direction. */
private const val MONTH_SCROLL_RANGE = 1200L

/** Below this width a day cell has no room for names and shows dots instead. */
private val MIN_CELL_WIDTH_FOR_NAMES = 72.dp
private val DAY_NUMBER_SIZE = 24.dp
private val CELL_NAME_HEIGHT = 16.dp
private val MAX_DOTS = 3

/**
 * The calendar layout of a data view: a month grid that swipes between months, and the objects
 * of the selected day beside or under it.
 *
 * A narrow window stacks the agenda under the grid. A wide or short window (a tablet, or a phone
 * in landscape) puts the agenda beside the grid, and the day cells show object names when they
 * are wide enough, as on desktop.
 */
@Composable
fun CalendarScreen(
    viewer: Viewer.Calendar,
    state: CalendarViewState,
    canCreateObject: Boolean,
    onMonthChanged: (YearMonth) -> Unit,
    onObjectClicked: (Id) -> Unit,
    onCreateObject: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    bottomContentInset: Dp = 0.dp
) {
    val today = remember { LocalDate.now() }
    // The calendar state is keyed on its inputs: a changing first month would rebuild it on every
    // swipe. Start from the month the view model holds and let the calendar own the scrolling.
    val initialMonth = remember(viewer.id) { state.month }
    val calendarState = rememberCalendarState(
        startMonth = initialMonth.minusMonths(MONTH_SCROLL_RANGE),
        endMonth = initialMonth.plusMonths(MONTH_SCROLL_RANGE),
        firstVisibleMonth = initialMonth,
        firstDayOfWeek = state.firstDayOfWeek,
        outDateStyle = OutDateStyle.EndOfGrid
    )
    var selectedDate by rememberSaveable(viewer.id) {
        mutableStateOf(if (initialMonth == today.yearMonth) today else initialMonth.atDay(1))
    }
    val scope = rememberCoroutineScope()
    val currentOnMonthChanged by rememberUpdatedState(onMonthChanged)

    LaunchedEffect(calendarState) {
        snapshotFlow { calendarState.firstVisibleMonth.yearMonth to calendarState.isScrollInProgress }
            .filter { (_, scrolling) -> !scrolling }
            .distinctUntilChanged { old, new -> old.first == new.first }
            .collect { (month, _) ->
                currentOnMonthChanged(month)
                // Keep the selection inside the month on screen.
                if (selectedDate.yearMonth != month) {
                    selectedDate = if (month == today.yearMonth) today else month.atDay(1)
                }
            }
    }

    val visibleMonth = calendarState.firstVisibleMonth.yearMonth
    val selectDate: (LocalDate) -> Unit = { date ->
        selectedDate = date
        if (date.yearMonth != visibleMonth) {
            scope.launch { calendarState.animateScrollToMonth(date.yearMonth) }
        }
    }
    val dateRelationMissing = viewer.dateRelationKey == null
    val canCreateOnDay: (LocalDate) -> Boolean = { date ->
        canCreateObject && !dateRelationMissing && (!state.isDateRelationReadonly || date == today)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sideBySide = maxWidth >= 600.dp || maxWidth > maxHeight
        val gridWidth = if (sideBySide) maxWidth * 0.6f else maxWidth
        val cellWidth = (gridWidth - GRID_HORIZONTAL_PADDING * 2) / 7

        val header: @Composable () -> Unit = {
            MonthHeader(
                month = visibleMonth,
                onPrevious = {
                    scope.launch { calendarState.animateScrollToMonth(visibleMonth.minusMonths(1)) }
                },
                onNext = {
                    scope.launch { calendarState.animateScrollToMonth(visibleMonth.plusMonths(1)) }
                },
                onToday = {
                    selectedDate = today
                    scope.launch { calendarState.animateScrollToMonth(today.yearMonth) }
                }
            )
            if (dateRelationMissing) {
                Hint(text = stringResource(R.string.calendar_view_no_date_property))
            } else if (state.isTruncated) {
                Hint(text = stringResource(R.string.calendar_view_truncated))
            }
            WeekDaysRow(state = state)
        }

        val grid: @Composable (Modifier, Boolean) -> Unit = { gridModifier, showNames ->
            HorizontalCalendar(
                modifier = gridModifier.padding(horizontal = GRID_HORIZONTAL_PADDING),
                state = calendarState,
                contentHeightMode = ContentHeightMode.Fill,
                dayContent = { day ->
                    DayCell(
                        day = day,
                        items = state.itemsByDate[day.date].orEmpty(),
                        isToday = day.date == today,
                        isSelected = day.date == selectedDate,
                        showNames = showNames,
                        hideIcon = viewer.hideIcon,
                        onClick = { selectDate(day.date) }
                    )
                }
            )
        }

        val agenda: @Composable (Modifier) -> Unit = { agendaModifier ->
            DayAgenda(
                modifier = agendaModifier,
                date = selectedDate,
                items = state.itemsByDate[selectedDate].orEmpty(),
                isLoading = state.isLoading,
                hideIcon = viewer.hideIcon,
                canCreate = canCreateOnDay(selectedDate),
                onCreate = { onCreateObject(selectedDate) },
                onObjectClicked = onObjectClicked,
                bottomContentInset = bottomContentInset
            )
        }

        if (sideBySide) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.width(gridWidth).fillMaxHeight()) {
                    header()
                    grid(
                        Modifier.fillMaxWidth().weight(1f).padding(bottom = bottomContentInset),
                        cellWidth >= MIN_CELL_WIDTH_FOR_NAMES
                    )
                }
                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .fillMaxHeight()
                        .background(colorResource(R.color.shape_primary))
                )
                agenda(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            // Rows take a share of the height, within bounds, so the agenda always keeps room.
            val rowHeight = (maxHeight * 0.55f / 6).coerceIn(40.dp, 72.dp)
            Column(modifier = Modifier.fillMaxSize()) {
                header()
                grid(Modifier.fillMaxWidth().height(rowHeight * 6), cellWidth >= MIN_CELL_WIDTH_FOR_NAMES)
                Divider(paddingStart = 0.dp, paddingEnd = 0.dp)
                agenda(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

private val GRID_HORIZONTAL_PADDING = 8.dp

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    val title = remember(month) {
        month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = Title2,
            color = colorResource(R.color.text_primary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClick = onToday)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            text = stringResource(R.string.today),
            style = Caption1Medium,
            color = colorResource(R.color.text_primary)
        )
        ArrowButton(
            description = stringResource(R.string.calendar_view_previous_month),
            rotation = 180f,
            onClick = onPrevious
        )
        ArrowButton(
            description = stringResource(R.string.calendar_view_next_month),
            rotation = 0f,
            onClick = onNext
        )
    }
}

@Composable
private fun ArrowButton(description: String, rotation: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Image(
            modifier = Modifier.rotate(rotation),
            painter = painterResource(R.drawable.ic_arrow_right_18),
            contentDescription = null
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        text = text,
        style = Caption1Regular,
        color = colorResource(R.color.text_secondary)
    )
}

@Composable
private fun WeekDaysRow(state: CalendarViewState) {
    val days = remember(state.firstDayOfWeek) { daysOfWeek(state.firstDayOfWeek) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GRID_HORIZONTAL_PADDING)
            .height(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        days.forEach { day ->
            Text(
                modifier = Modifier.weight(1f),
                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = Caption2Regular,
                color = colorResource(R.color.text_secondary),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    items: List<CalendarItem>,
    isToday: Boolean,
    isSelected: Boolean,
    showNames: Boolean,
    hideIcon: Boolean,
    onClick: () -> Unit
) {
    val inMonth = day.position == DayPosition.MonthDate
    val description = remember(day.date, items.size) {
        day.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())) +
                if (items.isNotEmpty()) ", ${items.size}" else ""
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) colorResource(R.color.shape_tertiary)
                else Color.Transparent
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(top = 3.dp, start = 2.dp, end = 2.dp),
        horizontalAlignment = if (showNames) Alignment.Start else Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(DAY_NUMBER_SIZE)
                .clip(CircleShape)
                .background(
                    if (isToday) colorResource(R.color.palette_system_amber_100)
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = if (isToday) Caption1Medium else Caption1Regular,
                color = when {
                    isToday -> colorResource(R.color.text_white)
                    inMonth -> colorResource(R.color.text_primary)
                    else -> colorResource(R.color.text_tertiary)
                },
                maxLines = 1
            )
        }
        if (items.isEmpty()) return@Column
        if (showNames) {
            CellNames(items = items, hideIcon = hideIcon, dimmed = !inMonth)
        } else {
            CellDots(count = items.size, dimmed = !inMonth)
        }
    }
}

/** Names of the day's objects, as many as fit in the cell, then "+N". */
@Composable
private fun CellNames(items: List<CalendarItem>, hideIcon: Boolean, dimmed: Boolean) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        val fits = ((maxHeight - 2.dp) / CELL_NAME_HEIGHT).toInt().coerceAtLeast(1)
        val shown = if (items.size > fits) (fits - 1).coerceAtLeast(0) else items.size
        val textColor = colorResource(if (dimmed) R.color.text_secondary else R.color.text_primary)
        Column(modifier = Modifier.padding(top = 2.dp)) {
            items.take(shown).forEach { item ->
                Row(
                    modifier = Modifier.height(CELL_NAME_HEIGHT),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!hideIcon && item.icon !is ObjectIcon.None) {
                        ListWidgetObjectIcon(
                            icon = item.icon,
                            modifier = Modifier.size(12.dp),
                            iconSize = 12.dp,
                            iconWithoutBackgroundMaxSize = 12.dp
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(
                        text = item.name,
                        style = Relations3,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (shown < items.size) {
                Text(
                    modifier = Modifier.height(CELL_NAME_HEIGHT),
                    text = stringResource(R.string.calendar_view_more_count, items.size - shown),
                    style = Relations3,
                    color = colorResource(R.color.text_secondary),
                    maxLines = 1
                )
            }
        }
    }
}

/** One dot per object, up to [MAX_DOTS]: a narrow cell has no room for names. */
@Composable
private fun CellDots(count: Int, dimmed: Boolean) {
    Row(
        modifier = Modifier.padding(top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val color = colorResource(if (dimmed) R.color.glyph_inactive else R.color.glyph_active)
        repeat(count.coerceAtMost(MAX_DOTS)) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun DayAgenda(
    date: LocalDate,
    items: List<CalendarItem>,
    isLoading: Boolean,
    hideIcon: Boolean,
    canCreate: Boolean,
    onCreate: () -> Unit,
    onObjectClicked: (Id) -> Unit,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier
) {
    val title = remember(date) {
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = bottomContentInset + 80.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = title,
                    style = Caption1Medium,
                    color = colorResource(R.color.text_secondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (canCreate) {
                    val description = stringResource(R.string.new_object)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onCreate)
                            .semantics { contentDescription = description },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            modifier = Modifier.size(20.dp),
                            painter = painterResource(R.drawable.ic_default_plus),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorResource(R.color.glyph_active))
                        )
                    }
                }
            }
        }
        if (items.isEmpty()) {
            if (!isLoading) {
                item(key = "empty") {
                    Text(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        text = stringResource(R.string.calendar_view_no_objects),
                        style = BodyRegular,
                        color = colorResource(R.color.text_tertiary)
                    )
                }
            }
        } else {
            items(items = items, key = { it.objectId }) { item ->
                AgendaItem(
                    item = item,
                    hideIcon = hideIcon,
                    onClick = { onObjectClicked(item.objectId) }
                )
            }
        }
    }
}

@Composable
private fun AgendaItem(item: CalendarItem, hideIcon: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!hideIcon && item.icon !is ObjectIcon.None) {
            ListWidgetObjectIcon(
                icon = item.icon,
                modifier = Modifier.size(20.dp),
                iconSize = 20.dp,
                iconWithoutBackgroundMaxSize = 20.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            modifier = Modifier.weight(1f),
            text = item.name,
            style = BodyRegular,
            color = colorResource(R.color.text_primary),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
