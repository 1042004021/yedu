package com.taverndream.yedu

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.taverndream.yedu.data.GalgameHistoryEntry
import com.taverndream.yedu.data.GalgameImagePlanner
import com.taverndream.yedu.data.GalgameImageSpec
import com.taverndream.yedu.data.GalgameJson
import com.taverndream.yedu.data.GalgameSave
import com.taverndream.yedu.data.GalgameScript
import com.taverndream.yedu.data.GalgameScriptCodec
import com.taverndream.yedu.data.GalgameScriptNode
import com.taverndream.yedu.data.GalgameSettings
import com.taverndream.yedu.data.GalgameState
import com.taverndream.yedu.data.withEffects
import com.taverndream.yedu.image.GalgameImageGenerator
import com.taverndream.yedu.sd.SdBackendService
import com.taverndream.yedu.sd.SdModel
import com.taverndream.yedu.sd.SdModelManager
import com.taverndream.yedu.sd.UpscalerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** 剧本库中的一条记录；每个条目对应一个独立目录、存档和图片目录。 */
data class GalgameLibraryEntry(
    val key: String,
    val title: String,
    val nodeCount: Int,
    val imageCount: Int,
    val hasSave: Boolean,
    val currentNodeTitle: String,
    val lastPlayedAt: Long,
    val script: GalgameScript,
)

/** 剧情拓扑中的一条出边。 */
data class GalgameGraphEdge(
    val label: String,
    val targetId: String,
    val targetTitle: String,
)

/** 剧情拓扑节点。 */
data class GalgameGraphNode(
    val id: String,
    val title: String,
    val depth: Int,
    val ending: Boolean,
    val current: Boolean,
    val visited: Boolean,
    val imageCount: Int,
    val edges: List<GalgameGraphEdge>,
)

/** 画廊条目：未生成时 path 为空。 */
data class GalgameGalleryItem(
    val id: String,
    val prompt: String,
    val kind: String,
    val path: String?,
)

/** 玩家当前看到的一行；旁白和台词统一成同一种展示模型。 */
data class DisplayLine(
    val speaker: String,
    val text: String,
    val narration: Boolean,
    val imageId: String,
)

/**
 * 独立 Galgame APK 的运行时。
 *
 * 只做三件事：读 JSON、走节点、按 JSON 的图片清单后台生图。
 * 不依赖世界书、角色卡、LLM 对话或 Room。
 */
class GalgameViewModel(app: Application) : AndroidViewModel(app) {

    val settings = GalgameSettings(app)

    private val rootDir = File(app.filesDir, "galgame_json").apply { mkdirs() }
    private val scriptsDir = File(rootDir, "scripts").apply { mkdirs() }
    private val legacyImageRoot = File(rootDir, "images")
    private val lastScriptFile = File(rootDir, "last_script.txt")
    private val imageGenerator = GalgameImageGenerator(app, settings)
    private val imageMutex = Mutex()

    var scriptTitle by mutableStateOf("Galgame")
        private set
    var nodeCount by mutableStateOf(0)
        private set
    var imageCount by mutableStateOf(0)
        private set
    var hasScript by mutableStateOf(false)
        private set
    var hasSave by mutableStateOf(false)
        private set
    var library by mutableStateOf<List<GalgameLibraryEntry>>(emptyList())
        private set
    var currentScriptKey by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var toast by mutableStateOf<String?>(null)
    var importSerial by mutableStateOf(0)
        private set

    var currentNode by mutableStateOf<GalgameScriptNode?>(null)
        private set
    var displayLines by mutableStateOf<List<DisplayLine>>(emptyList())
        private set
    var lineIndex by mutableStateOf(0)
        private set
    var history by mutableStateOf<List<GalgameHistoryEntry>>(emptyList())
        private set
    var state by mutableStateOf(GalgameState())
        private set
    var isEnding by mutableStateOf(false)
        private set
    var backgroundPath by mutableStateOf<String?>(null)
        private set
    var backgroundKind by mutableStateOf("scene")
        private set
    var currentImageId by mutableStateOf<String?>(null)
        private set

    var imageProgress by mutableStateOf<String?>(null)
        private set
    var imageError by mutableStateOf<String?>(null)
        private set
    var imageBusy by mutableStateOf(false)
        private set
    var modelRevision by mutableStateOf(0)
        private set
    var upscalerRevision by mutableStateOf(0)
        private set
    var imageRevision by mutableStateOf(0)
        private set

    private var script: GalgameScript? = null
    private var currentScriptFolder: File? = null
    private var currentScriptFile: File? = null
    private var currentSaveFile: File? = null
    private var scriptImageDir: File? = null
    private var imagePaths: MutableMap<String, String> = mutableMapOf()
    private var imageJob: Job? = null
    private var imageGeneration = 0

    val hasScripts: Boolean
        get() = library.isNotEmpty()

    val graphNodes: List<GalgameGraphNode>
        get() {
            val s = script ?: return emptyList()
            val byId = s.nodes.associateBy { it.id }
            val depth = mutableMapOf<String, Int>()
            val queue = ArrayDeque<String>()
            if (s.start.isNotBlank()) {
                queue.add(s.start)
                depth[s.start] = 0
            }
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                val node = byId[id] ?: continue
                val nextDepth = (depth[id] ?: 0) + 1
                (node.choices.map { it.next } + node.next)
                    .filter { it.isNotBlank() }
                    .forEach { target ->
                        val old = depth[target]
                        if (old == null || nextDepth < old) {
                            depth[target] = nextDepth
                            queue.add(target)
                        }
                    }
            }
            val visited = history.map { it.nodeId }.toSet()
            val currentId = currentNode?.id
            val order = s.nodes.withIndex().associate { it.value.id to it.index }
            return s.nodes.map { node ->
                val edges = buildList {
                    if (node.next.isNotBlank()) {
                        add(
                            GalgameGraphEdge(
                                label = "继续",
                                targetId = node.next,
                                targetTitle = byId[node.next]?.title.orEmpty(),
                            )
                        )
                    }
                    node.choices.forEach { choice ->
                        add(
                            GalgameGraphEdge(
                                label = choice.text,
                                targetId = choice.next,
                                targetTitle = byId[choice.next]?.title.orEmpty(),
                            )
                        )
                    }
                }
                GalgameGraphNode(
                    id = node.id,
                    title = node.title.ifBlank { node.id },
                    depth = depth[node.id] ?: Int.MAX_VALUE,
                    ending = node.ending,
                    current = node.id == currentId,
                    visited = node.id in visited,
                    imageCount = buildSet {
                        if (node.image.isNotBlank()) add(node.image)
                        node.lines.forEach { if (it.image.isNotBlank()) add(it.image) }
                    }.size,
                    edges = edges,
                )
            }.sortedWith(compareBy({ it.depth }, { order[it.id] ?: Int.MAX_VALUE }))
        }

    val galleryItems: List<GalgameGalleryItem>
        get() {
            // 订阅生成进度，画廊里的灰图会在图片落盘后自动变成可查看。
            imageRevision
            val specs = script?.images.orEmpty()
            return specs.map { spec ->
                val path = imagePaths[spec.id]?.takeIf { File(it).isFile }
                GalgameGalleryItem(spec.id, spec.prompt, spec.kind, path)
            }
        }

    val atEnd: Boolean
        get() = displayLines.isEmpty() || lineIndex >= displayLines.lastIndex

    val choices: List<com.taverndream.yedu.data.GalgameChoice>
        get() = if (atEnd && !isEnding) currentNode?.choices.orEmpty() else emptyList()

    fun load() {
        if (loading) return
        viewModelScope.launch {
            loading = true
            error = null
            try {
                loadFromDisk()
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                error = "剧本读取失败：${t.message}"
            } finally {
                loading = false
            }
        }
    }

    fun importScript(uri: Uri) {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val raw = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: throw IllegalStateException("无法读取文件")
                }
                val result = GalgameScriptCodec.validate(raw)
                if (!result.ok || result.script == null) {
                    error = "剧本校验失败：\n" + result.errors.joinToString("\n").take(1600)
                    return@launch
                }
                imageJob?.cancel()
                val folder = withContext(Dispatchers.IO) {
                    val target = newScriptFolder(result.script)
                    File(target, "script.json").writeText(raw)
                    target
                }
                currentScriptKey = folder.name
                runCatching { lastScriptFile.writeText(folder.name) }
                refreshLibrary()
                applyScript(result.script, folder, restore = false)
                importSerial++
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                error = "剧本导入失败：${t.message}"
            } finally {
                loading = false
            }
        }
    }

    fun startNewGame() {
        val s = script ?: run {
            toast = "请先导入 JSON 剧本"
            return
        }
        state = GalgameState()
        history = emptyList()
        isEnding = false
        showNode(s.start, null, addHistory = true)
    }

    fun advance() {
        val node = currentNode ?: return
        if (lineIndex < displayLines.lastIndex) {
            val previousImage = currentImageId
            lineIndex++
            renderCurrent()
            saveState()
            if (currentImageId != previousImage && currentImageId != null) startImageWorker()
            return
        }
        if (node.ending || node.choices.isNotEmpty()) return
        if (node.next.isBlank()) return
        showNode(node.next, "继续", addHistory = true)
    }

    fun choose(choiceId: String) {
        val node = currentNode ?: return
        val choice = node.choices.firstOrNull { it.id == choiceId } ?: return
        state = state.withEffects(choice.effects)
        val next = choice.next.ifBlank { node.next }
        if (next.isBlank()) {
            toast = "这个选项没有配置 next"
            return
        }
        showNode(next, choice.text, addHistory = true)
    }

    fun retry() {
        error = null
        load()
    }

    fun retryImage() {
        val id = currentImageId ?: return
        imagePaths.remove(id)
        imageRevision++
        renderCurrent()
        startImageWorker()
    }

    fun startSdEngine(modelId: String = settings.sdModelId) {
        SdBackendService.start(getApplication(), modelId)
    }

    fun stopSdEngine() {
        SdBackendService.stop(getApplication())
    }

    fun downloadSdModel(modelId: String) {
        val model = SdModelManager.models(getApplication()).firstOrNull { it.id == modelId } ?: return
        viewModelScope.launch {
            SdModelManager.download(
                context = getApplication(),
                model = model,
                mirror = settings.hfMirror,
                threads = settings.sdDownloadThreads,
            )
            modelRevision++
        }
    }

    fun deleteSdModel(model: SdModel) {
        viewModelScope.launch {
            runCatching { SdModelManager.delete(getApplication(), model) }
                .onSuccess { modelRevision++ }
                .onFailure { toast = "删除失败：${it.message}" }
        }
    }

    fun downloadUpscaler() {
        viewModelScope.launch {
            UpscalerManager.download(getApplication(), settings.hfMirror)
            upscalerRevision++
        }
    }

    fun deleteUpscaler() {
        viewModelScope.launch {
            runCatching { UpscalerManager.delete(getApplication()) }
                .onSuccess { upscalerRevision++ }
                .onFailure { toast = "删除失败：${it.message}" }
        }
    }

    private suspend fun loadFromDisk() {
        migrateLegacyScript()
        refreshLibrary()
        if (library.isEmpty()) {
            resetToEmpty()
            return
        }
        val savedKey = runCatching { lastScriptFile.readText().trim() }.getOrNull()
        val entry = library.firstOrNull { it.key == savedKey } ?: library.first()
        currentScriptKey = entry.key
        applyScript(entry.script, File(scriptsDir, entry.key), restore = true)
    }

    private suspend fun migrateLegacyScript() = withContext(Dispatchers.IO) {
        val oldScript = File(rootDir, "script.json")
        if (!oldScript.isFile) return@withContext
        val raw = runCatching { oldScript.readText() }.getOrNull() ?: return@withContext
        val result = GalgameScriptCodec.validate(raw)
        val parsed = result.script ?: return@withContext
        if (!result.ok) return@withContext
        val folder = newScriptFolder(parsed)
        runCatching {
            oldScript.copyTo(File(folder, "script.json"), overwrite = true)
            File(rootDir, "save.json").takeIf { it.isFile }
                ?.copyTo(File(folder, "save.json"), overwrite = true)
            val legacyImages = File(legacyImageRoot, safeId(parsed.id))
            if (legacyImages.isDirectory) {
                legacyImages.listFiles()?.forEach { file ->
                    runCatching { file.copyTo(File(File(folder, "images"), file.name), overwrite = true) }
                }
            }
            oldScript.delete()
            File(rootDir, "save.json").delete()
            legacyImageRoot.deleteRecursively()
            lastScriptFile.writeText(folder.name)
        }
    }

    private suspend fun refreshLibrary() = withContext(Dispatchers.IO) {
        val entries = scriptsDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { folder ->
                val raw = runCatching { File(folder, "script.json").readText() }.getOrNull()
                    ?: return@mapNotNull null
                val result = GalgameScriptCodec.validate(raw)
                val parsed = result.script ?: return@mapNotNull null
                if (!result.ok) return@mapNotNull null
                val save = readSaveFrom(folder, parsed.id)
                val currentNodeTitle = save
                    ?.let { s -> parsed.nodes.firstOrNull { it.id == s.nodeId }?.title.orEmpty() }
                    .orEmpty()
                val saveFile = File(folder, "save.json")
                val lastPlayed = maxOf(
                    saveFile.takeIf { it.isFile }?.lastModified() ?: 0L,
                    File(folder, "script.json").lastModified(),
                )
                GalgameLibraryEntry(
                    key = folder.name,
                    title = parsed.title,
                    nodeCount = parsed.nodes.size,
                    imageCount = parsed.images.size,
                    hasSave = save != null,
                    currentNodeTitle = currentNodeTitle,
                    lastPlayedAt = lastPlayed,
                    script = parsed,
                )
            }
            .sortedByDescending { it.lastPlayedAt }
        library = entries
    }

    fun selectScript(key: String) {
        val entry = library.firstOrNull { it.key == key } ?: return
        val folder = File(scriptsDir, key)
        if (!File(folder, "script.json").isFile) {
            refreshAfterError("剧本不存在：$key")
            return
        }
        currentScriptKey = key
        runCatching { lastScriptFile.writeText(key) }
        applyScript(entry.script, folder, restore = true)
    }

    private fun refreshAfterError(message: String) {
        error = message
    }

    private fun resetToEmpty() {
        script = null
        hasScript = false
        hasSave = false
        scriptTitle = "Galgame"
        nodeCount = 0
        imageCount = 0
        currentNode = null
        displayLines = emptyList()
        history = emptyList()
        lineIndex = 0
        isEnding = false
        backgroundPath = null
        currentImageId = null
        imagePaths = mutableMapOf()
        imageRevision++
        currentScriptKey = null
        currentScriptFolder = null
        currentScriptFile = null
        currentSaveFile = null
        scriptImageDir = null
    }

    private fun applyScript(value: GalgameScript, folder: File, restore: Boolean) {
        script = value
        hasScript = true
        scriptTitle = value.title
        nodeCount = value.nodes.size
        imageCount = value.images.size
        currentScriptFolder = folder
        currentScriptFile = File(folder, "script.json")
        currentSaveFile = File(folder, "save.json")
        scriptImageDir = File(folder, "images").apply { mkdirs() }
        imagePaths = existingImages(value).toMutableMap()
        imageRevision++
        val save = if (restore) readSave(value.id) else null
        hasSave = save != null
        if (save != null) {
            val node = value.nodes.firstOrNull { it.id == save.nodeId }
            if (node != null) {
                state = GalgameJson.parseState(save.stateJson)
                history = save.history
                currentNode = node
                lineIndex = save.lineIndex
                renderCurrent()
                startImageWorker()
                return
            }
        }
        state = GalgameState()
        history = emptyList()
        showNode(value.start, null, addHistory = true)
    }

    private fun newScriptFolder(value: GalgameScript): File {
        val base = safeId(value.id).ifBlank { "script" }
        var folder = File(scriptsDir, base)
        var suffix = 2
        while (folder.exists()) {
            folder = File(scriptsDir, "$base-$suffix")
            suffix++
        }
        folder.mkdirs()
        File(folder, "images").mkdirs()
        return folder
    }

    private fun showNode(nodeId: String, choiceText: String?, addHistory: Boolean) {
        val s = script ?: return
        val node = s.nodes.firstOrNull { it.id == nodeId } ?: run {
            error = "找不到节点：$nodeId"
            return
        }
        if (addHistory) {
            history = history + GalgameHistoryEntry(node.id, choiceText.orEmpty())
            state = state.withEffects(node.effects)
        }
        state = state.copy(
            location = node.location.ifBlank { state.location },
            storyTime = node.time.ifBlank { state.storyTime },
        )
        currentNode = node
        lineIndex = 0
        renderCurrent()
        saveState()
        startImageWorker()
    }

    private fun renderCurrent() {
        val node = currentNode ?: run {
            displayLines = emptyList()
            backgroundPath = null
            currentImageId = null
            return
        }
        val inherited = inheritedImageBeforeCurrent()
        val defaultImage = node.image.ifBlank { inherited ?: "" }
        val lines = buildList {
            if (node.narration.isNotBlank()) {
                add(DisplayLine("旁白", node.narration, narration = true, imageId = defaultImage))
            }
            node.lines.forEach { line ->
                add(
                    DisplayLine(
                        speaker = line.speaker,
                        text = line.text,
                        narration = false,
                        imageId = line.image.ifBlank { defaultImage },
                    )
                )
            }
        }
        displayLines = lines
        lineIndex = lineIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
        val line = lines.getOrNull(lineIndex)
        currentImageId = line?.imageId
        backgroundPath = currentImageId?.let { imagePaths[it] }?.takeIf { File(it).isFile }
        backgroundKind = currentImageId
            ?.let { id -> script?.images?.firstOrNull { it.id == id }?.kind }
            .orEmpty()
            .ifBlank { "scene" }
        isEnding = node.ending && lineIndex >= lines.lastIndex
    }

    /** 当前节点之前最近的显式图片；节点没写 image 时沿路径继承。 */
    private fun inheritedImageBeforeCurrent(): String? {
        val s = script ?: return null
        var inherited: String? = null
        history.dropLast(1).forEach { entry ->
            val node = s.nodes.firstOrNull { it.id == entry.nodeId } ?: return@forEach
            inherited = node.image.ifBlank { inherited }
        }
        return inherited
    }

    private fun existingImages(value: GalgameScript): Map<String, String> {
        val dir = scriptImageDir ?: return emptyMap()
        return value.images.mapNotNull { spec ->
            listOf("jpg", "png")
                .map { File(dir, "${safeId(spec.id)}.$it") }
                .firstOrNull { it.isFile }
                ?.let { spec.id to it.absolutePath }
        }.toMap()
    }

    private fun startImageWorker() {
        imageJob?.cancel()
        val token = ++imageGeneration
        val s = script ?: return
        val dir = scriptImageDir ?: return
        if (s.images.isEmpty()) {
            imageBusy = false
            imageProgress = null
            return
        }
        val specs = s.images.associateBy { it.id }
        val planned = GalgameImagePlanner.plan(s, history, currentImageId)
            .mapNotNull { specs[it] }
            .filter { spec ->
                spec.prompt.isNotBlank() &&
                    imagePaths[spec.id]?.let { File(it).isFile } != true
            }
        if (planned.isEmpty()) {
            imageBusy = false
            imageProgress = null
            return
        }
        imageBusy = true
        imageError = null
        imageJob = viewModelScope.launch {
            try {
                planned.forEachIndexed { index, spec ->
                    if (token != imageGeneration) return@launch
                    imageProgress = "生成图片 ${index + 1}/${planned.size}…"
                    try {
                        val generated = imageMutex.withLock {
                            imageGenerator.generate(
                                prompt = spec.prompt,
                                negative = spec.negative,
                                ratio = spec.ratio,
                                seed = spec.seed,
                                onProgress = { message ->
                                    viewModelScope.launch { imageProgress = message }
                                },
                            )
                        }
                        if (token != imageGeneration) {
                            generated.delete()
                            return@launch
                        }
                        val target = File(dir, "${safeId(spec.id)}.${generated.extension}")
                        withContext(Dispatchers.IO) {
                            generated.copyTo(target, overwrite = true)
                            generated.delete()
                        }
                        if (token == imageGeneration) {
                            imagePaths[spec.id] = target.absolutePath
                            imageRevision++
                            if (spec.id == currentImageId) renderCurrent()
                        }
                    } catch (t: CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        imageError = "图片 ${spec.id} 生成失败：${t.message}"
                    }
                }
            } finally {
                if (token == imageGeneration) {
                    imageProgress = null
                    imageBusy = false
                }
            }
        }
    }

    private fun saveState() {
        val s = script ?: return
        val node = currentNode ?: return
        val file = currentSaveFile ?: return
        val save = GalgameSave(
            scriptId = s.id,
            nodeId = node.id,
            stateJson = GalgameJson.encodeState(state),
            history = history,
            lineIndex = lineIndex,
        )
        hasSave = true
        val key = currentScriptKey
        if (key != null) {
            library = library.map {
                if (it.key == key) {
                    it.copy(
                        hasSave = true,
                        currentNodeTitle = node.title,
                        lastPlayedAt = System.currentTimeMillis(),
                    )
                } else {
                    it
                }
            }.sortedByDescending { it.lastPlayedAt }
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { file.writeText(GalgameScriptCodec.encodeSave(save)) }
        }
    }

    private fun readSave(scriptId: String): GalgameSave? {
        val save = currentSaveFile?.takeIf { it.isFile }?.let { file ->
            runCatching { GalgameScriptCodec.parseSave(file.readText()) }.getOrNull()
        } ?: return null
        return save.takeIf { it.scriptId == scriptId }
    }

    private fun readSaveFrom(folder: File, scriptId: String): GalgameSave? {
        val file = File(folder, "save.json").takeIf { it.isFile } ?: return null
        val save = runCatching { GalgameScriptCodec.parseSave(file.readText()) }.getOrNull()
            ?: return null
        return save.takeIf { it.scriptId == scriptId }
    }

    fun jumpToNode(nodeId: String) {
        val s = script ?: return
        if (s.nodes.none { it.id == nodeId }) return
        showNode(nodeId, "地图跳转", addHistory = true)
    }

    private fun safeId(id: String): String =
        id.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "image" }

    override fun onCleared() {
        imageJob?.cancel()
        super.onCleared()
    }
}