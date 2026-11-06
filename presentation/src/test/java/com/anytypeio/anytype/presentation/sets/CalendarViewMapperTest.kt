package com.anytypeio.anytype.presentation.sets

import com.anytypeio.anytype.core_models.Block
import com.anytypeio.anytype.core_models.DVFilterCondition
import com.anytypeio.anytype.core_models.ObjectWrapper
import com.anytypeio.anytype.core_models.RelationFormat
import com.anytypeio.anytype.core_models.RelationLink
import com.anytypeio.anytype.core_models.Relations
import com.anytypeio.anytype.domain.config.Gateway
import com.anytypeio.anytype.domain.debugging.Logger
import com.anytypeio.anytype.domain.misc.DateProvider
import com.anytypeio.anytype.domain.misc.UrlBuilderImpl
import com.anytypeio.anytype.domain.objects.DefaultStoreOfRelations
import com.anytypeio.anytype.domain.objects.GetDateObjectByTimestamp
import com.anytypeio.anytype.domain.objects.StoreOfObjectTypes
import com.anytypeio.anytype.domain.primitives.FieldParser
import com.anytypeio.anytype.domain.primitives.FieldParserImpl
import com.anytypeio.anytype.domain.resources.StringResourceProvider
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarViewMapperTest {

    @Mock lateinit var gateway: Gateway
    @Mock lateinit var dateProvider: DateProvider
    @Mock lateinit var logger: Logger
    @Mock lateinit var getDateObjectByTimestamp: GetDateObjectByTimestamp
    @Mock lateinit var stringResourceProvider: StringResourceProvider
    @Mock lateinit var storeOfObjectTypes: StoreOfObjectTypes

    private lateinit var fieldParser: FieldParser

    private val zone = ZoneId.of("America/Los_Angeles")
    private val dueDate = "dueDate"

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        fieldParser = FieldParserImpl(dateProvider, logger, getDateObjectByTimestamp, stringResourceProvider)
        whenever(stringResourceProvider.getUntitledObjectTitle()).thenReturn("Untitled")
    }

    private fun seconds(dateTime: LocalDateTime): Double =
        dateTime.atZone(zone).toEpochSecond().toDouble()

    private fun obj(id: String, name: String, date: Any?) = ObjectWrapper.Basic(
        buildMap<String, Any?> {
            put(Relations.ID, id)
            put(Relations.NAME, name)
            if (date != null) put(dueDate, date)
        }
    )

    private fun relation(
        key: String,
        format: RelationFormat,
        name: String,
        isHidden: Boolean = false,
        readonlyValue: Boolean = false
    ) = ObjectWrapper.Relation(
        mapOf(
            Relations.ID to key,
            Relations.RELATION_KEY to key,
            Relations.RELATION_FORMAT to format.code.toDouble(),
            Relations.NAME to name,
            Relations.IS_HIDDEN to isHidden,
            Relations.RELATION_READ_ONLY_VALUE to readonlyValue
        )
    )

    private fun calendarViewer(groupRelationKey: String?) = Block.Content.DataView.Viewer(
        id = "view",
        name = "Calendar",
        type = Block.Content.DataView.Viewer.Type.CALENDAR,
        sorts = emptyList(),
        filters = emptyList(),
        viewerRelations = emptyList(),
        groupRelationKey = groupRelationKey
    )

    @Test
    fun `grid starts on the first day of the week and spans six weeks`() {
        // November 2026 starts on a Sunday.
        val (mondayStart, mondayEnd) = calendarGridRange(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 26), mondayStart)
        assertEquals(LocalDate.of(2026, 12, 6), mondayEnd)

        val (sundayStart, sundayEnd) = calendarGridRange(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 1), sundayStart)
        assertEquals(LocalDate.of(2026, 12, 12), sundayEnd)
    }

    @Test
    fun `load range covers the grids of both neighbour months`() {
        val (from, to) = calendarLoadRange(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(calendarGridRange(YearMonth.of(2026, 10), DayOfWeek.MONDAY).first, from)
        assertEquals(calendarGridRange(YearMonth.of(2026, 12), DayOfWeek.MONDAY).second, to)
    }

    @Test
    fun `range filters reach one day past each end at noon`() {
        val from = LocalDate.of(2026, 11, 1)
        val to = LocalDate.of(2026, 11, 30)
        val filters = calendarRangeFilters(dueDate, from, to, zone)

        assertEquals(2, filters.size)
        val (lower, upper) = filters
        assertEquals(DVFilterCondition.GREATER_OR_EQUAL, lower.condition)
        assertEquals(seconds(LocalDateTime.of(2026, 10, 31, 12, 0)), lower.value)
        assertEquals(DVFilterCondition.LESS_OR_EQUAL, upper.condition)
        assertEquals(seconds(LocalDateTime.of(2026, 12, 1, 12, 0)), upper.value)
        filters.forEach {
            assertEquals(dueDate, it.relation)
            assertEquals(RelationFormat.DATE, it.relationFormat)
        }
    }

    @Test
    fun `objects are grouped by their local day in subscription order`() = runTest {
        val from = LocalDate.of(2026, 11, 1)
        val to = LocalDate.of(2026, 11, 30)
        val objects = listOf(
            obj("late", "Late evening", seconds(LocalDateTime.of(2026, 11, 6, 23, 30))),
            obj("early", "Just after midnight", seconds(LocalDateTime.of(2026, 11, 7, 0, 10))),
            obj("same-day", "Same day, later in the list", seconds(LocalDateTime.of(2026, 11, 6, 9, 0))),
            obj("long", "Stored as a long", seconds(LocalDateTime.of(2026, 11, 20, 12, 0)).toLong()),
            // Outside the range: loaded because of the one-day margin, then dropped.
            obj("before", "Before", seconds(LocalDateTime.of(2026, 10, 31, 12, 0))),
            obj("no-date", "No date", null)
        )

        val grouped = groupCalendarItems(
            objects = objects,
            relationKey = dueDate,
            from = from,
            to = to,
            zone = zone,
            urlBuilder = UrlBuilderImpl(gateway),
            fieldParser = fieldParser,
            storeOfObjectTypes = storeOfObjectTypes
        )

        val want = mapOf(
            LocalDate.of(2026, 11, 6) to listOf("late", "same-day"),
            LocalDate.of(2026, 11, 7) to listOf("early"),
            LocalDate.of(2026, 11, 20) to listOf("long")
        )
        assertEquals(want, grouped.mapValues { (_, items) -> items.map { it.objectId } })
        assertEquals("Late evening", grouped.getValue(LocalDate.of(2026, 11, 6)).first().name)
    }

    @Test
    fun `new objects are dated at local noon`() {
        val timestamp = calendarTimestamp(LocalDate.of(2026, 11, 6), zone)
        assertEquals(seconds(LocalDateTime.of(2026, 11, 6, 12, 0)).toLong(), timestamp)
        assertEquals(
            LocalDate.of(2026, 11, 6),
            obj("id", "name", timestamp.toDouble()).calendarDate(dueDate, zone)
        )
    }

    @Test
    fun `date relation is resolved from the view's group property`() = runTest {
        val store = DefaultStoreOfRelations().apply {
            merge(
                listOf(
                    relation(dueDate, RelationFormat.DATE, "Due date"),
                    relation("status", RelationFormat.STATUS, "Status"),
                    relation(Relations.CREATED_DATE, RelationFormat.DATE, "Creation date", readonlyValue = true)
                )
            )
        }

        assertEquals(
            CalendarDateRelation(key = dueDate, name = "Due date", isReadonlyValue = false),
            calendarViewer(dueDate).calendarDateRelation(store)
        )
        assertEquals(
            CalendarDateRelation(Relations.CREATED_DATE, "Creation date", isReadonlyValue = true),
            calendarViewer(Relations.CREATED_DATE).calendarDateRelation(store)
        )
        // Not a date: the calendar has nothing to place objects by.
        assertNull(calendarViewer("status").calendarDateRelation(store))
        assertNull(calendarViewer(null).calendarDateRelation(store))
        assertNull(calendarViewer("").calendarDateRelation(store))
        // Not in the store yet: usable until the store says otherwise.
        assertEquals(
            CalendarDateRelation(key = "unknown", name = null, isReadonlyValue = false),
            calendarViewer("unknown").calendarDateRelation(store)
        )
    }

    @Test
    fun `date property options include system dates and skip hidden ones`() = runTest {
        val store = DefaultStoreOfRelations().apply {
            merge(
                listOf(
                    relation(dueDate, RelationFormat.DATE, "Due date"),
                    relation(Relations.CREATED_DATE, RelationFormat.DATE, "Creation date", readonlyValue = true),
                    relation("hiddenDate", RelationFormat.DATE, "Hidden", isHidden = true),
                    relation("status", RelationFormat.STATUS, "Status")
                )
            )
        }
        val links = listOf(
            RelationLink(dueDate, RelationFormat.DATE),
            RelationLink(Relations.CREATED_DATE, RelationFormat.DATE),
            RelationLink("hiddenDate", RelationFormat.DATE),
            RelationLink("status", RelationFormat.STATUS)
        )

        val options = calendarDateRelationOptions(store, links).map { it.key }

        assertEquals(listOf(dueDate, Relations.CREATED_DATE), options)
        assertTrue(options.none { it == "hiddenDate" })
    }
}
