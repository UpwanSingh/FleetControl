package com.fleetcontrol.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.fleetcontrol.utils.ImageOptimizer
import com.fleetcontrol.utils.ImageOptimizer.QualityPreset
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service for handling image capture, compression, and storage
 * Used by drivers to capture trip-related images
 */
class ImageCaptureService(private val context: Context) {
    
    companion object {
        private const val IMAGE_DIR = "trip_attachments"
        private const val MAX_IMAGE_DIMENSION = 1920 // Max width/height
        private const val JPEG_QUALITY = 85 // Compression quality
        private const val FILE_PROVIDER_AUTHORITY = "com.fleetcontrol.provider"
    }
    
    /**
     * Get the directory for storing trip attachments
     */
    private fun getAttachmentsDir(): File {
        val dir = File(context.filesDir, IMAGE_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Create a temporary file for camera capture
     * Returns the Uri for use with camera intent
     */
    fun createTempImageFile(): Pair<File, Uri> {
        // Hygiene: Cleanup old abandoned temp files (older than 24h)
        cleanupOldTempFiles()
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "TRIP_${timestamp}.jpg"
        val file = File(getAttachmentsDir(), fileName)
        val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        return Pair(file, uri)
    }

    private fun cleanupOldTempFiles() {
        try {
            val dir = getAttachmentsDir()
            val files = dir.listFiles { _, name -> name.startsWith("TRIP_") && name.endsWith(".jpg") }
            
            val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
            
            files?.forEach { file ->
                if (file.lastModified() < twentyFourHoursAgo) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore hygiene errors
        }
    }
    
    /**
     * Process image with advanced optimization
     * Uses ImageOptimizer for better memory efficiency and compression
     */
    fun processAndSaveImageOptimized(
        sourceUri: Uri, 
        tripId: Long, 
        prefix: String = "IMG",
        preset: QualityPreset = QualityPreset.STANDARD
    ): ImageSaveResult {
        return try {
            // Create temp file to copy URI content
            val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Detect image type automatically
            val imageType = ImageOptimizer.detectImageType(tempFile.absolutePath)
            
            // Generate output file path
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${prefix}_${tripId}_${timestamp}.jpg"
            val outputFile = File(getAttachmentsDir(), fileName)
            
            // Process with optimizer
            val result = ImageOptimizer.processImage(
                context = context,
                sourcePath = tempFile.absolutePath,
                outputPath = outputFile.absolutePath,
                preset = preset,
                imageType = imageType
            )
            
            // Cleanup temp file
            tempFile.delete()
            
            // TODO: Fix ImageOptimizationResult when expression
            ImageSaveResult.Error("Optimization temporarily disabled")
            
        } catch (e: Exception) {
            ImageSaveResult.Error("Optimized processing failed: ${e.message}")
        }
    }
    
    /**
     * Process image from gallery/file picker (legacy method)
     * - Decodes with sample size for memory efficiency
     * - Corrects rotation based on EXIF
     * - Returns the saved file path and size
     */
    fun processAndSaveImage(sourceUri: Uri, tripId: Long, prefix: String = "IMG"): ImageSaveResult {
        return try {
            // Read the image
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return ImageSaveResult.Error("Cannot read image")
            
            // Decode with sample size for memory efficiency
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            // Calculate sample size
            val sampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
            
            // Decode actual bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val inputStream2 = context.contentResolver.openInputStream(sourceUri)
                ?: return ImageSaveResult.Error("Cannot read image")
            var bitmap = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
            inputStream2.close()
            
            if (bitmap == null) {
                return ImageSaveResult.Error("Failed to decode image")
            }
            
            // Correct rotation from EXIF
            bitmap = correctImageRotation(sourceUri, bitmap)
            
            // Scale down if still too large
            bitmap = scaleDownIfNeeded(bitmap, MAX_IMAGE_DIMENSION)
            
            // Generate filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${prefix}_${tripId}_${timestamp}.jpg"
            val outputFile = File(getAttachmentsDir(), fileName)
            
            // Save compressed
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            
            // Recycle bitmap
            bitmap.recycle()
            
            ImageSaveResult.Success(
                filePath = outputFile.absolutePath,
                fileName = fileName,
                fileSize = outputFile.length()
            )
        } catch (e: Exception) {
            ImageSaveResult.Error(e.message ?: "Unknown error saving image")
        }
    }
    
    /**
     * Process image from camera file (already saved to temp location)
     */
    fun processAndSaveCameraImage(sourceFile: File, tripId: Long, prefix: String = "IMG"): ImageSaveResult {
        return try {
            if (!sourceFile.exists()) {
                return ImageSaveResult.Error("Camera image file not found")
            }
            
            // Decode with sample size
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(sourceFile.absolutePath, options)
            
            val sampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
            
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            var bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
                ?: return ImageSaveResult.Error("Failed to decode camera image")
            
            // Correct rotation
            bitmap = correctImageRotationFromFile(sourceFile.absolutePath, bitmap)
            
            // Scale down if needed
            bitmap = scaleDownIfNeeded(bitmap, MAX_IMAGE_DIMENSION)
            
            // Generate final filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${prefix}_${tripId}_${timestamp}.jpg"
            val outputFile = File(getAttachmentsDir(), fileName)
            
            // Save compressed
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            
            bitmap.recycle()
            
            // Delete temp file if different from output
            if (sourceFile.absolutePath != outputFile.absolutePath) {
                sourceFile.delete()
            }
            
            ImageSaveResult.Success(
                filePath = outputFile.absolutePath,
                fileName = fileName,
                fileSize = outputFile.length()
            )
        } catch (e: Exception) {
            ImageSaveResult.Error(e.message ?: "Unknown error processing camera image")
        }
    }
    
    /**
     * Delete an attachment file
     */
    fun deleteImage(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get file Uri for sharing/viewing
     */
    fun getFileUri(filePath: String): Uri? {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if file exists
     */
    fun fileExists(filePath: String): Boolean {
        return File(filePath).exists()
    }
    
    /**
     * Get total storage used by attachments
     */
    fun getTotalStorageUsed(): Long {
        val dir = getAttachmentsDir()
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }
    
    /**
     * Format file size for display
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
    
    // === Private Helper Methods ===
    
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
    
    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        
        val scale = if (width > height) {
            maxDimension.toFloat() / width
        } else {
            maxDimension.toFloat() / height
        }
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        if (scaled != bitmap) {
            bitmap.recycle()
        }
        return scaled
    }
    
    private fun correctImageRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            val rotation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream.close()
            
            rotateIfNeeded(bitmap, rotation)
        } catch (e: Exception) {
            bitmap
        }
    }
    
    private fun correctImageRotationFromFile(filePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val rotation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            rotateIfNeeded(bitmap, rotation)
        } catch (e: Exception) {
            bitmap
        }
    }
    
    private fun rotateIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }
}

/**
 * Result of saving an image
 */
sealed class ImageSaveResult {
    data class Success(
        val filePath: String,
        val fileName: String,
        val fileSize: Long
    ) : ImageSaveResult()
    
    data class Error(val message: String) : ImageSaveResult()
}
