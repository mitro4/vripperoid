package me.vripper.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import me.vripper.android.domain.Post
import me.vripper.android.domain.Status
import me.vripper.android.service.DownloadService
import me.vripper.android.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, DownloadService::class.java))
        
        setContent {
            MaterialTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    val posts by viewModel.posts.collectAsState(initial = emptyList())

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Text("+")
            }
        }
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            items(posts) { post ->
                PostItem(post, 
                    onStart = { viewModel.startDownload(post) },
                    onDelete = { viewModel.deletePost(post) }
                )
            }
        }
        
        if (showDialog) {
            AddUrlDialog(
                onDismiss = { showDialog = false },
                onAdd = { url ->
                    viewModel.addPost(url)
                    showDialog = false
                }
            )
        }
    }
}

@Composable
fun PostItem(post: Post, onStart: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = post.postTitle, style = MaterialTheme.typography.titleMedium)
            Text(text = "Status: ${post.status}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Images: ${post.downloaded}/${post.total}", style = MaterialTheme.typography.bodySmall)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (post.status == Status.STOPPED) {
                    IconButton(onClick = onStart) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Start")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
fun AddUrlDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Link") },
        text = {
            TextField(value = text, onValueChange = { text = it }, label = { Text("URL") })
        },
        confirmButton = {
            Button(onClick = { onAdd(text) }) {
                Text("Add")
            }
        }
    )
}
