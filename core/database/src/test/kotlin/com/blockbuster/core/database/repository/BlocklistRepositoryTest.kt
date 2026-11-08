package com.blockbuster.core.database.repository

import com.blockbuster.core.common.model.BlocklistEntryDraft
import com.blockbuster.core.common.model.BlocklistTargetType
import com.blockbuster.core.common.model.BlocklistType
import com.blockbuster.core.common.result.AppError
import com.blockbuster.core.common.result.AppResult
import com.blockbuster.core.database.RepositoryTestFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BlocklistRepositoryTest {

    private lateinit var fixture: RepositoryTestFixture

    @Before
    fun setUp() {
        fixture = RepositoryTestFixture()
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun addsAndObservesEntry() = runTest {
        val repository = fixture.blocklistRepository(UnconfinedTestDispatcher(testScheduler))

        val result = repository.addEntry(
            draft("com.example.app", BlocklistTargetType.APP, BlocklistType.BLACKLIST),
        )

        assertTrue(result is AppResult.Success)
        assertEquals(
            listOf("com.example.app"),
            repository.observeEntries(
                BlocklistType.BLACKLIST,
                BlocklistTargetType.APP,
            ).first().map { it.value },
        )
    }

    @Test
    fun duplicateReturnsConflict() = runTest {
        val repository = fixture.blocklistRepository(UnconfinedTestDispatcher(testScheduler))
        repository.addEntry(
            draft("example.com", BlocklistTargetType.WEBSITE, BlocklistType.BLACKLIST),
        )

        val duplicate = repository.addEntry(
            draft(" EXAMPLE.com ", BlocklistTargetType.WEBSITE, BlocklistType.BLACKLIST),
        )

        assertTrue(duplicate is AppResult.Failure)
        assertTrue((duplicate as AppResult.Failure).error is AppError.Conflict)
    }

    @Test
    fun invalidPackageIsRejected() = runTest {
        val repository = fixture.blocklistRepository(UnconfinedTestDispatcher(testScheduler))

        val result = repository.addEntry(
            draft("not a package", BlocklistTargetType.APP, BlocklistType.BLACKLIST),
        )

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is AppError.Unexpected)
    }

    @Test
    fun websiteIsNormalized() = runTest {
        val repository = fixture.blocklistRepository(UnconfinedTestDispatcher(testScheduler))
        repository.addEntry(
            draft(
                "HTTPS://WWW.Example.com/path?q=1",
                BlocklistTargetType.WEBSITE,
                BlocklistType.BLACKLIST,
            ),
        )

        assertEquals(
            "example.com",
            repository.observeEntries().first().first().value,
        )
    }

    @Test
    fun deleteRemovesEntryAndMissingIdFails() = runTest {
        val repository = fixture.blocklistRepository(UnconfinedTestDispatcher(testScheduler))
        val id = (repository.addEntry(
            draft("shorts", BlocklistTargetType.KEYWORD, BlocklistType.BLACKLIST),
        ) as AppResult.Success).value

        assertTrue(repository.deleteEntry(id) is AppResult.Success)
        assertTrue(repository.observeEntries().first().isEmpty())
        assertTrue(repository.deleteEntry(id) is AppResult.Failure)
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
