package com.fleetcontrol.services.export

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Typeface
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.utils.DateUtils
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * PDF Export Service
 * Requires Premium subscription
 */
class PdfExportService(private val context: Context, private val exportDir: File) {
    
    companion object {
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 40
        private const val LINE_HEIGHT = 20
    }
    
    /**
     * Get suggested filename for PDF export
     */
    fun getSuggestedFileName(year: Int, month: Int): String {
        return "fleetcontrol_report_${year}_${month + 1}.pdf"
    }
    
    /**
     * Generate PDF and write to an OutputStream (for SAF)
     */
    fun writePdfToStream(outputStream: OutputStream, trips: List<TripEntity>, year: Int, month: Int) {
        val document = createPdfDocument(trips, year, month)
        document.writeTo(outputStream)
        document.close()
    }
    
    /**
     * Create PDF document with trip data
     */
    private fun createPdfDocument(trips: List<TripEntity>, year: Int, month: Int): PdfDocument {
        val document = PdfDocument()
        var pageNumber = 1
        
        // Define Paints
        val titlePaint = Paint().apply {
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }
        val headerPaint = Paint().apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        val textPaint = Paint().apply {
            textSize = 10f
        }
        
        // Helper to start a new page
        fun startNewPage(): android.graphics.pdf.PdfDocument.Page {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            pageNumber++
            return document.startPage(pageInfo)
        }
        
        var page = startNewPage()
        var canvas = page.canvas
        var y = MARGIN.toFloat()
        
        // === Title & Summary (First Page Only) ===
        canvas.drawText("FleetControl Report", MARGIN.toFloat(), y + 24, titlePaint)
        y += 48
        
        canvas.drawText("Period: ${month + 1}/$year", MARGIN.toFloat(), y, headerPaint)
        y += LINE_HEIGHT * 2
        
        // Summary Calculations
        val totalBags = trips.sumOf { it.bagCount }
        val totalRevenue = trips.sumOf { it.bagCount * it.snapshotCompanyRate }
        val totalDriverEarnings = trips.sumOf { it.bagCount * it.snapshotDriverRate }
        val totalProfit = totalRevenue - totalDriverEarnings
        
        canvas.drawText("Total Trips: ${trips.size}", MARGIN.toFloat(), y, textPaint)
        y += LINE_HEIGHT
        canvas.drawText("Total Bags: $totalBags", MARGIN.toFloat(), y, textPaint)
        y += LINE_HEIGHT
        canvas.drawText("Total Revenue: ₹$totalRevenue", MARGIN.toFloat(), y, textPaint)
        y += LINE_HEIGHT
        canvas.drawText("Driver Earnings: ₹$totalDriverEarnings", MARGIN.toFloat(), y, textPaint)
        y += LINE_HEIGHT
        canvas.drawText("Net Profit: ₹$totalProfit", MARGIN.toFloat(), y, textPaint)
        y += LINE_HEIGHT * 2
        
        // === Table Header Logic ===
        fun drawHeader(c: android.graphics.Canvas, currentY: Float) {
            c.drawText("Date", MARGIN.toFloat(), currentY, headerPaint)
            c.drawText("Client", 120f, currentY, headerPaint)
            c.drawText("Bags", 250f, currentY, headerPaint)
            c.drawText("Revenue", 300f, currentY, headerPaint)
            c.drawText("Profit", 400f, currentY, headerPaint)
            c.drawLine(MARGIN.toFloat(), currentY + 5, 500f, currentY + 5, textPaint)
        }
        
        drawHeader(canvas, y)
        y += LINE_HEIGHT
        
        // === Data Rows (Multi-page) ===
        trips.forEach { trip ->
            // Check for page overflow
            if (y > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                page = startNewPage()
                canvas = page.canvas
                y = MARGIN.toFloat()
                
                // Draw header on new page for consistency
                drawHeader(canvas, y)
                y += LINE_HEIGHT
            }
            
            val revenue = trip.bagCount * trip.snapshotCompanyRate
            val driverEarning = trip.bagCount * trip.snapshotDriverRate
            val labourCost = trip.bagCount * trip.snapshotLabourCostPerBag
            val profit = revenue - driverEarning - labourCost
            
            canvas.drawText(DateUtils.formatDate(trip.tripDate), MARGIN.toFloat(), y, textPaint)
            canvas.drawText(trip.clientName.take(15), 120f, y, textPaint)
            canvas.drawText("${trip.bagCount}", 250f, y, textPaint)
            canvas.drawText("₹${revenue.toInt()}", 300f, y, textPaint)
            canvas.drawText("₹${profit.toInt()}", 400f, y, textPaint)
            y += LINE_HEIGHT
        }
        
        document.finishPage(page)
        return document
    }
    
    /**
     * Export trips to PDF file (fallback to internal storage)
     * Returns the file path
     */
    suspend fun exportTrips(trips: List<TripEntity>, year: Int, month: Int): String {
        val fileName = getSuggestedFileName(year, month)
        val file = File(exportDir, fileName)
        
        val document = createPdfDocument(trips, year, month)
        
        FileOutputStream(file).use { outputStream ->
            document.writeTo(outputStream)
        }
        
        document.close()
        
        return file.absolutePath
    }
}
