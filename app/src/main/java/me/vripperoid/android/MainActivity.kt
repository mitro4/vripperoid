package me.vripperoid.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Folder
import android.net.Uri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.compose.BackHandler
import me.vripperoid.android.domain.Post
import me.vripperoid.android.domain.Status
import me.vripperoid.android.service.DownloadService
import me.vripperoid.android.settings.SettingsStore
import me.vripperoid.android.ui.MainViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.get

import android.provider.DocumentsContract
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Popup
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Code
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.vector.ImageVector

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Build

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val settingsStore: SettingsStore by inject()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permissions granted/rejected
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, DownloadService::class.java))
        
        checkPermissions()
        
        setContent {
            MaterialTheme {
                MainScreen(viewModel, settingsStore)
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
             if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, settingsStore: SettingsStore = get()) {
    var showDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val posts by viewModel.posts.collectAsState(initial = emptyList())
    val context = LocalContext.current
    
    // Initial Setup Dialog
    var showSetupDialog by remember { mutableStateOf(settingsStore.downloadPathUri == null) }
    
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            settingsStore.downloadPathUri = uri.toString()
            showSetupDialog = false
        }
    }

    if (showSetupDialog) {
        AlertDialog(
            onDismissRequest = { /* Force user to select */ },
            title = { Text("Welcome to VRipperoid") },
            text = { Text("Please select a download folder to store your galleries. This ensures the app has permission to save files.") },
            confirmButton = {
                Button(onClick = { launcher.launch(null) }) {
                    Text("Select Folder")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VRipperoid") },
                actions = {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "Info")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!showSettings) {
                FloatingActionButton(onClick = { showDialog = true }) {
                    Text("+")
                }
            }
        }
    ) { padding ->
        if (showSettings) {
            BackHandler {
                showSettings = false
            }
            SettingsScreen(onDismiss = { showSettings = false })
        } else {
            LazyColumn(contentPadding = padding) {
                items(posts) { post ->
                    PostItem(post, 
                        onStart = { viewModel.startDownload(post) },
                        onDelete = { viewModel.deletePost(post) }
                    )
                }
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

        if (showInfo) {
            InfoPopup(onDismiss = { showInfo = false })
        }
    }
}

@Composable
fun InfoPopup(onDismiss: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("About")
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:mytridman@proton.me")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Handle no email app
                    }
                }) {
                    Icon(Icons.Filled.Email, contentDescription = "Email")
                }
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com")) // TODO: Update GitHub URL later
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Handle no browser
                    }
                }) {
                    Icon(Icons.Filled.Code, contentDescription = "GitHub") // Using Code icon as placeholder for GitHub
                }
            }
        },
        text = {
            Column {
                Text("Vripperoid - android app gallery downloader for Vipergirls forum")
                Spacer(modifier = Modifier.height(16.dp))
                Text("You can support this project by crypto :")
                Spacer(modifier = Modifier.height(8.dp))
                
                CryptoField("BTC", "bc1qt3cqtpq2n9fqx58rgt50wqdfr8u8050ewpv67l", Icons.Filled.CurrencyBitcoin)
                Spacer(modifier = Modifier.height(8.dp))
                CryptoField("ETH", "0xA9aD656Ec208e3F62F09a5c8D9730D7eCe34D3E8", Icons.Filled.Diamond) // Using Diamond for ETH
                Spacer(modifier = Modifier.height(8.dp))
                CryptoField("USDT TRC20", "TNiDdpFU2rzsrRJuvz8su5jFRm4u9CWtdj", Icons.Filled.AttachMoney)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun CryptoField(label: String, address: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val clipboardManager = LocalClipboardManager.current
    
    OutlinedTextField(
        value = address,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(icon, contentDescription = label)
        },
        trailingIcon = {
            IconButton(onClick = {
                clipboardManager.setText(AnnotatedString(address))
            }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
            }
        }
    )
}

@Composable
fun SettingsScreen(onDismiss: () -> Unit) {
    val settingsStore: SettingsStore = get()
    val context = LocalContext.current
    var maxConcurrent by remember { mutableStateOf(settingsStore.maxGlobalConcurrent.toFloat()) }
    var downloadPath by remember { mutableStateOf(getReadablePathFromUri(context, settingsStore.downloadPathUri)) }
    var deleteFromStorage by remember { mutableStateOf(settingsStore.deleteFromStorage) }
    var retryCount by remember { mutableStateOf(settingsStore.retryCount.toString()) }

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            settingsStore.downloadPathUri = uri.toString()
            downloadPath = getReadablePathFromUri(context, uri.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Global concurrent downloads", style = MaterialTheme.typography.bodyLarge)
        Text("Max Concurrent Downloads: ${maxConcurrent.toInt()}")
        Slider(
            value = maxConcurrent,
            onValueChange = { 
                maxConcurrent = it
                settingsStore.maxGlobalConcurrent = it.toInt()
            },
            valueRange = 1f..20f,
            steps = 19
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Download Directory", style = MaterialTheme.typography.bodyLarge)
        
        OutlinedTextField(
            value = downloadPath,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { launcher.launch(null) }) {
                    Icon(Icons.Filled.Folder, contentDescription = "Select Directory")
                }
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = deleteFromStorage,
                onCheckedChange = { 
                    deleteFromStorage = it
                    settingsStore.deleteFromStorage = it
                }
            )
            Text("Delete from storage when deleting post", style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Retry count (503 error)", style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = retryCount,
            onValueChange = { 
                if (it.all { char -> char.isDigit() }) {
                    retryCount = it
                    if (it.isNotEmpty()) {
                        settingsStore.retryCount = it.toInt()
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Back")
        }
    }
}

@Composable
fun PostItem(post: Post, onStart: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = post.folderName, style = MaterialTheme.typography.titleMedium)
            
            if (post.previewUrls.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(post.previewUrls) { url ->
                         AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(url)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
            }

            if (post.status == Status.DOWNLOADING || post.status == Status.PENDING || post.status == Status.FINISHED || post.status == Status.ALREADY_DOWNLOADED) {
                 val progress = if (post.total > 0) post.downloaded.toFloat() / post.total.toFloat() else 0f
                 LinearProgressIndicator(
                     progress = progress,
                     modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                 )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Status: ${post.status.stringValue}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(8.dp))
                if (post.status == Status.FINISHED || post.status == Status.ALREADY_DOWNLOADED) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Finished", tint = Color.Green)
                } else if (post.status == Status.ERROR || post.status == Status.NOT_FULL_FINISHED) {
                    Icon(Icons.Filled.Error, contentDescription = "Error", tint = Color.Red)
                }
            }
            
            Text(text = "Images: ${post.downloaded}/${post.total}", style = MaterialTheme.typography.bodySmall)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (post.downloaded > 0) {
                    IconButton(onClick = {
                         val intent = Intent(Intent.ACTION_VIEW)
                         intent.setType("image/*")
                         intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                         try {
                             context.startActivity(intent)
                         } catch (e: Exception) {
                             // Handle error
                         }
                    }) {
                         Icon(Icons.Filled.Visibility, contentDescription = "Open Gallery")
                    }
                }
            
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

fun getReadablePathFromUri(context: android.content.Context, uriString: String?): String {
    if (uriString == null) return "Default (Pictures/VRipper)"
    try {
        val uri = Uri.parse(uriString)
        val docId = DocumentsContract.getTreeDocumentId(uri)
        // docId is usually "primary:Pictures" or "ABCD-1234:Pictures"
        val split = docId.split(":")
        if (split.size > 1) {
            val type = split[0]
            val path = split[1]
            if (type == "primary") {
                return "Internal Storage/$path"
            } else {
                return "SD Card ($type)/$path"
            }
        }
        return docId
    } catch (e: Exception) {
        return Uri.decode(uriString)
    }
}
