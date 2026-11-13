package com.example.domain.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

data class ImageInfoResult(
    val width: Int,
    val height: Int,
    val colorSpace: String,
    val tiffPageCount: Int = 1,
    val exifTags: Map<String, String>
)

object ImageToolsEngine {

    suspend fun inspectImage(context: Context, file: File): ImageInfoResult = withContext(Dispatchers.IO) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        val cs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            opts.outColorSpace?.name ?: opts.outMimeType ?: "sRGB"
        } else {
            opts.outMimeType ?: "sRGB"
        }

        val exifMap = linkedMapOf<String, String>()
        try {
            val exif = ExifInterface(file.absolutePath)
            val tags = listOf(
                ExifInterface.TAG_MAKE to "Camera Make",
                ExifInterface.TAG_MODEL to "Camera Model",
                ExifInterface.TAG_DATETIME_ORIGINAL to "Date Taken",
                ExifInterface.TAG_F_NUMBER to "Aperture (f/)",
                ExifInterface.TAG_EXPOSURE_TIME to "Exposure Time",
                ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY to "ISO",
                ExifInterface.TAG_FOCAL_LENGTH to "Focal Length",
                ExifInterface.TAG_IMAGE_WIDTH to "EXIF Width",
                ExifInterface.TAG_IMAGE_LENGTH to "EXIF Height",
                ExifInterface.TAG_ORIENTATION to "Orientation",
                ExifInterface.TAG_FLASH to "Flash",
                ExifInterface.TAG_WHITE_BALANCE to "White Balance"
            )
            for ((tag, label) in tags) {
                val v = exif.getAttribute(tag)
                if (!v.isNullOrBlank()) exifMap[label] = v
            }
            exif.latLong?.let { latLng ->
                exifMap["GPS Coordinates"] = "%.5f, %.5f".format(latLng[0], latLng[1])
            }
        } catch (_: Exception) {
        }

        val tiffPages = if (file.extension.lowercase() in setOf("tif", "tiff")) {
            countTiffIfdPages(file)
        } else 1

        ImageInfoResult(
            width = w,
            height = h,
            colorSpace = cs,
            tiffPageCount = tiffPages,
            exifTags = exifMap
        )
    }

    suspend fun decodeBitmapSafe(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val src = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                        val w = info.size.width
                        val h = info.size.height
                        if (w > maxDim || h > maxDim) {
                            val scale = maxDim.toFloat() / maxOf(w, h)
                            decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                        }
                        decoder.isMutableRequired = true
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

    suspend fun transformAndSaveImage(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        rotateDegrees: Float = 0f,
        flipHorizontal: Boolean = false,
        flipVertical: Boolean = false,
        cropRectNorm: RectF? = null,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 90
    ): Result<File> = withContext(Dispatchers.IO) {
        var working: Bitmap? = null
        try {
            val original = decodeBitmapSafe(context, sourceUri, 4096)
                ?: return@withContext Result.failure(IllegalStateException("Unable to decode image"))
            working = original

            // 1. Crop
            if (cropRectNorm != null) {
                val left = (cropRectNorm.left * working.width).toInt().coerceIn(0, working.width - 1)
                val top = (cropRectNorm.top * working.height).toInt().coerceIn(0, working.height - 1)
                val w = ((cropRectNorm.right - cropRectNorm.left) * working.width).toInt().coerceIn(1, working.width - left)
                val h = ((cropRectNorm.bottom - cropRectNorm.top) * working.height).toInt().coerceIn(1, working.height - top)
                val cropped = Bitmap.createBitmap(working, left, top, w, h)
                if (cropped != working) working.recycle()
                working = cropped
            }

            // 2. Rotate / Flip
            if (rotateDegrees != 0f || flipHorizontal || flipVertical) {
                val matrix = Matrix().apply {
                    postRotate(rotateDegrees)
                    postScale(if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f)
                }
                val transformed = Bitmap.createBitmap(working, 0, 0, working.width, working.height, matrix, true)
                if (transformed != working) working.recycle()
                working = transformed
            }

            // 3. Resize
            if (targetWidth != null && targetHeight != null && targetWidth > 0 && targetHeight > 0) {
                val scaled = Bitmap.createScaledBitmap(working, targetWidth, targetHeight, true)
                if (scaled != working) working.recycle()
                working = scaled
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                working.compress(format, quality.coerceIn(10, 100), fos)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            working?.recycle()
        }
    }

    private fun countTiffIfdPages(file: File): Int {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 8) return 1
                val b0 = raf.readUnsignedByte()
                val b1 = raf.readUnsignedByte()
                val littleEndian = (b0 == 0x49 && b1 == 0x49)
                val bigEndian = (b0 == 0x4D && b1 == 0x4D)
                if (!littleEndian && !bigEndian) return 1

                fun readShort(): Int {
                    val a = raf.readUnsignedByte()
                    val b = raf.readUnsignedByte()
                    return if (littleEndian) (b shl 8) or a else (a shl 8) or b
                }

                fun readInt(): Long {
                    val a = raf.readUnsignedByte().toLong()
                    val b = raf.readUnsignedByte().toLong()
                    val c = raf.readUnsignedByte().toLong()
                    val d = raf.readUnsignedByte().toLong()
                    return if (littleEndian) (d shl 24) or (c shl 16) or (b shl 8) or a
                    else (a shl 24) or (b shl 16) or (c shl 8) or d
                }

                val magic = readShort()
                if (magic != 42) return 1
                var ifdOffset = readInt()
                var pages = 0
                while (ifdOffset > 0 && ifdOffset < raf.length() - 2 && pages < 500) {
                    pages++
                    raf.seek(ifdOffset)
                    val entryCount = readShort()
                    val nextOffsetPos = ifdOffset + 2L + entryCount * 12L
                    if (nextOffsetPos + 4 > raf.length()) break
                    raf.seek(nextOffsetPos)
                    ifdOffset = readInt()
                }
                pages.coerceAtLeast(1)
            }
        } catch (_: Exception) {
            1
        }
    }
}
