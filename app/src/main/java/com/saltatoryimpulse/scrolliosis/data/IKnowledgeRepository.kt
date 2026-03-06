package com.saltatoryimpulse.scrolliosis.data

import kotlinx.coroutines.flow.Flow
import com.saltatoryimpulse.scrolliosis.BlockedApp
import com.saltatoryimpulse.scrolliosis.KnowledgeEntry

/**
 * Abstraction for knowledge data operations. Implemented by `KnowledgeRepository`.
 */
interface IKnowledgeRepository {
    fun getBlockedApps(): Flow<List<BlockedApp>>
    fun getAllEntries(): Flow<List<KnowledgeEntry>>

    suspend fun insertEntry(entry: KnowledgeEntry)
    suspend fun deleteEntry(entry: KnowledgeEntry)

    // BUG-04: returns a random user-authored knowledge prompt, or null if none exist yet
    suspend fun getRandomCustomPrompt(): KnowledgeEntry?

    suspend fun blockApp(app: BlockedApp)
    suspend fun unblockApp(app: BlockedApp)

    suspend fun isAppBlocked(pkg: String): Boolean
}
