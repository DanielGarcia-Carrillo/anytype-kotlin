package com.anytypeio.anytype.presentation.sets

import com.anytypeio.anytype.core_models.DVViewer
import com.anytypeio.anytype.core_models.DVViewerType
import com.anytypeio.anytype.core_models.ObjectWrapper
import com.anytypeio.anytype.core_models.StubDataViewView
import com.anytypeio.anytype.core_models.StubObject
import com.anytypeio.anytype.core_models.UrlBuilder
import com.anytypeio.anytype.domain.objects.DefaultObjectStore
import com.anytypeio.anytype.domain.objects.StoreOfObjectTypes
import com.anytypeio.anytype.domain.objects.StoreOfRelations
import com.anytypeio.anytype.domain.primitives.FieldParser
import com.anytypeio.anytype.domain.resources.StringResourceProvider
import com.anytypeio.anytype.presentation.editor.cover.CoverImageHashProvider
import com.anytypeio.anytype.presentation.relations.render
import com.anytypeio.anytype.presentation.sets.model.Viewer
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CalendarViewFallbackTest {

    @Mock
    lateinit var urlBuilder: UrlBuilder

    @Mock
    lateinit var coverImageHashProvider: CoverImageHashProvider

    @Mock
    lateinit var storeOfRelations: StoreOfRelations

    @Mock
    lateinit var storeOfObjectTypes: StoreOfObjectTypes

    @Mock
    lateinit var fieldParser: FieldParser

    @Mock
    lateinit var stringResourceProvider: StringResourceProvider

    private val store = DefaultObjectStore()

    private val first = StubObject(name = "First")
    private val second = StubObject(name = "Second")

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(fieldParser.getObjectName(any<ObjectWrapper.Basic>(), any())).thenAnswer {
            it.getArgument<ObjectWrapper.Basic>(0).name.orEmpty()
        }
    }

    private suspend fun DVViewer.renderRecords(): Viewer {
        store.merge(
            objects = listOf(first, second),
            dependencies = emptyList(),
            subscriptions = emptyList()
        )
        return render(
            builder = urlBuilder,
            coverImageHashProvider = coverImageHashProvider,
            objects = listOf(first.id, second.id),
            dataViewRelations = emptyList(),
            store = store,
            storeOfRelations = storeOfRelations,
            fieldParser = fieldParser,
            storeOfObjectTypes = storeOfObjectTypes,
            stringResourceProvider = stringResourceProvider
        )
    }

    @Test
    fun `calendar view renders its records as a grid that names the real layout`() = runTest {
        // given
        val calendar = StubDataViewView(type = DVViewerType.CALENDAR)
        val want = calendar.copy(type = DVViewerType.GRID).renderRecords()

        // when
        val got = calendar.renderRecords()

        // then
        assertIs<Viewer.GridView>(want)
        assertEquals(
            expected = want.copy(unsupportedLayout = DVViewerType.CALENDAR),
            actual = got
        )
        assertEquals(
            expected = listOf(first.id, second.id),
            actual = (got as Viewer.GridView).rows.map { it.id }
        )
    }

    @Test
    fun `grid view does not name an unsupported layout`() = runTest {
        // when
        val got = StubDataViewView(type = DVViewerType.GRID).renderRecords()

        // then
        assertIs<Viewer.GridView>(got)
        assertNull(got.unsupportedLayout)
    }

    @Test
    fun `graph view stays unsupported`() = runTest {
        // when
        val got = StubDataViewView(type = DVViewerType.GRAPH).renderRecords()

        // then
        assertIs<Viewer.Unsupported>(got)
        assertEquals(expected = Viewer.Unsupported.TYPE_GRAPH, actual = got.type)
    }
}
