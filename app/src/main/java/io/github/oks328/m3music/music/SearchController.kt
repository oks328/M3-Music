package io.github.oks328.m3music.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationSeconds: Long,
)

data class SearchState(
    val query: String = "",
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
)

class SearchController(
    private val scope: CoroutineScope,
    private val searchCatalog: suspend (String) -> List<Track>,
) {
    private val mutableState = MutableStateFlow(SearchState())
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            mutableState.value = SearchState()
            return
        }
        mutableState.value = SearchState(query = normalized, loading = true)
        searchJob = scope.launch {
            try {
                val tracks = searchCatalog(normalized)
                ensureActive()
                mutableState.value = SearchState(normalized, tracks, hasSearched = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                ensureActive()
                mutableState.value = SearchState(
                    query = normalized,
                    hasSearched = true,
                    error = if (failure is java.io.IOException)
                        "Couldn't reach YouTube Music. Check your connection and try again."
                    else "YouTube Music couldn't complete this search. Please try again later.",
                )
            }
        }
    }
}
