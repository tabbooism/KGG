package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.RetrofitClient
import com.example.data.api.model.ApiLogEntry
import com.example.data.api.model.BenchmarkMetrics
import com.example.data.api.model.Comment
import com.example.data.api.model.NetworkResult
import com.example.data.api.model.Post
import com.example.data.api.model.RestWorkbenchResponse
import com.example.data.api.model.User
import com.example.data.local.entity.CachedPostEntity
import com.example.data.local.entity.FavoritePostEntity
import com.example.data.repository.ApiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

data class UiState(
    val activeTab: Int = 0, // 0: Posts, 1: Create/Update, 2: REST Client, 3: Favorites, 4: Benchmark, 5: Moshi & Chaos
    val searchQuery: String = "",
    val postsResult: NetworkResult<List<Post>> = NetworkResult.Loading,
    val usersResult: NetworkResult<List<User>> = NetworkResult.Loading,
    val createPostResult: NetworkResult<Post>? = null,
    val postCommentsResult: NetworkResult<List<Comment>>? = null,
    val baseUrl: String = RetrofitClient.getBaseUrl(),
    val baseUrlInput: String = RetrofitClient.getBaseUrl(),
    val baseUrlSuccessMsg: String? = null,

    // Pagination & Sorting (Feature 5)
    val currentPage: Int = 1,
    val pageSize: Int = 10,
    val sortField: String = "id", // "id", "title", "body"
    val sortOrder: String = "asc", // "asc", "desc"

    // Offline & Cache (Features 2 & 9)
    val isOfflineMode: Boolean = false,
    val cacheHitCount: Int = 0,
    val cacheNetworkCount: Int = 0,
    val cacheRequestCount: Int = 0,

    // Post creation/editing
    val newPostTitle: String = "",
    val newPostBody: String = "",
    val isEditingPostId: Int? = null,
    val isCreatingPost: Boolean = false,
    val selectedPost: Post? = null,
    val favoritePostIds: Set<Int> = emptySet(),

    // Moshi Playground (Feature 1)
    val moshiJsonInput: String = "{\n  \"userId\": 1,\n  \"title\": \"Moshi Custom Adapters Test\",\n  \"body\": \"Testing IsoDateAdapter and HexColorAdapter with Kotlin reflection.\"\n}",
    val moshiTestOutput: String? = null,

    // Benchmark Suite (Feature 4)
    val benchmarkMetrics: BenchmarkMetrics = BenchmarkMetrics(),

    // REST Client Workbench (Feature 6)
    val workbenchMethod: String = "GET",
    val workbenchUrl: String = "https://jsonplaceholder.typicode.com/posts/1",
    val workbenchBody: String = "{\n  \"title\": \"Custom Workbench Request\",\n  \"body\": \"Sent via direct OkHttp runner\"\n}",
    val workbenchResponse: RestWorkbenchResponse? = null,
    val isWorkbenchLoading: Boolean = false,

    // Chaos Mode (Feature 7)
    val chaosLatencyMs: Long = 0L,
    val chaosErrorCode: Int = 0,
    val chaosCorruptJson: Boolean = false,

    // Dynamic Headers & Auth (Feature 3)
    val bearerToken: String = "",
    val customHeaders: Map<String, String> = emptyMap(),
    val newHeaderKey: String = "",
    val newHeaderValue: String = ""
)

class MainViewModel(
    private val repository: ApiRepository = ApiRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val apiLogs: StateFlow<List<ApiLogEntry>> = repository.apiLogs
    val cachedPosts: StateFlow<List<CachedPostEntity>> = repository.cachedPosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorites: StateFlow<List<FavoritePostEntity>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered posts based on search query
    val filteredPosts: StateFlow<List<Post>> = combine(_uiState, cachedPosts) { state, cached ->
        val posts = if (state.isOfflineMode) {
            cached.map { it.toPost() }
        } else {
            when (val res = state.postsResult) {
                is NetworkResult.Success -> res.data
                else -> cached.map { it.toPost() }
            }
        }
        if (state.searchQuery.isBlank()) {
            posts
        } else {
            val query = state.searchQuery.trim().lowercase()
            posts.filter {
                it.title.lowercase().contains(query) || it.body.lowercase().contains(query)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadPostsPaged()
        refreshCacheStats()

        // Sync favorite post IDs
        viewModelScope.launch {
            repository.favorites.collect { favs ->
                _uiState.value = _uiState.value.copy(
                    favoritePostIds = favs.map { it.postId }.toSet()
                )
            }
        }
    }

    fun setTab(index: Int) {
        _uiState.value = _uiState.value.copy(activeTab = index)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setNewPostTitle(title: String) {
        _uiState.value = _uiState.value.copy(newPostTitle = title)
    }

    fun setNewPostBody(body: String) {
        _uiState.value = _uiState.value.copy(newPostBody = body)
    }

    fun setBaseUrlInput(url: String) {
        _uiState.value = _uiState.value.copy(baseUrlInput = url)
    }

    fun setMoshiJsonInput(json: String) {
        _uiState.value = _uiState.value.copy(moshiJsonInput = json)
    }

    fun selectPost(post: Post?) {
        _uiState.value = _uiState.value.copy(selectedPost = post)
        if (post?.id != null) {
            loadPostComments(post.id)
        }
    }

    fun toggleOfflineMode() {
        val next = !_uiState.value.isOfflineMode
        _uiState.value = _uiState.value.copy(isOfflineMode = next)
        if (!next) {
            loadPostsPaged()
        }
    }

    // --- Pagination & Sorting (Feature 5) ---
    fun setPage(page: Int) {
        if (page < 1) return
        _uiState.value = _uiState.value.copy(currentPage = page)
        loadPostsPaged()
    }

    fun setPageSize(size: Int) {
        _uiState.value = _uiState.value.copy(pageSize = size, currentPage = 1)
        loadPostsPaged()
    }

    fun setSorting(field: String, order: String) {
        _uiState.value = _uiState.value.copy(sortField = field, sortOrder = order)
        loadPostsPaged()
    }

    fun loadPostsPaged() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(postsResult = NetworkResult.Loading)
            val result = repository.getPostsPaged(
                page = state.currentPage,
                limit = state.pageSize,
                sort = state.sortField,
                order = state.sortOrder
            )
            _uiState.value = _uiState.value.copy(postsResult = result)
            refreshCacheStats()
        }
    }

    fun loadPostComments(postId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(postCommentsResult = NetworkResult.Loading)
            val result = repository.getPostComments(postId)
            _uiState.value = _uiState.value.copy(postCommentsResult = result)
        }
    }

    // --- Create / Update Post ---
    fun startEditingPost(post: Post) {
        _uiState.value = _uiState.value.copy(
            isEditingPostId = post.id,
            newPostTitle = post.title,
            newPostBody = post.body,
            activeTab = 1
        )
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(
            isEditingPostId = null,
            newPostTitle = "",
            newPostBody = ""
        )
    }

    fun submitPost() {
        val title = _uiState.value.newPostTitle.trim()
        val body = _uiState.value.newPostBody.trim()
        if (title.isEmpty() || body.isEmpty()) return

        val editId = _uiState.value.isEditingPostId

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingPost = true, createPostResult = NetworkResult.Loading)
            val result = if (editId != null) {
                repository.updatePost(editId, Post(id = editId, userId = 1, title = title, body = body))
            } else {
                repository.createPost(Post(userId = 1, title = title, body = body))
            }

            _uiState.value = _uiState.value.copy(
                isCreatingPost = false,
                createPostResult = result,
                newPostTitle = if (result is NetworkResult.Success) "" else _uiState.value.newPostTitle,
                newPostBody = if (result is NetworkResult.Success) "" else _uiState.value.newPostBody,
                isEditingPostId = if (result is NetworkResult.Success) null else editId
            )
            loadPostsPaged()
        }
    }

    fun deletePost(id: Int) {
        viewModelScope.launch {
            val result = repository.deletePost(id)
            if (result is NetworkResult.Success) {
                loadPostsPaged()
            }
        }
    }

    // --- Favorites (Feature 8) ---
    fun toggleFavorite(post: Post, note: String = "", tag: String = "General") {
        viewModelScope.launch {
            repository.toggleFavorite(post, note, tag)
        }
    }

    fun removeFavorite(postId: Int) {
        viewModelScope.launch {
            repository.removeFavorite(postId)
        }
    }

    // --- Benchmark Suite (Feature 4) ---
    fun runBenchmark(count: Int = 5) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                benchmarkMetrics = BenchmarkMetrics(isRunning = true, totalRequests = count)
            )
            val finalMetrics = repository.runBenchmark(count) { progress ->
                _uiState.value = _uiState.value.copy(benchmarkMetrics = progress)
            }
            _uiState.value = _uiState.value.copy(benchmarkMetrics = finalMetrics)
        }
    }

    // --- REST Client Workbench (Feature 6) ---
    fun setWorkbenchMethod(method: String) {
        _uiState.value = _uiState.value.copy(workbenchMethod = method)
    }

    fun setWorkbenchUrl(url: String) {
        _uiState.value = _uiState.value.copy(workbenchUrl = url)
    }

    fun setWorkbenchBody(body: String) {
        _uiState.value = _uiState.value.copy(workbenchBody = body)
    }

    fun executeWorkbenchRequest() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorkbenchLoading = true)
            val res = repository.executeCustomRequest(
                method = state.workbenchMethod,
                url = state.workbenchUrl,
                headers = repository.getCustomHeaders(),
                bodyJson = state.workbenchBody
            )
            _uiState.value = _uiState.value.copy(
                isWorkbenchLoading = false,
                workbenchResponse = res
            )
            refreshCacheStats()
        }
    }

    // --- Chaos Engineering (Feature 7) ---
    fun setChaosLatency(ms: Long) {
        repository.setSimulatedLatency(ms)
        _uiState.value = _uiState.value.copy(chaosLatencyMs = ms)
    }

    fun setChaosErrorCode(code: Int) {
        repository.setSimulatedErrorCode(code)
        _uiState.value = _uiState.value.copy(chaosErrorCode = code)
    }

    fun setChaosCorruptedJson(enabled: Boolean) {
        repository.setSimulatedCorruptedJson(enabled)
        _uiState.value = _uiState.value.copy(chaosCorruptJson = enabled)
    }

    // --- Dynamic Headers & Auth (Feature 3) ---
    fun setBearerToken(token: String) {
        repository.setBearerToken(token.ifBlank { null })
        _uiState.value = _uiState.value.copy(bearerToken = token)
    }

    fun setNewHeaderKey(k: String) {
        _uiState.value = _uiState.value.copy(newHeaderKey = k)
    }

    fun setNewHeaderValue(v: String) {
        _uiState.value = _uiState.value.copy(newHeaderValue = v)
    }

    fun addCustomHeader() {
        val k = _uiState.value.newHeaderKey.trim()
        val v = _uiState.value.newHeaderValue.trim()
        if (k.isNotEmpty() && v.isNotEmpty()) {
            repository.setCustomHeader(k, v)
            _uiState.value = _uiState.value.copy(
                customHeaders = repository.getCustomHeaders(),
                newHeaderKey = "",
                newHeaderValue = ""
            )
        }
    }

    fun removeCustomHeader(k: String) {
        repository.removeCustomHeader(k)
        _uiState.value = _uiState.value.copy(customHeaders = repository.getCustomHeaders())
    }

    // --- Cache Stats & Clearing (Feature 9) ---
    fun refreshCacheStats() {
        val (hits, net, total) = RetrofitClient.getCacheStats()
        _uiState.value = _uiState.value.copy(
            cacheHitCount = hits,
            cacheNetworkCount = net,
            cacheRequestCount = total
        )
    }

    fun clearLocalCache() {
        viewModelScope.launch {
            repository.clearCache()
            refreshCacheStats()
            loadPostsPaged()
        }
    }

    // --- Base URL ---
    fun applyBaseUrl() {
        val url = _uiState.value.baseUrlInput.trim()
        val success = RetrofitClient.updateBaseUrl(url)
        if (success) {
            _uiState.value = _uiState.value.copy(
                baseUrl = RetrofitClient.getBaseUrl(),
                baseUrlSuccessMsg = "Base URL updated successfully to ${RetrofitClient.getBaseUrl()}"
            )
            loadPostsPaged()
        } else {
            _uiState.value = _uiState.value.copy(baseUrlSuccessMsg = "Invalid URL format")
        }
    }

    fun resetBaseUrl() {
        RetrofitClient.updateBaseUrl(RetrofitClient.DEFAULT_BASE_URL)
        _uiState.value = _uiState.value.copy(
            baseUrl = RetrofitClient.DEFAULT_BASE_URL,
            baseUrlInput = RetrofitClient.DEFAULT_BASE_URL,
            baseUrlSuccessMsg = "Base URL reset to default"
        )
        loadPostsPaged()
    }

    // --- Moshi Custom Adapters Playground (Feature 1) ---
    fun testMoshiParse() {
        val json = _uiState.value.moshiJsonInput
        try {
            val parsedPost = RetrofitClient.postFromJson(json)
            if (parsedPost != null) {
                val reSerialized = RetrofitClient.postToJson(parsedPost)
                _uiState.value = _uiState.value.copy(
                    moshiTestOutput = "✅ Parsed successfully with Moshi!\n\nKotlin Object:\nPost(\n  id=${parsedPost.id},\n  userId=${parsedPost.userId},\n  title=\"${parsedPost.title}\",\n  body=\"${parsedPost.body}\"\n)\n\nRe-serialized JSON:\n$reSerialized"
                )
            } else {
                _uiState.value = _uiState.value.copy(moshiTestOutput = "❌ Moshi returned null adapter result.")
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                moshiTestOutput = "❌ Moshi Parsing Error:\n${e.message}\n${e.cause?.message ?: ""}"
            )
        }
    }

    fun testIsoDateAdapter() {
        try {
            val adapter = RetrofitClient.moshi.adapter(Date::class.java)
            val now = Date()
            val isoJson = adapter.toJson(now)
            val parsedBack = adapter.fromJson(isoJson)
            _uiState.value = _uiState.value.copy(
                moshiTestOutput = "✅ IsoDateAdapter Test:\n\nCurrent Date: $now\nMoshi ISO-8601 Output: $isoJson\nDeserialized Date: $parsedBack"
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(moshiTestOutput = "❌ IsoDateAdapter Error: ${e.message}")
        }
    }

    fun clearLogs() {
        repository.clearLogs()
    }

    fun dismissPostDialog() {
        _uiState.value = _uiState.value.copy(selectedPost = null, postCommentsResult = null)
    }

    fun dismissCreateSuccess() {
        _uiState.value = _uiState.value.copy(createPostResult = null)
    }
}
