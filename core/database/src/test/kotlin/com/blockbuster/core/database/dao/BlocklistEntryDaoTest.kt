package com.blockbuster.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blockbuster.core.common.model.BlocklistEntryDraft
import com.blockbuster.core.common.model.BlocklistTargetType
import com.blockbuster.core.common.model.BlocklistType
import com.blockbuster.core.database.AppDatabase
import com.blockbuster.core.database.entity.toEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BlocklistEntryDaoTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndObserveByListAndTargetType() = runTest {
        val dao = database.blocklistEntryDao()
        dao.insert(
            draft("com.example.app", BlocklistTargetType.APP, BlocklistType.BLACKLIST)
                .toEntity(),
        )
        dao.insert(
            draft("example.com", BlocklistTargetType.WEBSITE, BlocklistType.WHITELIST)
                .toEntity(),
        )

        val blacklisted = dao.observeByType(
            BlocklistType.BLACKLIST.name,
            BlocklistTargetType.APP.name,
        ).first()

        assertEquals(1, blacklisted.size)
        assertEquals("com.example.app", blacklisted.first().value)
    }

    @Test
    fun findDuplicateReturnsExistingEntry() = runTest {
        val dao = database.blocklistEntryDao()
        dao.insert(
            draft("example.com", BlocklistTargetType.WEBSITE, BlocklistType.BLACKLIST)
                .toEntity(),
        )

        val duplicate = dao.findDuplicate(
            value = "example.com",
            targetType = BlocklistTargetType.WEBSITE.name,
            listType = BlocklistType.BLACKLIST.name,
        )

        assertNotNull(duplicate)
    }

    @Test
    fun deleteByIdRemovesEntry() = runTest {
        val dao = database.blocklistEntryDao()
        val id = dao.insert(
            draft("shorts", BlocklistTargetType.KEYWORD, BlocklistType.BLACKLIST).toEntity(),
        )

        assertEquals(1, dao.deleteById(id))
        assertEquals(null, dao.findById(id))
    }

    private fun draft(
        value: String,
        targetType: BlocklistTargetType,
        listType: BlocklistType,
    ) = BlocklistEntryDraft(
        value = value,
        targetType = targetType,
        listType = listType,
        createdAt = 1_000L,
    )
}
