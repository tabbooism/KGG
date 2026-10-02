package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.BenchmarkScreen
import com.example.ui.screens.CreatePostScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.MoshiInspectorScreen
import com.example.ui.screens.PostsScreen
import com.example.ui.screens.RestClientScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConnectApp(
    viewModel: MainViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredPosts by viewModel.filteredPosts.collectAsState()
    val apiLogs by viewModel.apiLogs.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (uiState.activeTab) {
                            0 -> "ApiConnect • Posts"
                            1 -> if (uiState.isEditingPostId != null) "Edit Post #${uiState.isEditingPostId}" else "Create Post"
                            2 -> "REST Client Workbench"
                            3 -> "Room Bookmarks"
                            4 -> "Performance Benchmark"
                            else -> "Moshi & Chaos Inspector"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            when (uiState.activeTab) {
                                0 -> viewModel.loadPostsPaged()
                                else -> viewModel.loadPostsPaged()
                            }
                        },
                        modifier = Modifier.testTag("appbar_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Data"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_navigation")
            ) {
                NavigationBarItem(
                    selected = uiState.activeTab == 0,
                    onClick = { viewModel.setTab(0) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "Posts") },
                    label = { Text("Posts") },
                    modifier = Modifier.testTag("tab_posts")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 1,
                    onClick = { viewModel.setTab(1) },
                    icon = { Icon(Icons.Default.AddCircle, contentDescription = "Create") },
                    label = { Text("Create") },
                    modifier = Modifier.testTag("tab_create")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 2,
                    onClick = { viewModel.setTab(2) },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = "REST") },
                    label = { Text("REST") },
                    modifier = Modifier.testTag("tab_rest")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 3,
                    onClick = { viewModel.setTab(3) },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = "Saved") },
                    label = { Text("Saved") },
                    modifier = Modifier.testTag("tab_saved")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 4,
                    onClick = { viewModel.setTab(4) },
                    icon = { Icon(Icons.Default.Speed, contentDescription = "Metrics") },
                    label = { Text("Speed") },
                    modifier = Modifier.testTag("tab_benchmark")
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 5,
                    onClick = { viewModel.setTab(5) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Inspector") },
                    label = { Text("Debug") },
                    modifier = Modifier.testTag("tab_inspector")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.activeTab) {
                0 -> PostsScreen(
                    uiState = uiState,
                    filteredPosts = filteredPosts,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onRefresh = viewModel::loadPostsPaged,
                    onDeletePost = viewModel::deletePost,
                    onSelectPost = viewModel::selectPost,
                    onToggleOffline = viewModel::toggleOfflineMode,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onEditPost = viewModel::startEditingPost,
                    onPageChange = viewModel::setPage,
                    onPageSizeChange = viewModel::setPageSize,
                    onSortChange = viewModel::setSorting
                )
                1 -> CreatePostScreen(
                    uiState = uiState,
                    onTitleChange = viewModel::setNewPostTitle,
                    onBodyChange = viewModel::setNewPostBody,
                    onSubmit = viewModel::submitPost,
                    onCancelEdit = viewModel::cancelEditing,
                    onDismissSuccess = viewModel::dismissCreateSuccess
                )
                2 -> RestClientScreen(
                    uiState = uiState,
                    onMethodChange = viewModel::setWorkbenchMethod,
                    onUrlChange = viewModel::setWorkbenchUrl,
                    onBodyChange = viewModel::setWorkbenchBody,
                    onSend = viewModel::executeWorkbenchRequest
                )
                3 -> FavoritesScreen(
                    favorites = favorites,
                    onRemoveFavorite = viewModel::removeFavorite
                )
                4 -> BenchmarkScreen(
                    metrics = uiState.benchmarkMetrics,
                    onRunBenchmark = viewModel::runBenchmark
                )
                5 -> MoshiInspectorScreen(
                    uiState = uiState,
                    apiLogs = apiLogs,
                    onMoshiInputChange = viewModel::setMoshiJsonInput,
                    onTestMoshi = viewModel::testMoshiParse,
                    onTestIsoDateAdapter = viewModel::testIsoDateAdapter,
                    onBaseUrlInputChange = viewModel::setBaseUrlInput,
                    onApplyBaseUrl = viewModel::applyBaseUrl,
                    onResetBaseUrl = viewModel::resetBaseUrl,
                    onClearLogs = viewModel::clearLogs,
                    onClearCache = viewModel::clearLocalCache,
                    onSetBearerToken = viewModel::setBearerToken,
                    onNewHeaderKeyChange = viewModel::setNewHeaderKey,
                    onNewHeaderValueChange = viewModel::setNewHeaderValue,
                    onAddHeader = viewModel::addCustomHeader,
                    onRemoveHeader = viewModel::removeCustomHeader,
                    onSetChaosLatency = viewModel::setChaosLatency,
                    onSetChaosErrorCode = viewModel::setChaosErrorCode,
                    onSetChaosCorruptJson = viewModel::setChaosCorruptedJson
                )
            }
        }
    }
}
