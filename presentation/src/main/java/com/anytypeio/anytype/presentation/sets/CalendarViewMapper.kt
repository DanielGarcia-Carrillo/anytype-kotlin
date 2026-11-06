package com.anytypeio.anytype.presentation.sets

import com.anytypeio.anytype.core_models.DVFilter
import com.anytypeio.anytype.core_models.DVFilterCondition
import com.anytypeio.anytype.core_models.DVFilterQuickOption
import com.anytypeio.anytype.core_models.DVViewer
import com.anytypeio.anytype.core_models.Id
import com.anytypeio.anytype.core_models.Key
import com.anytypeio.anytype.core_models.ObjectWrapper
import com.anytypeio.anytype.core_models.RelationFormat
import com.anytypeio.anytype.core_models.UrlBuilder
import com.anytypeio.anytype.core_models.ui.ObjectIcon
import com.anytypeio.anytype.core_models.ui.objectIcon
import com.anytypeio.anytype.domain.objects.StoreOfObjectTypes
import com.anytypeio.anytype.domain.objects.StoreOfRelations
import com.anytypeio.anytype.domain.objects.getTypeOfObject
import com.anytypeio.anytype.domain.primitives.FieldParser
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A month grid always shows six weeks, so its height does not jump between months. */
const val CALENDAR_GRID_DAYS = 42L

/**
 * Upper bound for the objects one calendar subscription loads (three month grids). A month with
 * more objects shows the first ones and [CalendarViewState.isTruncated] tells the user.
 */
const val CALENDAR_RECORD_LIMIT = 1000

/** The view's date property, as far as the store knows it. */
data class CalendarDateRelation(
    val key: Key,
    val name: String?,
    /** A value the user cannot set, such as "Creation date": objects land on today only. */
    val isReadonlyValue: Boolean
)

/**
 * The objects of the calendar's visible months, grouped by local day. Fed by the calendar's own
 * date-range subscription, separate from the paged record window the other views use.
 */
data class CalendarViewState(
    val viewerId: Id? = null,
    val month: YearMonth = YearMonth.now(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val dateRelationKey: Key? = null,
    val isDateRelationReadonly: Boolean = false,
    val itemsByDate: Map<LocalDate, List<CalendarItem>> = emptyMap(),
    /** True until the first response for the current range arrives. */
    val isLoading: Boolean = true,
    /** The range held more objects than [CALENDAR_RECORD_LIMIT]; some are missing. */
    val isTruncated: Boolean = false
)

data class CalendarItem(
    val objectId: Id,
    val name: String,
    val icon: ObjectIcon,
    /** Seconds since the epoch: the object's value for the date property. */
    val timestamp: Long
)

/**
 * The date property that places objects on the calendar, or null when the view has none (or one
 * that is not a date). A property the store has not loaded yet counts as usable, the same way the
 * board treats its group property: the views re-render once the store fills.
 */
suspend fun DVViewer.calendarDateRelation(storeOfRelations: StoreOfRelations): CalendarDateRelation? {
    val key = groupRelationKey?.takeIf { it.isNotEmpty() } ?: return null
    val relation = storeOfRelations.getByKey(key)
        ?: return CalendarDateRelation(key = key, name = null, isReadonlyValue = false)
    if (relation.format != RelationFormat.DATE) return null
    return CalendarDateRelation(
        key = key,
        name = relation.name,
        isReadonlyValue = relation.isReadonlyValue
    )
}

/** First and last day of the six-week grid that shows [month]. */
fun calendarGridRange(month: YearMonth, firstDayOfWeek: DayOfWeek): Pair<LocalDate, LocalDate> {
    val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    return start to start.plusDays(CALENDAR_GRID_DAYS - 1)
}

/**
 * The days one subscription covers: the grids of [month] and of both neighbours, so a swipe to
 * the next or previous month shows its objects at once while the range moves along.
 */
fun calendarLoadRange(month: YearMonth, firstDayOfWeek: DayOfWeek): Pair<LocalDate, LocalDate> {
    val start = calendarGridRange(month.minusMonths(1), firstDayOfWeek).first
    val end = calendarGridRange(month.plusMonths(1), firstDayOfWeek).second
    return start to end
}

/** Noon of [date] in [zone], in seconds. Desktop creates calendar objects at noon too. */
fun calendarTimestamp(date: LocalDate, zone: ZoneId): Long =
    date.atTime(LocalTime.NOON).atZone(zone).toEpochSecond()

/**
 * Filters that keep the objects whose [relationKey] falls inside [from]..[to].
 *
 * The middleware widens a date filter to whole days in its own time zone, which can differ from
 * the device's. Both ends therefore reach one day further, and [groupCalendarItems] puts each
 * object on its exact local day and drops the extra ones.
 */
fun calendarRangeFilters(
    relationKey: Key,
    from: LocalDate,
    to: LocalDate,
    zone: ZoneId
): List<DVFilter> = listOf(
    DVFilter(
        relation = relationKey,
        relationFormat = RelationFormat.DATE,
        condition = DVFilterCondition.GREATER_OR_EQUAL,
        quickOption = DVFilterQuickOption.EXACT_DATE,
        value = calendarTimestamp(from.minusDays(1), zone).toDouble()
    ),
    DVFilter(
        relation = relationKey,
        relationFormat = RelationFormat.DATE,
        condition = DVFilterCondition.LESS_OR_EQUAL,
        quickOption = DVFilterQuickOption.EXACT_DATE,
        value = calendarTimestamp(to.plusDays(1), zone).toDouble()
    )
)

/** The local day of an object's date value, or null when the object has none. */
fun ObjectWrapper.Basic.calendarDate(relationKey: Key, zone: ZoneId): LocalDate? {
    val seconds = calendarSeconds(relationKey) ?: return null
    return Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate()
}

private fun ObjectWrapper.Basic.calendarSeconds(relationKey: Key): Long? =
    when (val value = map[relationKey]) {
        is Number -> value.toLong()
        is String -> value.toDoubleOrNull()?.toLong()
        else -> null
    }

/**
 * Groups [objects] by the local day of [relationKey], keeping the subscription order inside a
 * day. Objects outside [from]..[to] and objects without a value are left out.
 */
suspend fun groupCalendarItems(
    objects: List<ObjectWrapper.Basic>,
    relationKey: Key,
    from: LocalDate,
    to: LocalDate,
    zone: ZoneId,
    urlBuilder: UrlBuilder,
    fieldParser: FieldParser,
    storeOfObjectTypes: StoreOfObjectTypes
): Map<LocalDate, List<CalendarItem>> {
    val result = linkedMapOf<LocalDate, MutableList<CalendarItem>>()
    objects.forEach { obj ->
        if (!obj.isValid) return@forEach
        val seconds = obj.calendarSeconds(relationKey) ?: return@forEach
        val date = Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate()
        if (date < from || date > to) return@forEach
        result.getOrPut(date) { mutableListOf() }.add(
            CalendarItem(
                objectId = obj.id,
                name = fieldParser.getObjectName(obj),
                icon = obj.objectIcon(
                    builder = urlBuilder,
                    objType = storeOfObjectTypes.getTypeOfObject(obj)
                ),
                timestamp = seconds
            )
        )
    }
    return result
}
