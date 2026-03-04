package com.saltatoryimpulse.braingate.data

import kotlinx.coroutines.flow.Flow
import com.saltatoryimpulse.braingate.BlockedApp
import com.saltatoryimpulse.braingate.KnowledgeDao
import com.saltatoryimpulse.braingate.KnowledgeEntry

/**
 * Lightweight repository wrapping Room DAO. Keeps data access logic
 * in one place and makes higher-level code easier to test and refactor.
 */
class KnowledgeRepository(private val dao: KnowledgeDao) : IKnowledgeRepository {
    override fun getBlockedApps(): Flow<List<BlockedApp>> = dao.getBlockedApps()
    override fun getAllEntries(): Flow<List<KnowledgeEntry>> = dao.getAllEntries()

    override suspend fun insertEntry(entry: KnowledgeEntry) = dao.insertEntry(entry)
    override suspend fun deleteEntry(entry: KnowledgeEntry) = dao.deleteEntry(entry)

    override suspend fun blockApp(app: BlockedApp) = dao.blockApp(app)
    override suspend fun unblockApp(app: BlockedApp) = dao.unblockApp(app)

    override suspend fun isAppBlocked(pkg: String): Boolean = dao.isAppBlocked(pkg)
}
