package com.taverndream.yedu

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.taverndream.yedu.data.GalgameHistoryEntry
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
    private val scriptFile = File(rootDir, "script.json")
    private val saveFile = File(rootDir, "save.json")
    private val imageRoot = File(rootDir, "images").apply { mkdirs() }
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

    private var script: GalgameScript? = null
    private var scriptImageDir: File? = null
    private var imagePaths: MutableMap<String, String> = mutableMapOf()
    private var imageJob: Job? = null
    private var imageGeneration = 0

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
                withContext(Dispatchers.IO) {
                    scriptFile.parentFile?.mkdirs()
                    scriptFile.writeText(raw)
                    saveFile.delete()
                    imageJob?.cancel()
                    imageRoot.deleteRecursively()
                    imageRoot.mkdirs()
                }
                applyScript(result.script, restore = false)
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
            lineIndex++
            renderCurrent()
            saveState()
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
        hasSave = saveFile.isFile
        if (!scriptFile.isFile) {
            resetToEmpty()
            return
        }
        val raw = withContext(Dispatchers.IO) { scriptFile.readText() }
        val result = GalgameScriptCodec.validate(raw)
        if (!result.ok || result.script == null) {
            error = "剧本校验失败：\n" + result.errors.joinToString("\n").take(1600)
            resetToEmpty()
            return
        }
        applyScript(result.script, restore = true)
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
    }

    private fun applyScript(value: GalgameScript, restore: Boolean) {
        script = value
        hasScript = true
        scriptTitle = value.title
        nodeCount = value.nodes.size
        imageCount = value.images.size
        scriptImageDir = File(imageRoot, safeId(value.id)).apply { mkdirs() }
        imagePaths = existingImages(value).toMutableMap()
        val save = if (restore) readSave(value.id) else null
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
        startImageWorker()
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
        if (s.images.isEmpty()) return
        val current = currentImageId
        val ordered = s.images.sortedBy { if (it.id == current) 0 else 1 }
        imageBusy = true
        imageError = null
        imageJob = viewModelScope.launch {
            try {
                ordered.forEachIndexed { index, spec ->
                    if (token != imageGeneration) return@launch
                    if (imagePaths[spec.id]?.let { File(it).isFile } == true) return@forEachIndexed
                    if (spec.prompt.isBlank()) return@forEachIndexed
                    imageProgress = "生成图片 ${index + 1}/${ordered.size}…"
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
        val save = GalgameSave(
            scriptId = s.id,
            nodeId = node.id,
            stateJson = GalgameJson.encodeState(state),
            history = history,
            lineIndex = lineIndex,
        )
        hasSave = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { saveFile.writeText(GalgameScriptCodec.encodeSave(save)) }
        }
    }

    private fun readSave(scriptId: String): GalgameSave? {
        if (!saveFile.isFile) return null
        val save = runCatching { GalgameScriptCodec.parseSave(saveFile.readText()) }.getOrNull()
            ?: return null
        return save.takeIf { it.scriptId == scriptId }
    }

    private fun safeId(id: String): String =
        id.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "image" }

    override fun onCleared() {
        imageJob?.cancel()
        super.onCleared()
    }
}