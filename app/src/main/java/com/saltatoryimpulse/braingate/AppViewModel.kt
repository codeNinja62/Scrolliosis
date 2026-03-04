package com.saltatoryimpulse.braingate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.saltatoryimpulse.braingate.data.IKnowledgeRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AppViewModel(application: Application) : AndroidViewModel(application), KoinComponent {
    private val repository: IKnowledgeRepository by inject()

    val blockedApps = repository.getBlockedApps()

    // Using stateIn with WhileSubscribed(5000) is the optimal way
    // to handle orientation changes without restarting the DB query.
    val blockedAppCount: StateFlow<Int> = repository.getBlockedApps()
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
}