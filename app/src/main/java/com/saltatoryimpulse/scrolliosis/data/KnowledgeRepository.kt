package com.saltatoryimpulse.scrolliosis.data

import kotlinx.coroutines.flow.Flow
import com.saltatoryimpulse.scrolliosis.BlockedApp
import com.saltatoryimpulse.scrolliosis.KnowledgeDao
import com.saltatoryimpulse.scrolliosis.KnowledgeEntry

/**
 * Lightweight repository wrapping Room DAO. Keeps data access logic
 * in one place and makes higher-level code easier to test and refactor.
 */
class KnowledgeRepository(private val dao: KnowledgeDao) : IKnowledgeRepository {
    override fun getBlockedApps(): Flow<List<BlockedApp>> = dao.getBlockedApps()
    override fun getAllEntries(): Flow<List<KnowledgeEntry>> = dao.getAllEntries()

    override suspend fun insertEntry(entry: KnowledgeEntry) = dao.insertEntry(entry)
    override suspend fun deleteEntry(entry: KnowledgeEntry) = dao.deleteEntry(entry)

    override suspend fun getRandomCustomPrompt(): KnowledgeEntry? = dao.getRandomCustomPrompt()

    override suspend fun blockApp(app: BlockedApp) = dao.blockApp(app)
    override suspend fun unblockApp(app: BlockedApp) = dao.unblockApp(app)

    override suspend fun isAppBlocked(pkg: String): Boolean = dao.isAppBlocked(pkg)
}
