package com.fleetcontrol.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Advanced image optimization utility for memory-efficient image processing
 * 
 * Features:
 * - Smart compression based on content type
 * - Memory-efficient decoding
 * - Automatic cleanup
 * - Multiple quality presets
 */
object ImageOptimizer {
    
    enum class QualityPreset(val maxDimension: Int, val quality: Int, val description: String) {
        THUMBNAIL(512, 75, "Thumbnail quality for list views"),
        STANDARD(1024, 85, "Standard quality for detail views"),
        HIGH(1920, 90, "High quality for important documents"),
        MAX(2048, 95, "Maximum quality for critical images")
    }
    
    enum class ImageType {
        DOCUMENT,     // Receipts, documents - prioritize text clarity
        PHOTO,        // General photos - balanced compression
        THUMBNAIL     // List thumbnails - maximum compression
    }
    
    /**
     * Optimized image processing with smart quality selection
     */
    fun processImage(
        context: Context,
        sourcePath: String,
        outputPath: String,
        preset: QualityPreset = QualityPreset.STANDARD,
        imageType: ImageType = ImageType.PHOTO
    ): ImageOptimizationResult {
        return try {
            // 1. Smart decode with memory efficiency
            val bitmap = decodeBitmapEfficiently(sourcePath, preset.maxDimension)
                ?: return ImageOptimizationResult.Error("Failed to decode image")
            
            // 2. Auto-rotate based on EXIF
            val rotatedBitmap = correctOrientation(context, sourcePath, bitmap)
            
            // 3. Smart compression based on image type
            val optimizedBitmap = optimizeForType(rotatedBitmap, imageType, preset)
            
            // 4. Save with optimal settings
            val fileSize = saveOptimized(optimizedBitmap, outputPath, preset, imageType)
            
            // 5. Cleanup
            if (rotatedBitmap != optimizedBitmap) {
                rotatedBitmap.recycle()
            }
            optimizedBitmap.recycle()
            
            ImageOptimizationResult.Success(
                outputPath = outputPath,
                originalSize = File(sourcePath).length(),
                optimizedSize = fileSize,
                compressionRatio = File(sourcePath).length().toDouble() / fileSize,
                dimensions = "${optimizedBitmap.width}x${optimizedBitmap.height}"
            )
            
        } catch (e: OutOfMemoryError) {
            ImageOptimizationResult.Error("Out of memory: ${e.message}")
        } catch (e: Exception) {
            ImageOptimizationResult.Error("Processing failed: ${e.message}")
        }
    }
    
    /**
     * Memory-efficient bitmap decoding
     */
    private fun decodeBitmapEfficiently(filePath: String, maxDimension: Int): Bitmap? {
        return try {
            // First pass: get dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)
            
            // Calculate sample size
            val sampleSize = calculateSampleSize(options, maxDimension)
            
            // Second pass: decode with sample size
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565 // Uses half the memory of ARGB_8888
                inSampleSize = sampleSize
                inPurgeable = true // Allow system to reclaim memory
            }.let { BitmapFactory.decodeFile(filePath, it) }
            
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Calculate optimal sample size for memory efficiency
     */
    private fun calculateSampleSize(options: BitmapFactory.Options, maxDimension: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        
        if (height > maxDimension || width > maxDimension) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            // Find the largest power of 2 that keeps dimensions under max
            while ((halfHeight / inSampleSize) >= maxDimension && 
                   (halfWidth / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * Correct image orientation based on EXIF data
     */
    private fun correctOrientation(context: Context, imagePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(bitmap, horizontal = true)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(bitmap, horizontal = false)
                else -> bitmap
            }
        } catch (e: IOException) {
            bitmap // Return original if EXIF reading fails
        }
    }
    
    /**
     * Rotate bitmap efficiently
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * Flip bitmap efficiently
     */
    private fun flipBitmap(bitmap: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix().apply {
            if (horizontal) {
                postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            } else {
                postScale(1f, -1f, bitmap.width / 2f, bitmap.height / 2f)
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * Optimize bitmap based on image type
     */
    private fun optimizeForType(
        bitmap: Bitmap, 
        imageType: ImageType, 
        preset: QualityPreset
    ): Bitmap {
        return when (imageType) {
            ImageType.DOCUMENT -> optimizeForDocument(bitmap, preset)
            ImageType.PHOTO -> optimizeForPhoto(bitmap, preset)
            ImageType.THUMBNAIL -> optimizeForThumbnail(bitmap, preset)
        }
    }
    
    /**
     * Optimize for text/document clarity
     */
    private fun optimizeForDocument(bitmap: Bitmap, preset: QualityPreset): Bitmap {
        // For documents, we want to preserve text clarity
        if (bitmap.width > preset.maxDimension || bitmap.height > preset.maxDimension) {
            val scale = min(
                preset.maxDimension.toFloat() / bitmap.width,
                preset.maxDimension.toFloat() / bitmap.height
            )
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            
            // Use higher quality scaling for documents
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }
        return bitmap
    }
    
    /**
     * Optimize for general photos
     */
    private fun optimizeForPhoto(bitmap: Bitmap, preset: QualityPreset): Bitmap {
        // Standard scaling for photos
        if (bitmap.width > preset.maxDimension || bitmap.height > preset.maxDimension) {
            val scale = min(
                preset.maxDimension.toFloat() / bitmap.width,
                preset.maxDimension.toFloat() / bitmap.height
            )
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }
        return bitmap
    }
    
    /**
     * Optimize for thumbnails (maximum compression)
     */
    private fun optimizeForThumbnail(bitmap: Bitmap, preset: QualityPreset): Bitmap {
        // Force thumbnail size
        val targetSize = min(preset.maxDimension, min(bitmap.width, bitmap.height))
        val scale = targetSize.toFloat() / min(bitmap.width, bitmap.height)
        
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, false)
    }
    
    /**
     * Save with optimal compression settings
     */
    private fun saveOptimized(
        bitmap: Bitmap,
        outputPath: String,
        preset: QualityPreset,
        imageType: ImageType
    ): Long {
        val file = File(outputPath)
        file.parentFile?.mkdirs()
        
        val quality = when (imageType) {
            ImageType.DOCUMENT -> min(preset.quality + 5, 100) // Slightly higher for documents
            ImageType.PHOTO -> preset.quality
            ImageType.THUMBNAIL -> kotlin.math.max(preset.quality - 10, 60) // Lower for thumbnails
        }
        
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        
        return file.length()
    }
    
    /**
     * Detect image type based on file characteristics
     */
    fun detectImageType(filePath: String): ImageType {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)
            
            val aspectRatio = options.outWidth.toFloat() / options.outHeight.toFloat()
            val totalPixels = options.outWidth * options.outHeight
            
            when {
                // Document-like characteristics
                aspectRatio > 2.5f || aspectRatio < 0.4f -> ImageType.DOCUMENT
                totalPixels < 500_000 -> ImageType.THUMBNAIL
                else -> ImageType.PHOTO
            }
        } catch (e: Exception) {
            ImageType.PHOTO // Default to photo
        }
    }
    
    /**
     * Get estimated memory usage for bitmap
     */
    fun estimateMemoryUsage(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Long {
        val bytesPerPixel = when (config) {
            Bitmap.Config.ARGB_8888 -> 4
            Bitmap.Config.RGB_565 -> 2
            Bitmap.Config.ARGB_4444 -> 2
            Bitmap.Config.ALPHA_8 -> 1
            else -> 4
        }
        return (width * height * bytesPerPixel).toLong()
    }
    
    /**
     * Check if image processing is safe for current memory
     */
    fun isSafeToProcess(width: Int, height: Int): Boolean {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val availableMemory = maxMemory - usedMemory
        
        val estimatedUsage = estimateMemoryUsage(width, height)
        
        // Only process if we have at least 3x the estimated memory available
        return estimatedUsage * 3 < availableMemory
    }
}

/**
 * Result of image optimization
 */
sealed class ImageOptimizationResult {
    data class Success(
        val outputPath: String,
        val originalSize: Long,
        val optimizedSize: Long,
        val compressionRatio: Double,
        val dimensions: String
    ) : ImageOptimizationResult()
    
    data class Error(val message: String) : ImageOptimizationResult()
}
