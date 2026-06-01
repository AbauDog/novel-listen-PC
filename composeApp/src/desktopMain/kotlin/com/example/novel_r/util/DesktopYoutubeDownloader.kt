package com.example.novel_r.util

import com.example.novel_r.data.model.AudioFile
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

private const val YTDLP_URL   = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
private const val FFMPEG_URL  = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"

class DesktopYoutubeDownloader : YoutubeDownloader {

    private fun getResourceDir(): File {
        val dir = File(getAppDataPath())
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 獲取即時串流資訊 (不下載) */
    override suspend fun getStreamInfo(url: String): Result<AudioFile> = withContext(Dispatchers.IO) {
        try {
            val resourceDir = getResourceDir()
            val ytdlpPath = ensureYtDlp(resourceDir.absolutePath) ?: return@withContext Result.failure(Exception("找不到 yt-dlp"))

            val urlTrimmed = url.trim()
            println("[YoutubeDownloader] getStreamInfo 收到網址: $urlTrimmed (長度: ${urlTrimmed.length})")
            val pb = ProcessBuilder(ytdlpPath, "--encoding", "utf-8", "--print", "%(title)s|%(id)s|%(duration)s", "--no-playlist", urlTrimmed)
            pb.environment()["PATH"] = buildEnvPath(resourceDir)
            pb.environment()["PYTHONIOENCODING"] = "utf-8"
            val proc = pb.start()
            val stdout = proc.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
            proc.waitFor()

            val lines = stdout.lines().filter { it.isNotBlank() && !it.startsWith("WARNING") && !it.startsWith("[") }
            val dataLine = lines.lastOrNull() ?: ""
            val parts = dataLine.split("|")
            
            if (parts.size < 3) return@withContext Result.failure(Exception("無法獲取資訊: $stdout"))

            val durationSec = parts.last().trim().toDoubleOrNull() ?: 0.0
            val title = parts.dropLast(2).joinToString("|").trim()

            // 檢查是否已有快取檔案，若有則直接回傳本地路徑，避免串流延遲與熱切換問題
            val cacheDir = File(resourceDir, "youtube_cache")
            val targetFile = File(cacheDir, sanitizeFileName(title, "mp3"))
            if (targetFile.exists()) {
                println("[YoutubeDownloader] 發現快取檔案，直接使用本地播放: ${targetFile.absolutePath}")
                return@withContext Result.success(AudioFile(
                    filePath = targetFile.absolutePath,
                    fileName = "[雲端] $title",
                    fileSize = targetFile.length(),
                    duration = (durationSec * 1000).toLong(),
                    mimeType = "audio/mpeg",
                    isStream = false,
                    originalUrl = url.trim()
                ))
            }

            Result.success(AudioFile(
                filePath = "youtube:${urlTrimmed}", // 使用特殊協議頭讓播放器知道要用管道播放
                fileName = "[串流] $title",
                fileSize = 0,
                duration = (durationSec * 1000).toLong(),
                mimeType = "audio/mpeg",
                isStream = true,
                originalUrl = url.trim()
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadAudio(url: String, outputDir: String, onProgress: (String) -> Unit): Result<AudioFile> = withContext(Dispatchers.IO) {
        val resourceDir = getResourceDir()
        val logFile = File(resourceDir, "novel_r_debug.log")
        try {
            logFile.appendText("downloadAudio called. URL: $url\n")
            logFile.appendText("resourceDir: ${resourceDir.absolutePath}\n")
            val cacheDir    = File(resourceDir, "youtube_cache").also { it.mkdirs() }
            logFile.appendText("cacheDir: ${cacheDir.absolutePath}\n")
            val ytdlpPath   = ensureYtDlp(resourceDir.absolutePath)
            logFile.appendText("ytdlpPath: $ytdlpPath\n")
            if (ytdlpPath == null) {
                logFile.appendText("ERROR: ytdlpPath is null!\n")
                return@withContext Result.failure(Exception("找不到 yt-dlp"))
            }
            val ffmpegOk = ensureFfmpeg()
            logFile.appendText("ffmpegOk: $ffmpegOk\n")

            val infoPb = ProcessBuilder(ytdlpPath, "--encoding", "utf-8", "--get-title", "--get-id", "--no-playlist", url)
            infoPb.environment()["PYTHONIOENCODING"] = "utf-8"
            logFile.appendText("Launching info process...\n")
            val infoProc = infoPb.start()
            val infoOut = infoProc.inputStream.bufferedReader(Charsets.UTF_8).readText().trim().lines().filter { it.isNotBlank() && !it.startsWith("WARNING") }
            infoProc.waitFor()
            logFile.appendText("info process exited with ${infoProc.exitValue()}. Output: $infoOut\n")
            
            if (infoOut.isEmpty()) {
                logFile.appendText("ERROR: infoOut is empty!\n")
                return@withContext Result.failure(Exception("無法獲取資訊"))
            }
            
            val title = infoOut[0]
            val id = if (infoOut.size > 1) infoOut[1] else infoOut[0]
            logFile.appendText("Title: $title, ID: $id\n")
            // 使用代換特殊字元後的實際檔名，不再另行編碼 ID
            val targetFile = File(cacheDir, sanitizeFileName(title, "mp3"))
            logFile.appendText("targetFile: ${targetFile.absolutePath}, exists: ${targetFile.exists()}\n")

            if (!targetFile.exists()) {
                logFile.appendText("Starting actual download...\n")
                val dlPb = ProcessBuilder(
                    ytdlpPath, 
                    "--encoding", "utf-8",
                    "--ffmpeg-location", resourceDir.absolutePath, // 明確指定 ffmpeg 位置
                    "--limit-rate", "2M", // 稍微放寬下載速度
                    "-f", "bestaudio[format_id$=drc]/bestaudio",
                    "-x", "--audio-format", "mp3", "--audio-quality", "128k",
                    "--postprocessor-args", "ExtractAudio:-af loudnorm",
                    "--no-playlist", "-o", targetFile.absolutePath, url
                )
                dlPb.environment()["PATH"] = buildEnvPath(resourceDir)
                dlPb.environment()["PYTHONIOENCODING"] = "utf-8"
                val proc = dlPb.start()
                
                try {
                    // 讀取標準輸出與錯誤輸出以防阻塞，並在失敗時顯示
                    val errThread = Thread {
                        try {
                            val err = proc.errorStream.bufferedReader(Charsets.UTF_8).readText()
                            if (err.isNotBlank()) {
                                logFile.appendText("[YTDownloader] 錯誤輸出: $err\n")
                            }
                        } catch (e: Exception) {}
                    }
                    errThread.start()

                    val outThread = Thread {
                        try {
                            val reader = proc.inputStream.bufferedReader(Charsets.UTF_8)
                            val sb = StringBuilder()
                            var charInt = reader.read()
                            while (charInt != -1) {
                                val c = charInt.toChar()
                                if (c == '\n' || c == '\r') {
                                    // 移除可能殘留的 ANSI 控制字元
                                    val line = sb.toString().replace(Regex("\u001B\\[[;\\d]*[mK]"), "")
                                    if (line.isNotBlank() && (line.contains("download") || line.contains("ERROR") || line.contains("ffmpeg"))) {
                                        logFile.appendText("[YTDownloader] 輸出: $line\n")
                                    }
                                    if (line.contains("[download]") && line.contains("%")) {
                                        val percent = line.substringAfter("[download]").trim().substringBefore("%").trim()
                                        // 再次過濾掉非數字部分（有時會有空白或文字）
                                        val purePercent = percent.split(" ").firstOrNull() ?: ""
                                        if (purePercent.toDoubleOrNull() != null) {
                                            onProgress(purePercent)
                                        }
                                    }
                                    sb.setLength(0)
                                } else {
                                    sb.append(c)
                                }
                                charInt = reader.read()
                            }
                        } catch (e: Exception) {}
                    }
                    outThread.start()
                    
                    // 使用 suspendCancellableCoroutine 或簡單的 loop 來支援取消
                    while (proc.isAlive) {
                        delay(500)
                    }
                } catch (e: CancellationException) {
                    logFile.appendText("[YTDownloader] 下載任務被取消，關閉進程\n")
                    proc.destroyForcibly()
                    throw e
                }
                logFile.appendText("Download process finished. Exit code: ${proc.exitValue()}\n")
            }
            
            val targetFileMp3 = File(cacheDir, sanitizeFileName(title, "mp3"))
            val targetFileWebm = File(cacheDir, sanitizeFileName(title, "webm"))
            val targetFileM4a = File(cacheDir, sanitizeFileName(title, "m4a"))
            
            val actualFile = listOf(targetFileMp3, targetFileWebm, targetFileM4a).find { it.exists() }
            logFile.appendText("Search result for actual downloaded files: mp3=${targetFileMp3.exists()}, webm=${targetFileWebm.exists()}, m4a=${targetFileM4a.exists()}\n")
            
            if (actualFile == null) {
                logFile.appendText("ERROR: actualFile is null!\n")
                return@withContext Result.failure(Exception("下載檔案未找到或下載失敗"))
            }

            logFile.appendText("Download successful! Saved at: ${actualFile.absolutePath}\n")
            Result.success(AudioFile(
                filePath = actualFile.absolutePath,
                fileName = "[雲端] $title",
                fileSize = actualFile.length(),
                duration = 0,
                mimeType = "audio/mpeg",
                isStream = false,
                originalUrl = url
            ))
        } catch (e: Exception) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            logFile.appendText("EXCEPTION in downloadAudio: ${sw.toString()}\n")
            Result.failure(e)
        }
    }

    override suspend fun ensureYtDlp(appDir: String, onProgress: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        val resourceDir = getResourceDir()
        val dest = File(resourceDir, "yt-dlp.exe")
        if (isExecutableWorking(dest.absolutePath, "--version")) return@withContext dest.absolutePath
        
        onProgress("正在下載播放組件 yt-dlp...")
        try {
            downloadFile(YTDLP_URL, dest) { onProgress("下載中: $it%") }
            if (isExecutableWorking(dest.absolutePath, "--version")) dest.absolutePath else null
        } catch (e: Exception) { null }
    }

    override suspend fun ensureFfmpeg(onProgress: (String) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val resourceDir = getResourceDir()
        val ffmpegExe = File(resourceDir, "ffmpeg.exe")
        if (ffmpegExe.exists() && isExecutableWorking(ffmpegExe.absolutePath, "-version")) return@withContext true

        onProgress("正在下載音訊組件 ffmpeg...")
        try {
            val zip = File(resourceDir, "_ffmpeg_tmp.zip")
            downloadFile(FFMPEG_URL, zip) { onProgress("下載中: $it%") }
            extractFfmpegFromZip(zip, resourceDir)
            zip.delete()
            ffmpegExe.exists()
        } catch (e: Exception) { false }
    }

    private fun buildEnvPath(resourceDir: File): String {
        val nodePath = "C:\\Program Files\\nodejs"
        return "${resourceDir.absolutePath};$nodePath;${System.getenv("PATH") ?: ""}"
    }

    private fun isExecutableWorking(cmd: String, vararg args: String): Boolean = try {
        val p = ProcessBuilder(cmd, *args).start()
        p.inputStream.bufferedReader().readText()
        p.waitFor() == 0
    } catch (e: Exception) { false }

    private fun downloadFile(urlStr: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = URL(urlStr).openConnection()
        val total = conn.contentLengthLong
        var downloaded = 0L
        conn.getInputStream().use { input ->
            dest.outputStream().use { output ->
                val buf = ByteArray(65536)
                var n: Int
                while (input.read(buf).also { n = it } >= 0) {
                    output.write(buf, 0, n)
                    downloaded += n
                    if (total > 0) onProgress((downloaded * 100 / total).toInt())
                }
            }
        }
    }

    private fun extractFfmpegFromZip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = File(entry.name).name
                if (name == "ffmpeg.exe" || name == "ffprobe.exe") {
                    File(destDir, name).outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}

actual fun createYoutubeDownloader(): YoutubeDownloader = DesktopYoutubeDownloader()
