package app.car.parking.data.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** 주차 사진은 앱 전용 저장소에 복사해 재실행 후에도 접근할 수 있게 한다. */
class PhotoStore(private val context: Context) {
    private val dir get() = File(context.filesDir, "photos").apply { mkdirs() }

    /** 카메라 촬영용 빈 파일과 content URI */
    fun newCaptureTarget(): Pair<File, Uri> {
        val file = File(dir, "capture-${UUID.randomUUID()}.jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** 사진 선택기의 URI를 앱 저장소로 복사한다. 전체 사진 보관함 권한을 요청하지 않는다. */
    suspend fun importPicked(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(dir, "picked-${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null
            file.absolutePath
        }.getOrNull()
    }

    fun delete(path: String?) {
        if (path == null) return
        val file = File(path)
        if (file.parentFile == dir) file.delete()
    }

    fun exists(path: String?): Boolean = path != null && File(path).let { it.exists() && it.length() > 0 }
}
