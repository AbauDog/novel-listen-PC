package com.example.novel_r.util

import javazoom.jl.decoder.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.*

actual fun createPlatformAudioPlayer(context: Any?): PlatformAudioPlayer = JLayerAudioPlayer()

class JLayerAudioPlayer : PlatformAudioPlayer {
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    @Volatile private var currentUrl: String? = null
    @Volatile private var playThread: Thread? = null
    private val stopFlag   = AtomicBoolean(false)
    private val pauseFlag  = AtomicBoolean(false)
    private val seekTarget = AtomicLong(-1L)

    private val _isPlaying       = MutableStateFlow(false)
    override val isPlaying:       StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long>   = _currentPosition.asStateFlow()
    private val _duration        = MutableStateFlow(0L)
    override val duration:        StateFlow<Long>   = _duration.asStateFlow()
    
    private val _debugLog        = MutableStateFlow("")
    override val debugLog:        StateFlow<String> = _debugLog.asStateFlow()

    private fun logDebug(msg: String) {
        val current = _debugLog.value
        val append = if (current.isEmpty()) msg else "\n$msg"
        _debugLog.value = (current + append).takeLast(2000)
    }

    override fun play(url: String, startPosition: Long, totalDuration: Long) {
        if (url == currentUrl && playThread?.isAlive == true) {
            pauseFlag.set(false)
            _isPlaying.value = true
            return
        }
        stopInternal()
        currentUrl = url
        stopFlag.set(false)
        pauseFlag.set(false)
        seekTarget.set(-1L)
        _currentPosition.value = 0L

        if (!url.startsWith("http") && !url.startsWith("youtube:")) {
            scope.launch { _duration.value = scanDuration(File(url)) }
        } else {
            _duration.value = totalDuration
        }

        playThread = Thread({ playbackLoop(url, startPosition) }, "mp3-play").also {
            it.isDaemon = true
            it.start()
        }
    }

    override fun pause() {
        pauseFlag.set(true)
        _isPlaying.value = false
    }

    override fun stop() = stopInternal()

    override fun seekTo(position: Long) {
        val dur = _duration.value
        val target = if (dur > 0) position.coerceIn(0L, dur) else position.coerceAtLeast(0L)
        seekTarget.set(target)
        _currentPosition.value = target
    }

    override fun setSpeed(speed: Float) {}
    override fun release() = stopInternal()

    private fun stopInternal() {
        stopFlag.set(true)
        pauseFlag.set(false)
        playThread?.join(3000)
        playThread = null
        currentUrl = null
        _isPlaying.value = false
    }

    private fun scanDuration(file: File): Long {
        val resourceDir = File(getAppDataPath())
        val ffprobeFile = File(resourceDir, "ffprobe.exe")
        
        var fileExists = file.exists()
        var fileSize = file.length()
        try {
            val p = java.nio.file.Paths.get(file.absolutePath)
            if (java.nio.file.Files.exists(p)) {
                fileExists = true
                fileSize = java.nio.file.Files.size(p)
            }
        } catch (e: Exception) {}

        if (ffprobeFile.exists() && fileExists) {
            try {
                val pb = ProcessBuilder(
                    ffprobeFile.absolutePath,
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    file.absolutePath
                )
                val proc = pb.start()
                val durationStr = proc.inputStream.bufferedReader().readText().trim()
                proc.waitFor()
                val durationSec = durationStr.toDoubleOrNull() ?: 0.0
                if (durationSec > 0.0) {
                    return (durationSec * 1000).toLong()
                }
            } catch (e: Exception) {
                logDebug("[System] ffprobe 讀取時長失敗: ${e.message}")
            }
        }
        
        // 檔案小於 50MB 才可以安全地使用 JLayer 逐幀掃描，避免大檔案時畫面卡死或耗費過多 CPU
        if (fileSize < 50 * 1024 * 1024) {
            logDebug("[System] 檔案較小 (${fileSize / 1024} KB)，使用 JLayer 掃描時長...")
            if (!fileExists) {
                logDebug("[System] 檔案不存在，無法進行 JLayer 掃描")
                return 0L
            }
            var totalMs = 0.0
            try {
                val stream = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(file.absolutePath))
                BufferedInputStream(stream).use { fis ->
                    val bs = Bitstream(fis)
                    while (true) {
                        val hdr = try { bs.readFrame() } catch (e: Exception) { null } ?: break
                        totalMs += hdr.ms_per_frame()
                        bs.closeFrame()
                    }
                }
            } catch (_: Exception) {}
            return totalMs.toLong()
        } else {
            logDebug("[System] 檔案過大 (${fileSize / 1024 / 1024} MB) 且無 ffprobe，使用檔案大小估算時長...")
            // 估算時長 (假設 128 kbps = 16 KB/s = 16384 bytes/s)
            val estimatedSec = fileSize / 16384.0
            return (estimatedSec * 1000).toLong()
        }
    }

    private fun skipFrames(bs: Bitstream, targetMs: Double): Double {
        var elapsed = 0.0
        while (elapsed < targetMs) {
            val hdr = try { bs.readFrame() } catch (e: Exception) { null } ?: break
            elapsed += hdr.ms_per_frame()
            bs.closeFrame()
        }
        return elapsed
    }

    private fun playbackLoop(url: String, startMs: Long) {
        var fis: InputStream? = null
        var bs: Bitstream? = null
        var line: SourceDataLine? = null
        var ytProcess: Process? = null
        var ffmpegProcess: Process? = null
        
        val isYoutube = url.startsWith("youtube:")
        var currentStartMs = startMs
        var targetUrl = url
        var isYoutubeResolved = false
        
        try {
            fun startStream(startTimeMs: Long) {
                // 徹底清理舊進程
                ffmpegProcess?.destroyForcibly()
                ytProcess?.destroyForcibly()
                try { ffmpegProcess?.waitFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: Exception) {}
                try { ytProcess?.waitFor(2000, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: Exception) {}
                ffmpegProcess = null
                ytProcess = null
                try { bs?.close() } catch (_: Exception) {}
                try { fis?.close() } catch (_: Exception) {}
                bs = null
                fis = null
                
                val resourceDir = File(getAppDataPath())
                val ffmpeg = File(resourceDir, "ffmpeg.exe").absolutePath
                val file = File(url)
                
                // 使用 NIO 偵測以避開 Windows ANSI 編碼限制
                var existsNio = false
                var sizeNio = 0L
                try {
                    val p = java.nio.file.Paths.get(url)
                    existsNio = java.nio.file.Files.exists(p)
                    sizeNio = java.nio.file.Files.size(p)
                } catch (e: Exception) {}
                
                val fileExists = file.exists() || existsNio
                val isLocalFile = !url.startsWith("http") && !url.startsWith("youtube:") && fileExists
                val hasFfmpeg = File(ffmpeg).exists()
                
                logDebug("[System] 準備開啟流: $url")
                logDebug("[System] 檔案屬性 - JavaIO存在: ${file.exists()}, JavaNIO存在: $existsNio, 大小: $sizeNio, 本地檔案: $isLocalFile, 有FFmpeg: $hasFfmpeg")
                
                if (isYoutube) {
                    val ytdlp = File(resourceDir, "yt-dlp.exe").absolutePath
                    val realUrl = url.substringAfter("youtube:").trim()
                    
                    // 每次都取全新 URL
                    logDebug("[System] 正在取得全新串流網址...")
                    val ytPb = ProcessBuilder(ytdlp, "--no-playlist", "--encoding", "utf-8", "-g", "-f", "bestaudio[format_id$=drc]/bestaudio[ext=m4a]/bestaudio", realUrl)
                    ytPb.environment()["PATH"] = buildEnvPath(resourceDir)
                    ytPb.environment()["PYTHONIOENCODING"] = "utf-8"
                    val ytProc = ytPb.start()
                    
                    // 必須消費 errorStream 才能避免 yt-dlp 卡死
                    Thread { 
                        try { 
                            ytProc.errorStream.bufferedReader().useLines { lines ->
                                lines.forEach { logDebug("[yt-dlp] $it") }
                            }
                        } catch(_: Exception){} 
                    }.start()
                    
                    val freshUrl = ytProc.inputStream.bufferedReader().readLines()
                        .filter { it.isNotBlank() && !it.startsWith("WARNING") }.lastOrNull()?.trim() ?: ""
                    ytProc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
                    
                    if (freshUrl.isBlank()) {
                        logDebug("[System] 錯誤: 無法取得串流網址")
                        return
                    }
                    logDebug("[System] 取得新網址(${freshUrl.length}字)，跳轉至 ${startTimeMs}ms")
                    
                    val ffPb = ProcessBuilder(
                        ffmpeg,
                        "-loglevel", "warning",
                        "-user_agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                        "-probesize", "128K",
                        "-analyzeduration", "0",
                        "-i", freshUrl,
                        "-vn", "-filter:a", "loudnorm", "-f", "mp3",
                        "pipe:1"
                    )
                    ffPb.environment()["PATH"] = buildEnvPath(resourceDir)
                    val ffProc = ffPb.start()
                    ffmpegProcess = ffProc
                    
                    Thread {
                        try {
                            ffProc.errorStream.bufferedReader().useLines { lines ->
                                lines.forEach { logDebug("[ffmpeg] $it") }
                            }
                        } catch(_: Exception){}
                    }.start()
                    
                    fis = BufferedInputStream(ffProc.inputStream, 256 * 1024)
                    bs = Bitstream(fis)
                    
                    if (startTimeMs > 1000) {
                        logDebug("[System] YouTube 串流已連線，開始跳轉至: $startTimeMs ms")
                        skipFrames(bs!!, startTimeMs.toDouble())
                    }
                    logDebug("[System] 串流已就緒")
                } else if (isLocalFile && hasFfmpeg) {
                    logDebug("[System] 使用 ffmpeg 播放本地檔案: ${file.name}，跳轉: $startTimeMs ms")
                    val ffPb = ProcessBuilder(
                        ffmpeg,
                        "-loglevel", "warning",
                        "-ss", (startTimeMs / 1000.0).toString(),
                        "-i", file.absolutePath,
                        "-vn", "-filter:a", "loudnorm", "-f", "mp3",
                        "pipe:1"
                    )
                    ffPb.environment()["PATH"] = buildEnvPath(resourceDir)
                    val ffProc = ffPb.start()
                    ffmpegProcess = ffProc
                    
                    Thread {
                        try {
                            ffProc.errorStream.bufferedReader().useLines { lines ->
                                lines.forEach { logDebug("[ffmpeg-local] $it") }
                            }
                        } catch(_: Exception){}
                    }.start()
                    
                    fis = BufferedInputStream(ffProc.inputStream, 256 * 1024)
                    bs = Bitstream(fis)
                } else if (isLocalFile && url.lowercase().endsWith(".mp3")) {
                    logDebug("[System] 無 ffmpeg，使用 JLayer 直接播放本地 MP3，跳轉: $startTimeMs ms")
                    val stream = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(url))
                    fis = BufferedInputStream(stream, 256 * 1024)
                    bs = Bitstream(fis)
                    if (startTimeMs > 1000) {
                        skipFrames(bs!!, startTimeMs.toDouble())
                    }
                }
            }

            startStream(currentStartMs)
            var dec = Decoder()
            var posMs = currentStartMs.toDouble()
            _currentPosition.value = posMs.toLong()

            _isPlaying.value = true
            while (!stopFlag.get()) {
                val seekMs = seekTarget.getAndSet(-1L)
                if (seekMs >= 0) {
                    line?.flush()
                    // 所有類型皆統一重啟進程進行精確且快速跳轉
                    currentStartMs = seekMs
                    startStream(currentStartMs)
                    dec = Decoder()
                    posMs = currentStartMs.toDouble()
                    _currentPosition.value = posMs.toLong()
                }

                while (pauseFlag.get() && !stopFlag.get() && seekTarget.get() < 0) {
                    Thread.sleep(50)
                }
                if (stopFlag.get()) break

                val hdr = try { 
                    bs?.readFrame() 
                } catch (e: Exception) { 
                    logDebug("[System] readFrame 發生例外: ${e.message}")
                    null 
                }
                
                if (hdr == null) {
                    logDebug("[System] readFrame 回傳 null，進程可能已結束或無資料")
                    break
                }
                
                val out = try { 
                    dec.decodeFrame(hdr, bs) as SampleBuffer 
                } catch (e: Exception) {
                    logDebug("[System] decodeFrame 發生例外: ${e.message}")
                    bs?.closeFrame()
                    continue
                }

                if (line == null) {
                    val fmt = AudioFormat(hdr.frequency().toFloat(), 16, if (hdr.mode() == Header.SINGLE_CHANNEL) 1 else 2, true, false)
                    line = AudioSystem.getSourceDataLine(fmt).also { it.open(fmt, 8192 * 4); it.start() }
                    logDebug("[System] AudioLine 已開啟，開始輸出聲音")
                }

                val buf = out.buffer
                val len = out.bufferLength
                val bytes = ByteArray(len * 2)
                for (i in 0 until len) {
                    bytes[i * 2]     = (buf[i].toInt() and 0xFF).toByte()
                    bytes[i * 2 + 1] = (buf[i].toInt() shr 8).toByte()
                }
                line?.write(bytes, 0, bytes.size)

                posMs += hdr.ms_per_frame()
                val currentPosLong = posMs.toLong()
                _currentPosition.value = currentPosLong
                bs?.closeFrame()
            }
        } catch (e: Exception) {
            logDebug("[Error] playbackLoop 發生例外: ${e.message}")
            e.printStackTrace()
        } finally {
            logDebug("[System] playbackLoop 結束，清理資源...")
            _isPlaying.value = false
            line?.drain(); line?.close()
            try { bs?.close() } catch (_: Exception) {}
            try { fis?.close() } catch (_: Exception) {}
            ytProcess?.destroy()
            ffmpegProcess?.destroy()
        }
    }
    
    private fun buildEnvPath(resourceDir: File): String {
        val nodePath = "C:\\Program Files\\nodejs"
        return "${resourceDir.absolutePath};$nodePath;${System.getenv("PATH") ?: ""}"
    }

    override fun clearDebugLog() {
        _debugLog.value = ""
    }
}
