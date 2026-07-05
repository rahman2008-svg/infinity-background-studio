package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Typeface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.*
import com.example.utils.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build
import android.os.Environment
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

data class EditLayer(
    val id: String = UUID.randomUUID().toString(),
    val type: String, // "subject", "background", "shadow", "text", "sticker"
    val name: String,
    val isVisible: Boolean = true,
    val opacity: Float = 1.0f,
    val xOffset: Float = 0f,
    val yOffset: Float = 0f,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val text: String = "",
    val fontStyle: String = "Normal", // Normal, Bold, Serif, Monospace
    val colorHex: String = "#FFFFFF",
    val outlineColorHex: String = "#000000",
    val outlineWidth: Float = 0f,
    val shadowColorHex: String = "#000000",
    val shadowRadius: Float = 0f,
    val shadowDx: Float = 0f,
    val shadowDy: Float = 0f,
    val isCurved: Boolean = false,
    val curveAngle: Float = 0f, // degree of curve
    val stickerType: String = "" // sale, discount, emoji, etc.
)

sealed interface EditorUiState {
    object Idle : EditorUiState
    object Loading : EditorUiState
    data class Success(val message: String) : EditorUiState
    data class Error(val error: String) : EditorUiState
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = ProjectRepository(database.projectDao())

    // Exposed DB States
    val activeProjects: StateFlow<List<Project>> = repository.activeProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedProjects: StateFlow<List<Project>> = repository.archivedProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashProjects: StateFlow<List<Project>> = repository.trashProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<Folder>> = repository.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI State
    val uiState = MutableStateFlow<EditorUiState>(EditorUiState.Idle)

    // Editing Project State
    val currentProject = MutableStateFlow<Project?>(null)
    val projectLayers = MutableStateFlow<List<EditLayer>>(emptyList())
    val selectedLayerId = MutableStateFlow<String?>(null)

    // Current interactive bitmaps (on thread)
    val originalBitmap = MutableStateFlow<Bitmap?>(null)
    val editedBitmap = MutableStateFlow<Bitmap?>(null) // Masked/Processed foreground
    val maskBitmap = MutableStateFlow<Bitmap?>(null) // Painted mask for eraser

    // Sliders & settings
    val toleranceSlider = MutableStateFlow(40f)
    val smoothnessSlider = MutableStateFlow(20f)
    val featherSlider = MutableStateFlow(0f)
    val edgeExpandSlider = MutableStateFlow(0f)

    val faceSmoothSlider = MutableStateFlow(0f)
    val teethWhitenSlider = MutableStateFlow(0f)

    val enhanceBrightness = MutableStateFlow(0f)
    val enhanceContrast = MutableStateFlow(0f)
    val enhanceSaturation = MutableStateFlow(0f)
    val enhanceSharpen = MutableStateFlow(0f)

    // Shadow Generator
    val isShadowEnabled = MutableStateFlow(false)
    val shadowOffsetDx = MutableStateFlow(15f)
    val shadowOffsetDy = MutableStateFlow(15f)
    val shadowBlur = MutableStateFlow(15f)
    val shadowOpacity = MutableStateFlow(0.5f)

    // Reflection Studio
    val isReflectionEnabled = MutableStateFlow(false)
    val reflectionLength = MutableStateFlow(40f) // height percentage of reflection
    val reflectionOpacity = MutableStateFlow(0.3f)

    // Canvas properties
    val canvasRatioWidth = MutableStateFlow(1)
    val canvasRatioHeight = MutableStateFlow(1)

    // Undo/Redo stack (state snapshots)
    private val undoStack = mutableListOf<List<EditLayer>>()
    private val redoStack = mutableListOf<List<EditLayer>>()

    init {
        // Default folders
        viewModelScope.launch {
            repository.folders.collect { list ->
                if (list.isEmpty()) {
                    repository.insertFolder(Folder(name = "Personal"))
                    repository.insertFolder(Folder(name = "E-Commerce"))
                    repository.insertFolder(Folder(name = "ID Photos"))
                }
            }
        }
    }

    fun pushToUndo() {
        undoStack.add(projectLayers.value.toList())
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val last = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(projectLayers.value.toList())
            projectLayers.value = last
            triggerAutoSave()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(projectLayers.value.toList())
            projectLayers.value = next
            triggerAutoSave()
        }
    }

    // Load template image as Bitmap
    fun selectTemplate(resId: Int, categoryName: String) {
        viewModelScope.launch {
            uiState.value = EditorUiState.Loading
            try {
                val context = getApplication<Application>().applicationContext
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                }
                val bitmap = BitmapFactory.decodeResource(context.resources, resId, options)
                originalBitmap.value = bitmap
                editedBitmap.value = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                
                // Empty mask
                val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                maskBitmap.value = mask

                // Setup default layers
                val defaultLayers = listOf(
                    EditLayer(type = "background", name = "Background Backdrop"),
                    EditLayer(type = "subject", name = "Cutout Subject", scale = 1.0f)
                )
                projectLayers.value = defaultLayers
                selectedLayerId.value = defaultLayers[1].id // select Subject

                // Reset all editing slider parameters
                resetParameters()

                // Create database project
                val proj = Project(
                    name = "Studio Project ${System.currentTimeMillis() % 10000}",
                    category = categoryName,
                    sampleImageResId = resId,
                    layersJson = "[]"
                )
                val id = repository.insertProject(proj)
                currentProject.value = proj.copy(id = id.toInt())

                uiState.value = EditorUiState.Success("Template loaded successfully")
            } catch (e: Exception) {
                uiState.value = EditorUiState.Error(e.localizedMessage ?: "Failed to load template")
            }
        }
    }

    fun selectImageFromUri(uri: Uri) {
        viewModelScope.launch {
            uiState.value = EditorUiState.Loading
            try {
                val context = getApplication<Application>().applicationContext
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                }
                
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close()

                if (bitmap == null) {
                    throw Exception("Unable to decode chosen file.")
                }

                originalBitmap.value = bitmap
                editedBitmap.value = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                
                // Empty mask
                val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                maskBitmap.value = mask

                // Setup default layers
                val defaultLayers = listOf(
                    EditLayer(type = "background", name = "Background Backdrop"),
                    EditLayer(type = "subject", name = "Cutout Subject", scale = 1.0f)
                )
                projectLayers.value = defaultLayers
                selectedLayerId.value = defaultLayers[1].id // select Subject

                // Reset all editing slider parameters
                resetParameters()

                // Create database project
                val proj = Project(
                    name = "Imported Project ${System.currentTimeMillis() % 10000}",
                    category = "Imported",
                    sampleImageResId = null,
                    layersJson = "[]"
                )
                val id = repository.insertProject(proj)
                currentProject.value = proj.copy(id = id.toInt())

                uiState.value = EditorUiState.Success("Image imported successfully")
            } catch (e: Exception) {
                uiState.value = EditorUiState.Error(e.localizedMessage ?: "Failed to import image")
            }
        }
    }

    fun saveBitmapToGallery(bitmap: Bitmap, context: android.content.Context, filename: String = "InfinityStudio_${System.currentTimeMillis()}.png"): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/InfinityStudio")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imageUri != null) {
            try {
                resolver.openOutputStream(imageUri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                return imageUri
            } catch (e: Exception) {
                resolver.delete(imageUri, null, null)
                e.printStackTrace()
            }
        }
        return null
    }

    fun resetParameters() {
        toleranceSlider.value = 40f
        smoothnessSlider.value = 20f
        featherSlider.value = 0f
        edgeExpandSlider.value = 0f
        faceSmoothSlider.value = 0f
        teethWhitenSlider.value = 0f
        enhanceBrightness.value = 0f
        enhanceContrast.value = 0f
        enhanceSaturation.value = 0f
        enhanceSharpen.value = 0f
        isShadowEnabled.value = false
        isReflectionEnabled.value = false
    }

    // Trigger local rule-based background removal
    fun triggerAutoRemoveBG(targetColor: Int? = null) {
        val orig = originalBitmap.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            uiState.value = EditorUiState.Loading
            val filtered = ImageProcessor.removeBackground(
                orig,
                toleranceSlider.value,
                smoothnessSlider.value,
                targetColor
            )
            editedBitmap.value = filtered
            uiState.value = EditorUiState.Success("Auto removed background offline")
            triggerAutoSave()
        }
    }

    // Trigger mask feathering
    fun triggerFeather() {
        val current = editedBitmap.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val feathered = ImageProcessor.featherAlpha(current, featherSlider.value.toInt())
            editedBitmap.value = feathered
            triggerAutoSave()
        }
    }

    // Trigger Object Eraser (Inpaint on mask)
    fun triggerObjectErase() {
        val orig = editedBitmap.value ?: return
        val mask = maskBitmap.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            uiState.value = EditorUiState.Loading
            val result = ImageProcessor.inpaint(orig, mask)
            editedBitmap.value = result
            
            // Clear mask
            val newMask = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
            maskBitmap.value = newMask
            
            uiState.value = EditorUiState.Success("Object erased offline")
            triggerAutoSave()
        }
    }

    // Trigger Portrait modifications
    fun triggerPortraitFilters() {
        val current = originalBitmap.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            uiState.value = EditorUiState.Loading
            var result = current
            if (faceSmoothSlider.value > 0) {
                result = ImageProcessor.faceSmooth(result, faceSmoothSlider.value)
            }
            if (teethWhitenSlider.value > 0) {
                result = ImageProcessor.teethWhitening(result, teethWhitenSlider.value)
            }
            editedBitmap.value = result
            uiState.value = EditorUiState.Success("Portrait retouched offline")
            triggerAutoSave()
        }
    }

    // Color Replace
    fun triggerColorReplace(source: Int, dest: Int, tolerance: Float) {
        val current = editedBitmap.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val result = ImageProcessor.replaceColor(current, source, dest, tolerance)
            editedBitmap.value = result
            triggerAutoSave()
        }
    }

    // Enhance Adjustments (Brightness, Contrast, Saturation, Sharpness)
    fun triggerEnhancements() {
        val current = originalBitmap.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val result = ImageProcessor.enhancePhoto(
                current,
                enhanceBrightness.value,
                enhanceContrast.value,
                enhanceSaturation.value,
                enhanceSharpen.value
            )
            editedBitmap.value = result
        }
    }

    // Add dynamic layers
    fun addTextLayer(text: String) {
        pushToUndo()
        val layer = EditLayer(
            type = "text",
            name = "Text Layer: '$text'",
            text = text,
            colorHex = "#3B82F6",
            xOffset = 50f,
            yOffset = 100f,
            scale = 1.2f
        )
        projectLayers.value = projectLayers.value + layer
        selectedLayerId.value = layer.id
        triggerAutoSave()
    }

    fun addStickerLayer(stickerType: String) {
        pushToUndo()
        val layer = EditLayer(
            type = "sticker",
            name = "Sticker: $stickerType",
            stickerType = stickerType,
            scale = 1.0f,
            xOffset = -50f,
            yOffset = -50f
        )
        projectLayers.value = projectLayers.value + layer
        selectedLayerId.value = layer.id
        triggerAutoSave()
    }

    fun removeLayer(layerId: String) {
        pushToUndo()
        projectLayers.value = projectLayers.value.filter { it.id != layerId }
        if (selectedLayerId.value == layerId) {
            selectedLayerId.value = projectLayers.value.firstOrNull()?.id
        }
        triggerAutoSave()
    }

    fun selectLayer(layerId: String) {
        selectedLayerId.value = layerId
    }

    fun updateSelectedLayer(updater: (EditLayer) -> EditLayer) {
        val currentSelected = selectedLayerId.value ?: return
        projectLayers.value = projectLayers.value.map {
            if (it.id == currentSelected) updater(it) else it
        }
        triggerAutoSave()
    }

    // Database Actions
    fun createFolder(name: String) {
        viewModelScope.launch {
            repository.insertFolder(Folder(name = name))
        }
    }

    fun archiveProject(project: Project, archive: Boolean) {
        viewModelScope.launch {
            repository.updateProject(project.copy(isArchived = archive, updatedAt = System.currentTimeMillis()))
        }
    }

    fun trashProject(project: Project, trash: Boolean) {
        viewModelScope.launch {
            repository.updateProject(project.copy(isInTrash = trash, updatedAt = System.currentTimeMillis()))
        }
    }

    fun duplicateProject(project: Project) {
        viewModelScope.launch {
            val dup = project.copy(
                id = 0,
                name = "${project.name} (Copy)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.insertProject(dup)
        }
    }

    fun deleteProjectPermanently(project: Project) {
        viewModelScope.launch {
            repository.deleteProject(project)
        }
    }

    fun loadProject(project: Project) {
        viewModelScope.launch {
            uiState.value = EditorUiState.Loading
            currentProject.value = project
            
            // Load bitmap based on res id
            val context = getApplication<Application>().applicationContext
            val resId = project.sampleImageResId ?: R.drawable.img_sample_portrait
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }
            try {
                val bitmap = BitmapFactory.decodeResource(context.resources, resId, options)
                originalBitmap.value = bitmap
                editedBitmap.value = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                maskBitmap.value = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

                // Try to deserialize layers (or fallback)
                val layersList = mutableListOf<EditLayer>()
                layersList.add(EditLayer(type = "background", name = "Background Backdrop", colorHex = project.bgColorHex))
                layersList.add(EditLayer(type = "subject", name = "Cutout Subject"))
                
                projectLayers.value = layersList
                selectedLayerId.value = layersList[1].id
                
                resetParameters()
                uiState.value = EditorUiState.Success("Project loaded successfully")
            } catch (e: Exception) {
                uiState.value = EditorUiState.Error(e.localizedMessage ?: "Failed to load project image")
            }
        }
    }

    // Auto save triggers on layer modifications or parameters adjustments
    private fun triggerAutoSave() {
        val proj = currentProject.value ?: return
        val layersList = projectLayers.value
        viewModelScope.launch {
            val updated = proj.copy(
                bgColorHex = layersList.firstOrNull { it.type == "background" }?.colorHex ?: "#FFFFFF",
                updatedAt = System.currentTimeMillis()
            )
            repository.updateProject(updated)
            
            // Save local version history periodically
            if (System.currentTimeMillis() % 10 == 0L) {
                repository.insertVersion(
                    VersionHistory(
                        projectId = proj.id,
                        name = "Auto Save Point",
                        layersJson = ""
                    )
                )
            }
        }
    }

    // Export output high-res rendering canvas
    suspend fun renderFinalCanvas(): Bitmap = withContext(Dispatchers.Default) {
        val fg = editedBitmap.value ?: Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val layersList = projectLayers.value

        val exportWidth = fg.width
        val exportHeight = fg.height
        val output = Bitmap.createBitmap(exportWidth, exportHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Draw Background layer
        val bgLayer = layersList.firstOrNull { it.type == "background" }
        if (bgLayer != null && bgLayer.isVisible) {
            paint.color = Color.parseColor(bgLayer.colorHex)
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, exportWidth.toFloat(), exportHeight.toFloat(), paint)
        } else {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }

        // 2. Draw Shadow under subject if enabled
        val subjectLayer = layersList.firstOrNull { it.type == "subject" }
        if (subjectLayer != null && subjectLayer.isVisible) {
            if (isShadowEnabled.value) {
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = PorterDuffColorFilter(
                        Color.argb((shadowOpacity.value * 255).toInt(), 0, 0, 0),
                        PorterDuff.Mode.SRC_IN
                    )
                    maskFilter = BlurMaskFilter(shadowBlur.value.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                }
                
                canvas.save()
                canvas.translate(
                    subjectLayer.xOffset + shadowOffsetDx.value,
                    subjectLayer.yOffset + shadowOffsetDy.value
                )
                canvas.scale(subjectLayer.scale, subjectLayer.scale, exportWidth / 2f, exportHeight / 2f)
                canvas.rotate(subjectLayer.rotation, exportWidth / 2f, exportHeight / 2f)
                canvas.drawBitmap(fg, 0f, 0f, shadowPaint)
                canvas.restore()
            }

            // Draw Reflection if enabled (rendered below product/subject)
            if (isReflectionEnabled.value) {
                val matrix = Matrix().apply {
                    preScale(1f, -1f)
                }
                val reflectedBitmap = Bitmap.createBitmap(fg, 0, 0, fg.width, fg.height, matrix, true)
                val reflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    alpha = (reflectionOpacity.value * 255).toInt()
                }
                
                canvas.save()
                canvas.translate(subjectLayer.xOffset, subjectLayer.yOffset + fg.height)
                canvas.scale(subjectLayer.scale, subjectLayer.scale, exportWidth / 2f, exportHeight / 2f)
                canvas.drawBitmap(reflectedBitmap, 0f, 0f, reflectionPaint)
                canvas.restore()
            }

            // 3. Draw CUTOUT FOREGROUND
            val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (subjectLayer.opacity * 255).toInt()
            }
            canvas.save()
            canvas.translate(subjectLayer.xOffset, subjectLayer.yOffset)
            canvas.scale(subjectLayer.scale, subjectLayer.scale, exportWidth / 2f, exportHeight / 2f)
            canvas.rotate(subjectLayer.rotation, exportWidth / 2f, exportHeight / 2f)
            canvas.drawBitmap(fg, 0f, 0f, fgPaint)
            canvas.restore()
        }

        // 4. Draw Custom Text layers
        layersList.filter { it.type == "text" }.forEach { tl ->
            if (tl.isVisible) {
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(tl.colorHex)
                    textSize = 48f * tl.scale
                    style = Paint.Style.FILL
                    textAlign = Paint.Align.CENTER
                    typeface = when (tl.fontStyle) {
                        "Bold" -> Typeface.DEFAULT_BOLD
                        "Serif" -> Typeface.SERIF
                        "Monospace" -> Typeface.MONOSPACE
                        else -> Typeface.DEFAULT
                    }
                }
                
                canvas.save()
                canvas.translate(tl.xOffset, tl.yOffset)
                canvas.rotate(tl.rotation)
                
                // Drop shadow
                if (tl.shadowRadius > 0) {
                    textPaint.setShadowLayer(tl.shadowRadius, tl.shadowDx, tl.shadowDy, Color.parseColor(tl.shadowColorHex))
                }
                
                canvas.drawText(tl.text, 0f, 0f, textPaint)
                
                // Outline if specified
                if (tl.outlineWidth > 0) {
                    val outlinePaint = Paint(textPaint).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = tl.outlineWidth
                        color = Color.parseColor(tl.outlineColorHex)
                        clearShadowLayer()
                    }
                    canvas.drawText(tl.text, 0f, 0f, outlinePaint)
                }
                
                canvas.restore()
            }
        }

        // 5. Draw Stickers layers
        layersList.filter { it.type == "sticker" }.forEach { sl ->
            if (sl.isVisible) {
                val stickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                }
                canvas.save()
                canvas.translate(sl.xOffset, sl.yOffset)
                canvas.scale(sl.scale, sl.scale)
                canvas.rotate(sl.rotation)
                
                // Draw sticker badge
                val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = when (sl.stickerType.lowercase()) {
                        "sale" -> Color.parseColor("#EF4444")
                        "discount" -> Color.parseColor("#F59E0B")
                        "new" -> Color.parseColor("#10B981")
                        "birthday" -> Color.parseColor("#EC4899")
                        "love" -> Color.parseColor("#EC4899")
                        else -> Color.parseColor("#3B82F6")
                    }
                    style = Paint.Style.FILL
                }
                
                canvas.drawRoundRect(-120f, -40f, 120f, 40f, 20f, 20f, rectPaint)
                
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 28f
                    typeface = Typeface.DEFAULT_BOLD
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(sl.stickerType.uppercase(), 0f, 10f, textPaint)
                canvas.restore()
            }
        }

        output
    }
}
