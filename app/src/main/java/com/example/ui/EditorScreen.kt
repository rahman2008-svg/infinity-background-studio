package com.example.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.Project
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.selectImageFromUri(uri)
        }
    }
    
    // UI reactive states
    val uiState by viewModel.uiState.collectAsState()
    val activeProjects by viewModel.activeProjects.collectAsState()
    val archivedProjects by viewModel.archivedProjects.collectAsState()
    val trashProjects by viewModel.trashProjects.collectAsState()
    val folders by viewModel.folders.collectAsState()

    val currentProj by viewModel.currentProject.collectAsState()
    val layersList by viewModel.projectLayers.collectAsState()
    val selectedLayerId by viewModel.selectedLayerId.collectAsState()

    val editedBitmap by viewModel.editedBitmap.collectAsState()
    val maskBitmap by viewModel.maskBitmap.collectAsState()

    // Slider states
    val tolerance by viewModel.toleranceSlider.collectAsState()
    val smoothness by viewModel.smoothnessSlider.collectAsState()
    val feather by viewModel.featherSlider.collectAsState()
    val faceSmooth by viewModel.faceSmoothSlider.collectAsState()
    val teethWhiten by viewModel.teethWhitenSlider.collectAsState()

    val brVal by viewModel.enhanceBrightness.collectAsState()
    val coVal by viewModel.enhanceContrast.collectAsState()
    val saVal by viewModel.enhanceSaturation.collectAsState()
    val shVal by viewModel.enhanceSharpen.collectAsState()

    // Shadow & Reflection
    val shadowEnabled by viewModel.isShadowEnabled.collectAsState()
    val shadowDx by viewModel.shadowOffsetDx.collectAsState()
    val shadowDy by viewModel.shadowOffsetDy.collectAsState()
    val shadowB by viewModel.shadowBlur.collectAsState()
    val shadowO by viewModel.shadowOpacity.collectAsState()

    val reflectionEnabled by viewModel.isReflectionEnabled.collectAsState()
    val reflectionLength by viewModel.reflectionLength.collectAsState()
    val reflectionO by viewModel.reflectionOpacity.collectAsState()

    // Local Interactive Editor modes
    var activeToolMode by remember { mutableStateOf("home") } // home, bg_remover, bg_changer, object_eraser, portrait, product, id_photo, enhancer, layers, text_sticker, crop
    var isBrushErasingMode by remember { mutableStateOf(false) } // true = erase, false = draw mask
    var brushRadius by remember { mutableStateOf(35f) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var folderNameInput by remember { mutableStateOf("") }
    
    // Text Layer creation fields
    var customTextInput by remember { mutableStateOf("") }
    var selectedTabProj by remember { mutableStateOf(0) } // 0 = Active, 1 = Archived, 2 = Trash

    // Drag translation state for subject/text layers
    var isDrawingMaskLine by remember { mutableStateOf(false) }
    val drawPoints = remember { mutableStateListOf<Offset>() }

    BackHandler {
        if (activeToolMode != "home") {
            activeToolMode = "home"
        } else if (currentProj != null) {
            viewModel.currentProject.value = null
            viewModel.originalBitmap.value = null
            viewModel.editedBitmap.value = null
        } else {
            onBack()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF6750A4), Color(0xFF958DA5))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Infinity Studio",
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF1B1B1F)
                        )
                    }
                },
                navigationIcon = {
                    if (currentProj != null) {
                        IconButton(onClick = {
                            viewModel.currentProject.value = null
                            viewModel.originalBitmap.value = null
                            viewModel.editedBitmap.value = null
                            activeToolMode = "home"
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit to projects", tint = Color(0xFF1B1B1F))
                        }
                    }
                },
                actions = {
                    if (currentProj != null) {
                        // Undo / Redo
                        IconButton(onClick = { viewModel.undo() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Undo", tint = Color(0xFF1B1B1F))
                        }
                        IconButton(onClick = { viewModel.redo() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Redo", tint = Color(0xFF1B1B1F))
                        }
                        // Save / Download Output
                        IconButton(
                            modifier = Modifier.testTag("save_button"),
                            onClick = {
                                coroutineScope.launch {
                                    val finalBmp = viewModel.renderFinalCanvas()
                                    val savedUri = viewModel.saveBitmapToGallery(finalBmp, context)
                                    if (savedUri != null) {
                                        Toast.makeText(context, "Exported successfully to phone gallery (Pictures/InfinityStudio)!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Failed to save to local storage", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Save project", tint = Color(0xFF6750A4))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFAF8FF)
                )
            )
        },
        floatingActionButton = {
            if (currentProj == null) {
                ExtendedFloatingActionButton(
                    text = { Text("Import Image", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.Photo, contentDescription = "Import local photo") },
                    onClick = {
                        imagePickerLauncher.launch("image/*")
                    },
                    containerColor = Color(0xFF6750A4),
                    contentColor = Color.White
                )
            }
        },
        containerColor = Color(0xFFFAF8FF)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentProj == null) {
                // HOME DASHBOARD PAGE
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Welcome & Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color(0xFFD0E4FF))
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.align(Alignment.CenterStart),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Remove\nBackground",
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                lineHeight = 28.sp,
                                color = Color(0xFF001D36)
                            )
                            Text(
                                text = "Instant AI cutout for any subject",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF001D36).copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    imagePickerLauncher.launch("image/*")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004A77)),
                                shape = RoundedCornerShape(50),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text("Upload Photo", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF004A77).copy(0.12f),
                            modifier = Modifier
                                .size(120.dp)
                                .align(Alignment.CenterEnd)
                        )
                    }

                    // Start New Creative Workspace (Quick load templates)
                    Text(
                        text = "Start with High-Quality Samples",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1B1B1F)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val templates = listOf(
                            Triple(R.drawable.img_sample_portrait, "👤 Portrait Retouch", "Portrait"),
                            Triple(R.drawable.img_sample_shoe, "🛍 Product Studio", "Product"),
                            Triple(R.drawable.img_sample_car, "🚗 Car Cutout", "Car"),
                            Triple(R.drawable.img_sample_cat, "🐱 Animal Edit", "Animal")
                        )
                        items(templates) { (res, label, category) ->
                            Card(
                                modifier = Modifier
                                    .width(130.dp)
                                    .clickable {
                                        viewModel.selectTemplate(res, category)
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFFAF8FF)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(
                                        painter = painterResource(id = res),
                                        contentDescription = label,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .clip(RoundedCornerShape(16.dp, 16.dp, 0.dp, 0.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1B1B1F),
                                        modifier = Modifier.padding(8.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // 2x2 Vibrant Quick Actions Grid from Design Theme
                    Text(
                        text = "Quick Studio Functions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1B1B1F)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Eraser Action
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color(0xFFEADDFF))
                                    .clickable { viewModel.selectTemplate(R.drawable.img_sample_portrait, "Portrait") }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF21005D))
                                        .padding(8.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCut, contentDescription = "Eraser", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Text("Eraser", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF21005D))
                            }

                            // Portrait Action
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color(0xFFE8DEF8))
                                    .clickable { viewModel.selectTemplate(R.drawable.img_sample_portrait, "Portrait") }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1D192B))
                                        .padding(8.dp)
                                ) {
                                    Icon(Icons.Filled.Face, contentDescription = "Portrait", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Text("Portrait", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1D192B))
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Product Action
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color(0xFFF2B8B5))
                                    .clickable { viewModel.selectTemplate(R.drawable.img_sample_shoe, "Product") }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF601410))
                                        .padding(8.dp)
                                ) {
                                    Icon(Icons.Filled.ShoppingBag, contentDescription = "Product", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Text("Product", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF601410))
                            }

                            // Enhance Action
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color(0xFFCCE8E7))
                                    .clickable { viewModel.selectTemplate(R.drawable.img_sample_cat, "Animal") }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF005049))
                                        .padding(8.dp)
                                ) {
                                    Icon(Icons.Filled.AutoFixHigh, contentDescription = "Enhance", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Text("Enhance", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF005049))
                            }
                        }
                    }

                    // Recent Projects section header and view switcher
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Projects",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1B1B1F)
                        )
                    }

                    // Project Tabs Menu
                    TabRow(
                        selectedTabIndex = selectedTabProj,
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF1B1B1F),
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabProj]),
                                color = Color(0xFF6750A4)
                            )
                        }
                    ) {
                        Tab(selected = selectedTabProj == 0, onClick = { selectedTabProj = 0 }) {
                            Text("Active (${activeProjects.size})", modifier = Modifier.padding(12.dp))
                        }
                        Tab(selected = selectedTabProj == 1, onClick = { selectedTabProj = 1 }) {
                            Text("Archived (${archivedProjects.size})", modifier = Modifier.padding(12.dp))
                        }
                        Tab(selected = selectedTabProj == 2, onClick = { selectedTabProj = 2 }) {
                            Text("Trash (${trashProjects.size})", modifier = Modifier.padding(12.dp))
                        }
                    }

                    val projectListToDisplay = when (selectedTabProj) {
                        0 -> activeProjects
                        1 -> archivedProjects
                        else -> trashProjects
                    }

                    if (projectListToDisplay.isEmpty()) {
                        // Empty states
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                Text("No studio projects found here", color = Color.Gray, fontSize = 14.sp)
                                Text("Tap a sample image above to start immediately!", color = Color.Gray.copy(0.7f), fontSize = 12.sp)
                            }
                        }
                    } else {
                        // Projects Grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 600.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(projectListToDisplay) { proj ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFFAF8FF)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(110.dp)
                                                .clip(RoundedCornerShape(16.dp, 16.dp, 0.dp, 0.dp))
                                        ) {
                                            // Show sample template thumbnail if assigned
                                            val resId = proj.sampleImageResId ?: R.drawable.img_sample_portrait
                                            Image(
                                                painter = painterResource(id = resId),
                                                contentDescription = proj.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .padding(8.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.Black.copy(0.6f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    .align(Alignment.TopStart)
                                            ) {
                                                Text(proj.category, color = Color.White, fontSize = 10.sp)
                                            }
                                        }

                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                proj.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF1B1B1F),
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (selectedTabProj == 2) {
                                                    // Trash actions
                                                    IconButton(onClick = { viewModel.trashProject(proj, false) }) {
                                                        Icon(Icons.Filled.Restore, contentDescription = "Restore", tint = Color.Green, modifier = Modifier.size(18.dp))
                                                    }
                                                    IconButton(onClick = { viewModel.deleteProjectPermanently(proj) }) {
                                                        Icon(Icons.Filled.DeleteForever, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
                                                    }
                                                } else {
                                                    IconButton(onClick = { viewModel.loadProject(proj) }) {
                                                        Icon(Icons.Filled.Edit, contentDescription = "Open", tint = Color(0xFF6750A4), modifier = Modifier.size(18.dp))
                                                    }
                                                    IconButton(onClick = { viewModel.duplicateProject(proj) }) {
                                                        Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate", tint = Color(0xFF49454F), modifier = Modifier.size(16.dp))
                                                    }
                                                    IconButton(onClick = { viewModel.trashProject(proj, true) }) {
                                                        Icon(Icons.Filled.Delete, contentDescription = "Trash", tint = Color.Red.copy(0.7f), modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // About Developer, About Company, Technical Info, Credits Sections
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

                    // --- Section Header ---
                    Text(
                        text = "About Creator & Publisher",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF1B1B1F)
                    )

                    // --- About Developer Card ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFFAF8FF)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF6750A4), Color(0xFF958DA5))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "PR",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Prince AR Abdur Rahman",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF1B1B1F)
                                    )
                                    Text(
                                        text = "Independent App Developer",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF6750A4)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Independent App Developer passionate about building modern Android applications, productivity tools, AI-powered experiences, media players, educational apps, and next-generation digital products.",
                                fontSize = 13.sp,
                                color = Color(0xFF49454F),
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = Color(0xFF1B1B1F).copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Get in Touch / Follow:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF1B1B1F)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Contact Actions Flow / Rows
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // WhatsApp 1
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFEADDFF))
                                        .clickable { uriHandler.openUri("https://wa.me/8801707424006") }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Call,
                                        contentDescription = "WhatsApp 1",
                                        tint = Color(0xFF21005D),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "WhatsApp: 01707424006",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF21005D)
                                    )
                                }

                                // WhatsApp 2
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFEADDFF))
                                        .clickable { uriHandler.openUri("https://wa.me/8801796951709") }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Call,
                                        contentDescription = "WhatsApp 2",
                                        tint = Color(0xFF21005D),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "WhatsApp: 01796951709",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF21005D)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Facebook Button
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFE8DEF8))
                                            .clickable { uriHandler.openUri("https://www.facebook.com/share/1BNn32qoJo/") }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Share,
                                            contentDescription = "Facebook",
                                            tint = Color(0xFF1D192B),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Facebook",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1D192B)
                                        )
                                    }

                                    // Instagram Button
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFCCE8E7))
                                            .clickable { uriHandler.openUri("https://www.instagram.com/ur___abdur____rahman__2008") }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Language,
                                            contentDescription = "Instagram",
                                            tint = Color(0xFF005049),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Instagram",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF005049)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- About Company Card ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFFAF8FF)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFD0E4FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Science,
                                        contentDescription = "NexVora",
                                        tint = Color(0xFF004A77),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "NexVora Lab's Ofc",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF1B1B1F)
                                    )
                                    Text(
                                        text = "Digital Innovation Hub",
                                        fontSize = 12.sp,
                                        color = Color(0xFF004A77)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "NexVora Lab's Ofc focuses on creating innovative Android applications designed to improve productivity, entertainment, learning, and digital experiences.",
                                fontSize = 13.sp,
                                color = Color(0xFF49454F),
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Mission:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF1B1B1F)
                            )
                            Text(
                                text = "Build fast, beautiful, privacy-friendly, and user-focused applications accessible to everyone.",
                                fontSize = 12.sp,
                                color = Color(0xFF49454F)
                            )
                        }
                    }

                    // --- Technical & Credits Card ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Technical Info",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF1D192B)
                                )
                                Text(
                                    text = "v1.0.0",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF6750A4)
                                )
                            }
                            
                            Divider(color = Color(0xFF1D192B).copy(alpha = 0.08f))

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Code, contentDescription = null, tint = Color(0xFF49454F), modifier = Modifier.size(12.dp))
                                    Text(
                                        text = "Developed by Prince AR Abdur Rahman",
                                        fontSize = 11.sp,
                                        color = Color(0xFF49454F)
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Public, contentDescription = null, tint = Color(0xFF49454F), modifier = Modifier.size(12.dp))
                                    Text(
                                        text = "Published by NexVora Lab's Ofc",
                                        fontSize = 11.sp,
                                        color = Color(0xFF49454F)
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Copyright, contentDescription = null, tint = Color(0xFF49454F), modifier = Modifier.size(12.dp))
                                    Text(
                                        text = "© 2026 NexVora Lab's Ofc. All Rights Reserved.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF49454F)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // THE CREATIVE WORKSPACE STUDIO
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Creative Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.1f)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .drawBehind {
                                // Draw checkerboard transparency grid pattern
                                val sizeDp = 10.dp.toPx()
                                for (x in 0 until (size.width / sizeDp).toInt() + 1) {
                                    for (y in 0 until (size.height / sizeDp).toInt() + 1) {
                                        val color = if ((x + y) % 2 == 0) Color(0xFFE2E8F0) else Color(0xFFFAF8FF)
                                        drawRect(
                                            color = color,
                                            topLeft = Offset(x * sizeDp, y * sizeDp),
                                            size = Size(sizeDp, sizeDp)
                                        )
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        if (activeToolMode == "manual_cutout" || activeToolMode == "object_eraser") {
                                            isDrawingMaskLine = true
                                            drawPoints.clear()
                                            drawPoints.add(offset)
                                        }
                                    },
                                    onDragEnd = {
                                        isDrawingMaskLine = false
                                        // Update actual mask bitmap
                                        if (maskBitmap != null && drawPoints.isNotEmpty()) {
                                            val mutableMask = maskBitmap!!.copy(Bitmap.Config.ARGB_8888, true)
                                            val canvas = android.graphics.Canvas(mutableMask)
                                            val paint = android.graphics.Paint().apply {
                                                color = if (isBrushErasingMode) android.graphics.Color.TRANSPARENT else android.graphics.Color.RED
                                                strokeWidth = brushRadius
                                                strokeCap = android.graphics.Paint.Cap.ROUND
                                                style = android.graphics.Paint.Style.STROKE
                                                xfermode = if (isBrushErasingMode) android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) else null
                                            }
                                            
                                            for (i in 0 until drawPoints.size - 1) {
                                                val p1 = drawPoints[i]
                                                val p2 = drawPoints[i + 1]
                                                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
                                            }
                                            viewModel.maskBitmap.value = mutableMask
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (activeToolMode == "manual_cutout" || activeToolMode == "object_eraser") {
                                            drawPoints.add(change.position)
                                        } else {
                                            // Handle transformation scaling/offset on the selected layer
                                            viewModel.updateSelectedLayer { layer ->
                                                layer.copy(
                                                    xOffset = layer.xOffset + dragAmount.x,
                                                    yOffset = layer.yOffset + dragAmount.y
                                                )
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Background Layer
                        val bgLayer = layersList.firstOrNull { it.type == "background" }
                        if (bgLayer != null && bgLayer.isVisible) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(android.graphics.Color.parseColor(bgLayer.colorHex)))
                            )
                        }

                        // Reflection
                        val subjectLayer = layersList.firstOrNull { it.type == "subject" }
                        if (subjectLayer != null && subjectLayer.isVisible && reflectionEnabled) {
                            editedBitmap?.let { bmp ->
                                Box(
                                    modifier = Modifier
                                        .size(300.dp)
                                        .offset(y = 150.dp)
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        alpha = reflectionO,
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }

                        // Subject Cutout Layer with shadow
                        if (subjectLayer != null && subjectLayer.isVisible) {
                            editedBitmap?.let { bmp ->
                                Box(
                                    modifier = Modifier
                                        .size(300.dp)
                                        .offset(
                                            x = subjectLayer.xOffset.dp,
                                            y = subjectLayer.yOffset.dp
                                        )
                                        .drawBehind {
                                            if (shadowEnabled) {
                                                // Soft Drop Shadow
                                                drawCircle(
                                                    color = Color.Black.copy(shadowO),
                                                    radius = 120f,
                                                    center = Offset(
                                                        size.width / 2 + shadowDx,
                                                        size.height / 2 + shadowDy
                                                    )
                                                )
                                            }
                                        }
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Cutout Subject",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        // Text Layers
                        layersList.filter { it.type == "text" }.forEach { tl ->
                            if (tl.isVisible) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = tl.xOffset.dp, y = tl.yOffset.dp)
                                        .clickable { viewModel.selectLayer(tl.id) }
                                ) {
                                    Text(
                                        text = tl.text,
                                        color = Color(android.graphics.Color.parseColor(tl.colorHex)),
                                        fontSize = (24 * tl.scale).sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = when (tl.fontStyle) {
                                            "Serif" -> FontFamily.Serif
                                            "Monospace" -> FontFamily.Monospace
                                            else -> FontFamily.Default
                                        },
                                        modifier = Modifier
                                            .border(
                                                width = if (selectedLayerId == tl.id) 2.dp else 0.dp,
                                                color = if (selectedLayerId == tl.id) Color(0xFF3B82F6) else Color.Transparent,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(6.dp)
                                    )
                                }
                            }
                        }

                        // Sticker Layers
                        layersList.filter { it.type == "sticker" }.forEach { sl ->
                            if (sl.isVisible) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = sl.xOffset.dp, y = sl.yOffset.dp)
                                        .clickable { viewModel.selectLayer(sl.id) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when (sl.stickerType.lowercase()) {
                                                    "sale" -> Color(0xFFEF4444)
                                                    "discount" -> Color(0xFFF59E0B)
                                                    "new" -> Color(0xFF10B981)
                                                    "birthday" -> Color(0xFFEC4899)
                                                    "love" -> Color(0xFFEC4899)
                                                    else -> Color(0xFF3B82F6)
                                                }
                                            )
                                            .border(
                                                width = if (selectedLayerId == sl.id) 2.dp else 0.dp,
                                                color = if (selectedLayerId == sl.id) Color.White else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = sl.stickerType.uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Brush gesture line drawing mask overlay
                        if (isDrawingMaskLine && drawPoints.isNotEmpty()) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                for (i in 0 until drawPoints.size - 1) {
                                    drawLine(
                                        color = if (isBrushErasingMode) Color.Black.copy(0.4f) else Color.Red.copy(0.6f),
                                        start = drawPoints[i],
                                        end = drawPoints[i + 1],
                                        strokeWidth = brushRadius,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }

                    // 2. Control Workshop Panel (Sliders, Actions, Tools selectors)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ) {
                        val panelTextColor = Color(0xFF1D192B)
                        val panelSubtextColor = Color(0xFF49454F)

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Sub-Tool Workspace based on selection
                            when (activeToolMode) {
                                "bg_remover" -> {
                                    Text("✂️ Background Remover Tools", fontWeight = FontWeight.Bold, color = panelTextColor)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = { viewModel.triggerAutoRemoveBG() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Auto Remove")
                                        }
                                        Button(
                                            onClick = { activeToolMode = "manual_cutout" },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = panelTextColor),
                                            border = BorderStroke(1.dp, Color(0xFFFAF8FF)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Manual Cutout")
                                        }
                                    }

                                    // Tolerance & Smoothness Sliders
                                    Text("Tolerance: ${tolerance.toInt()}", color = panelTextColor, fontSize = 12.sp)
                                    Slider(
                                        value = tolerance,
                                        onValueChange = { viewModel.toleranceSlider.value = it },
                                        valueRange = 5f..120f
                                    )
                                    Text("Edge Smoothness: ${smoothness.toInt()}", color = panelTextColor, fontSize = 12.sp)
                                    Slider(
                                        value = smoothness,
                                        onValueChange = { viewModel.smoothnessSlider.value = it },
                                        valueRange = 0f..100f
                                    )
                                }

                                "manual_cutout" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Manual Mask Brush", fontWeight = FontWeight.Bold, color = panelTextColor)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Erase Mode", color = panelTextColor, fontSize = 12.sp)
                                            Switch(
                                                checked = isBrushErasingMode,
                                                onCheckedChange = { isBrushErasingMode = it }
                                            )
                                        }
                                    }

                                    Text("Brush Radius: ${brushRadius.toInt()}", color = panelTextColor, fontSize = 12.sp)
                                    Slider(
                                        value = brushRadius,
                                        onValueChange = { brushRadius = it },
                                        valueRange = 10f..150f
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = { viewModel.triggerFeather() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Apply Feather")
                                        }
                                        Button(
                                            onClick = { activeToolMode = "bg_remover" },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = panelTextColor),
                                            border = BorderStroke(1.dp, Color(0xFFFAF8FF)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Back")
                                        }
                                    }
                                }

                                "bg_changer" -> {
                                    Text("🎨 Change Background", fontWeight = FontWeight.Bold, color = panelTextColor)
                                    Text("Select backdrop solid color", color = panelSubtextColor, fontSize = 12.sp)
                                    
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        val colorsList = listOf(
                                            "#FFFFFF" to "White",
                                            "#000000" to "Black",
                                            "#3B82F6" to "Blue",
                                            "#10B981" to "Green",
                                            "#EF4444" to "Red",
                                            "#F59E0B" to "Yellow",
                                            "#EC4899" to "Pink"
                                        )
                                        items(colorsList) { (hex, name) ->
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                                    .border(2.dp, Color.White, CircleShape)
                                                    .clickable {
                                                        viewModel.projectLayers.value = viewModel.projectLayers.value.map {
                                                            if (it.type == "background") it.copy(colorHex = hex) else it
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }

                                "object_eraser" -> {
                                    Text("🧹 Content-Aware Object Eraser", fontWeight = FontWeight.Bold, color = panelTextColor)
                                    Text("Brush over wires, logos, watermarks or blemishes, then tap Erase", color = panelSubtextColor, fontSize = 12.sp)
                                    
                                    Slider(
                                        value = brushRadius,
                                        onValueChange = { brushRadius = it },
                                        valueRange = 10f..150f
                                    )

                                    Button(
                                        onClick = { viewModel.triggerObjectErase() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF601410)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.Clear, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Erase Selected Object")
                                    }
                                }

                                "portrait" -> {
                                    Text("👤 Portrait Studio Retouching", fontWeight = FontWeight.Bold, color = panelTextColor)
                                    
                                    Text("Skin Smoothness: ${faceSmooth.toInt()}", color = panelTextColor, fontSize = 12.sp)
                                    Slider(
                                        value = faceSmooth,
                                        onValueChange = { viewModel.faceSmoothSlider.value = it },
                                        valueRange = 0f..100f,
                                        onValueChangeFinished = { viewModel.triggerPortraitFilters() }
                                    )

                                    Text("Teeth Whitening: ${teethWhiten.toInt()}", color = panelTextColor, fontSize = 12.sp)
                                    Slider(
                                        value = teethWhiten,
                                        onValueChange = { viewModel.teethWhitenSlider.value = it },
                                        valueRange = 0f..100f,
                                        onValueChangeFinished = { viewModel.triggerPortraitFilters() }
                                    )
                                }

                                "product" -> {
                                    Text("🛍 Product Photo Studio Controls", fontWeight = FontWeight.Bold, color = panelTextColor)
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Soft Drop Shadow", color = panelTextColor)
                                        Switch(checked = shadowEnabled, onCheckedChange = { viewModel.isShadowEnabled.value = it })
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Water Surface Reflection", color = panelTextColor)
                                        Switch(checked = reflectionEnabled, onCheckedChange = { viewModel.isReflectionEnabled.value = it })
                                    }
                                }

                                "id_photo" -> {
                                    Text("🪪 ID Photo Maker Templates", fontWeight = FontWeight.Bold, color = panelTextColor)
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.projectLayers.value = viewModel.projectLayers.value.map {
                                                    if (it.type == "background") it.copy(colorHex = "#3B82F6") else it
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004A77)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Blue Backdrop")
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.projectLayers.value = viewModel.projectLayers.value.map {
                                                    if (it.type == "background") it.copy(colorHex = "#EF4444") else it
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF601410)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Red Backdrop")
                                        }
                                    }
                                    
                                    Button(
                                        onClick = {
                                            Toast.makeText(context, "Printed standard Passport layout grid!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = panelTextColor),
                                        border = BorderStroke(1.dp, Color(0xFFE8DEF8)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Select Passport Preset (35x45mm)")
                                    }
                                }

                                "enhancer" -> {
                                    Text("✨ Real-time Photo Enhancements", fontWeight = FontWeight.Bold, color = panelTextColor)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("Brightness: ${brVal.toInt()}", color = panelTextColor, fontSize = 11.sp)
                                        Slider(value = brVal, onValueChange = { viewModel.enhanceBrightness.value = it }, valueRange = -100f..100f, onValueChangeFinished = { viewModel.triggerEnhancements() })
                                        
                                        Text("Contrast: ${coVal.toInt()}", color = panelTextColor, fontSize = 11.sp)
                                        Slider(value = coVal, onValueChange = { viewModel.enhanceContrast.value = it }, valueRange = -100f..100f, onValueChangeFinished = { viewModel.triggerEnhancements() })

                                        Text("Saturation: ${saVal.toInt()}", color = panelTextColor, fontSize = 11.sp)
                                        Slider(value = saVal, onValueChange = { viewModel.enhanceSaturation.value = it }, valueRange = -100f..100f, onValueChangeFinished = { viewModel.triggerEnhancements() })
                                    }
                                }

                                "text_sticker" -> {
                                    Text("📝 Add Text or Badges", fontWeight = FontWeight.Bold, color = panelTextColor)
                                    OutlinedTextField(
                                        value = customTextInput,
                                        onValueChange = { customTextInput = it },
                                        placeholder = { Text("Enter text...") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF6750A4),
                                            unfocusedBorderColor = Color.Gray,
                                            focusedTextColor = panelTextColor,
                                            unfocusedTextColor = panelTextColor
                                        )
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                if (customTextInput.isNotEmpty()) {
                                                    viewModel.addTextLayer(customTextInput)
                                                    customTextInput = ""
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Add Text")
                                        }
                                        Button(
                                            onClick = { viewModel.addStickerLayer("SALE") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF601410)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("+ Sale Badge")
                                        }
                                    }
                                }

                                "layers" -> {
                                    Text("📂 Active Layers Panel", fontWeight = FontWeight.Bold, color = panelTextColor)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(layersList) { layer ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (selectedLayerId == layer.id) Color(0xFFE8DEF8) else Color.White)
                                                    .clickable { viewModel.selectLayer(layer.id) }
                                                    .padding(10.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(layer.name, color = panelTextColor, fontSize = 12.sp)
                                                    IconButton(onClick = { viewModel.removeLayer(layer.id) }) {
                                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Layer", tint = Color.Red.copy(0.7f), modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                else -> {
                                    // Categories scrollable select row
                                    Text("Studio Instruments Toolset", fontWeight = FontWeight.Bold, color = panelTextColor)
                                    Text("Choose a tool to begin professional manipulation", color = panelSubtextColor, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Main bottom navigation toolbar
                            Divider(color = panelTextColor.copy(0.12f))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val toolsList = listOf(
                                    "bg_remover" to Pair(Icons.Filled.ContentCut, "BG Eraser"),
                                    "bg_changer" to Pair(Icons.Filled.ColorLens, "BG Changer"),
                                    "object_eraser" to Pair(Icons.Filled.AutoFixHigh, "Object Eraser"),
                                    "portrait" to Pair(Icons.Filled.Face, "Retouch Portrait"),
                                    "product" to Pair(Icons.Filled.ShoppingBag, "Product Photo"),
                                    "id_photo" to Pair(Icons.Filled.ContactPage, "ID Maker"),
                                    "enhancer" to Pair(Icons.Filled.Settings, "Enhancer"),
                                    "text_sticker" to Pair(Icons.Filled.Title, "Add Text"),
                                    "layers" to Pair(Icons.Filled.Layers, "Layers")
                                )

                                items(toolsList) { (mode, detail) ->
                                    val (icon, label) = detail
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable { activeToolMode = mode }
                                            .padding(horizontal = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (activeToolMode == mode) Color(0xFFE8DEF8) else Color.White),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(icon, contentDescription = label, tint = if (activeToolMode == mode) Color(0xFF1D192B) else Color(0xFF49454F), modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(label, fontSize = 10.sp, color = if (activeToolMode == mode) Color(0xFF1D192B) else Color(0xFF49454F), fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
