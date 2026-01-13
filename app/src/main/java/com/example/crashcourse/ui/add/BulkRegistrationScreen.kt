package com.example.crashcourse.ui.add

import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.crashcourse.viewmodel.FaceViewModel
import com.example.crashcourse.viewmodel.RegisterViewModel
import com.example.crashcourse.utils.PhotoProcessingUtils
import com.example.crashcourse.utils.PhotoStorageUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkRegistrationScreen(
    faceViewModel: FaceViewModel = viewModel(),
    registerViewModel: RegisterViewModel = viewModel()
) {
    val context = LocalContext.current
    val bulkState by registerViewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // ---------- Single registration state ----------
    var name by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf("") }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var embedding by remember { mutableStateOf<FloatArray?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // ---------- Batch registration state ----------
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }

    // ---------- Photo picker ----------
    val photoLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val bmp = PhotoStorageUtils.loadBitmapFromUri(context, uri) ?: return@rememberLauncherForActivityResult

            coroutineScope.launch {
                val result = PhotoProcessingUtils.processBitmapForFaceEmbedding(context, bmp)
                if (result != null) {
                    val (faceBitmap, emb) = result
                    bitmap = faceBitmap
                    embedding = emb
                    feedback = null
                } else {
                    bitmap = bmp
                    embedding = null
                    feedback = "No face detected. Please try another photo."
                }
            }
        }

    // ---------- CSV picker ----------
    val fileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@rememberLauncherForActivityResult

            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)?.lowercase() ?: ""

            var detectedName: String? = null
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx != -1) {
                    detectedName = cursor.getString(idx)
                }
            }

            val isCsv =
                mimeType.contains("csv") ||
                detectedName?.endsWith(".csv", ignoreCase = true) == true

            if (!isCsv) {
                feedback = "Unsupported file type. Only CSV files are accepted"
                return@rememberLauncherForActivityResult
            }

            fileUri = uri
            fileName = detectedName ?: "selected_file.csv"

            registerViewModel.resetState()
            registerViewModel.prepareProcessing(context, uri)
        }

    // ---------- UI ----------
    Scaffold(
        topBar = { TopAppBar(title = { Text("Bulk Registration") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {

            /* ================= SINGLE REGISTRATION ================= */

            Text("Single Registration", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = studentId,
                onValueChange = { studentId = it },
                label = { Text("ID") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { photoLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Image, null)
                Spacer(Modifier.width(8.dp))
                Text("Select Photo")
            }

            Spacer(Modifier.height(12.dp))

            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(180.dp)
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
            }

            feedback?.let {
                Text(it, color = Color.Red)
            }

            Button(
                enabled = !isProcessing &&
                        bitmap != null &&
                        embedding != null &&
                        name.isNotBlank() &&
                        studentId.isNotBlank(),
                onClick = {
                    isProcessing = true
                    val path = PhotoStorageUtils.saveFacePhoto(context, bitmap!!, studentId)
                    if (path == null) {
                        feedback = "Failed to save photo."
                        isProcessing = false
                        return@Button
                    }

                    faceViewModel.registerFace(
                        studentId = studentId,
                        name = name,
                        embedding = embedding!!,
                        photoUrl = path,
                        onSuccess = {
                            feedback = "Registration successful!"
                            isProcessing = false
                        },
                        onDuplicate = {
                            feedback = "User already registered!"
                            isProcessing = false
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Register Student")
            }

            /* ================= DIVIDER ================= */

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            /* ================= BULK REGISTRATION ================= */

            Text("Batch Registration", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { fileLauncher.launch("*/*") },
                enabled = !bulkState.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("Select CSV File")
            }

            fileName?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Description, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            it,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = {
                                fileUri = null
                                fileName = null
                                registerViewModel.resetState()
                            }
                        ) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                }
            }

            if (fileUri != null && !bulkState.isProcessing) {
                Button(
                    onClick = {
                        registerViewModel.processCsvFile(context, fileUri!!)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Batch Processing")
                }
            }

            if (bulkState.isProcessing) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = bulkState.progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(bulkState.status)
            }

            if (bulkState.results.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Details", style = MaterialTheme.typography.titleMedium)

                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(bulkState.results) { result ->
                        Text(
                            "${result.studentId} - ${result.name}: ${result.status}",
                            color = when {
                                result.status == "Registered" -> Color.Green
                                result.status.startsWith("Duplicate") -> Color(0xFFFFA500)
                                else -> Color.Red
                            }
                        )
                    }
                }
            }
        }
    }
}
