package com.fleetcontrol.services.export

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.fleetcontrol.data.entities.FuelEntryEntity
import com.fleetcontrol.data.entities.TripEntity
import com.fleetcontrol.data.models.DriverReportSummary
import com.fleetcontrol.utils.DateUtils
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * PDF Export Service for Driver Reports
 * Creates professional PDF reports for driver earnings
 */
class DriverPdfExportService(private val context: Context, private val exportDir: File) {
    
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
        return "driver_earnings_${year}_${month + 1}.pdf"
    }
    
    /**
     * Generate PDF and write to an OutputStream (for SAF)
     */
    fun writePdfToStream(
        outputStream: OutputStream,
        trips: List<TripEntity>,
        fuelEntries: List<FuelEntryEntity>,
        summary: DriverReportSummary,
        year: Int,
        month: Int
    ) {
        val document = createPdfDocument(trips, fuelEntries, summary, year, month)
        document.writeTo(outputStream)
        document.close()
    }
    
    /**
     * Create PDF document with driver report data
     */
    private fun createPdfDocument(
        trips: List<TripEntity>,
        fuelEntries: List<FuelEntryEntity>,
        summary: DriverReportSummary,
        year: Int,
        month: Int
    ): PdfDocument {
        val document = PdfDocument()
        var pageNumber = 1
        
        // Define Paints
        val titlePaint = Paint().apply {
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }
        val sectionPaint = Paint().apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        val headerPaint = Paint().apply {
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
        }
        val textPaint = Paint().apply {
            textSize = 10f
        }
        val highlightPaint = Paint().apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        
        // Helper to start a new page
        fun startNewPage(): PdfDocument.Page {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            pageNumber++
            return document.startPage(pageInfo)
        }
        
        var page = startNewPage()
        var canvas = page.canvas
        var y = MARGIN.toFloat()
        
        // === Title ===
        canvas.drawText("Driver Earnings Report", MARGIN.toFloat(), y + 24, titlePaint)
        y += 48
        
        canvas.drawText("Period: ${month + 1}/$year", MARGIN.toFloat(), y, headerPaint)
        y += LINE_HEIGHT * 2
        
        // === Summary Section ===
        canvas.drawText("EARNINGS SUMMARY", MARGIN.toFloat(), y, sectionPaint)
        y += LINE_HEIGHT + 8
        
        // Summary box background
        val summaryBoxTop = y - 5
        canvas.drawRect(
            MARGIN.toFloat() - 5,
            summaryBoxTop,
            PAGE_WIDTH - MARGIN.toFloat() + 5,
            y + LINE_HEIGHT * 6 + 10,
            Paint().apply { color = 0xFFF5F5F5.toInt() }
        )
        
        canvas.drawText("Total Trips: ${summary.totalTrips}", MARGIN.toFloat(), y, textPaint)
        y += LINE_HEIGHT
        canvas.drawText("Total Bags Delivered: ${summary.totalBags}", MARGIN.toFloat(), y, textPaint)
        y += LINE_HEIGHT
        canvas.drawText("Total Distance: ${String.format("%.1f", summary.totalDistanceKm)} km", MARGIN.toFloat(), y, textPaint)
        y += LINE_HEIGHT
        canvas.drawText("Gross Earnings: ₹${String.format("%.2f", summary.grossEarnings)}", MARGIN.toFloat(), y, textPaint)
        y += LINE_HEIGHT
        canvas.drawText("Fuel Expenses: -₹${String.format("%.2f", summary.fuelCost)}", MARGIN.toFloat(), y, textPaint)
        y += LINE_HEIGHT + 8
        
        // Net earnings highlight
        canvas.drawText("NET EARNINGS: ₹${String.format("%.2f", summary.netEarnings)}", MARGIN.toFloat(), y, highlightPaint)
        y += LINE_HEIGHT * 2
        
        // === Trip Records Section ===
        canvas.drawText("TRIP RECORDS (${trips.size} trips)", MARGIN.toFloat(), y, sectionPaint)
        y += LINE_HEIGHT + 4
        
        // Trip table header
        fun drawTripHeader(c: android.graphics.Canvas, currentY: Float) {
            c.drawText("Date", MARGIN.toFloat(), currentY, headerPaint)
            c.drawText("Client", 100f, currentY, headerPaint)
            c.drawText("Bags", 250f, currentY, headerPaint)
            c.drawText("Rate", 300f, currentY, headerPaint)
            c.drawText("Earnings", 360f, currentY, headerPaint)
            c.drawText("Dist", 430f, currentY, headerPaint)
            c.drawLine(MARGIN.toFloat(), currentY + 5, PAGE_WIDTH - MARGIN.toFloat(), currentY + 5, textPaint)
        }
        
        if (trips.isNotEmpty()) {
            drawTripHeader(canvas, y)
            y += LINE_HEIGHT
            
            trips.forEach { trip ->
                // Check for page overflow
                if (y > PAGE_HEIGHT - MARGIN - 100) {
                    document.finishPage(page)
                    page = startNewPage()
                    canvas = page.canvas
                    y = MARGIN.toFloat()
                    
                    canvas.drawText("TRIP RECORDS (continued)", MARGIN.toFloat(), y, sectionPaint)
                    y += LINE_HEIGHT + 4
                    drawTripHeader(canvas, y)
                    y += LINE_HEIGHT
                }
                
                val earnings = trip.bagCount * trip.snapshotDriverRate
                
                canvas.drawText(DateUtils.formatDate(trip.tripDate), MARGIN.toFloat(), y, textPaint)
                canvas.drawText(trip.clientName.take(18), 100f, y, textPaint)
                canvas.drawText("${trip.bagCount}", 250f, y, textPaint)
                canvas.drawText("₹${trip.snapshotDriverRate.toInt()}", 300f, y, textPaint)
                canvas.drawText("₹${earnings.toInt()}", 360f, y, textPaint)
                canvas.drawText("${trip.snapshotDistanceKm?.toInt() ?: 0}km", 430f, y, textPaint)
                y += LINE_HEIGHT
            }
        } else {
            canvas.drawText("No trips recorded this month", MARGIN.toFloat(), y, textPaint)
            y += LINE_HEIGHT
        }
        
        y += LINE_HEIGHT
        
        // === Fuel Records Section ===
        // Check for page overflow before fuel section
        if (y > PAGE_HEIGHT - MARGIN - 150) {
            document.finishPage(page)
            page = startNewPage()
            canvas = page.canvas
            y = MARGIN.toFloat()
        }
        
        canvas.drawText("FUEL RECORDS (${fuelEntries.size} entries)", MARGIN.toFloat(), y, sectionPaint)
        y += LINE_HEIGHT + 4
        
        // Fuel table header
        fun drawFuelHeader(c: android.graphics.Canvas, currentY: Float) {
            c.drawText("Date", MARGIN.toFloat(), currentY, headerPaint)
            c.drawText("Amount", 120f, currentY, headerPaint)
            c.drawText("Liters", 200f, currentY, headerPaint)
            c.drawText("Station", 280f, currentY, headerPaint)
            c.drawLine(MARGIN.toFloat(), currentY + 5, PAGE_WIDTH - MARGIN.toFloat(), currentY + 5, textPaint)
        }
        
        if (fuelEntries.isNotEmpty()) {
            drawFuelHeader(canvas, y)
            y += LINE_HEIGHT
            
            fuelEntries.forEach { fuel ->
                // Check for page overflow
                if (y > PAGE_HEIGHT - MARGIN) {
                    document.finishPage(page)
                    page = startNewPage()
                    canvas = page.canvas
                    y = MARGIN.toFloat()
                    
                    canvas.drawText("FUEL RECORDS (continued)", MARGIN.toFloat(), y, sectionPaint)
                    y += LINE_HEIGHT + 4
                    drawFuelHeader(canvas, y)
                    y += LINE_HEIGHT
                }
                
                canvas.drawText(DateUtils.formatDate(fuel.entryDate), MARGIN.toFloat(), y, textPaint)
                canvas.drawText("₹${fuel.amount.toInt()}", 120f, y, textPaint)
                canvas.drawText(if (fuel.liters > 0) "${String.format("%.1f", fuel.liters)}L" else "-", 200f, y, textPaint)
                canvas.drawText((fuel.fuelStation ?: "-").take(25), 280f, y, textPaint)
                y += LINE_HEIGHT
            }
        } else {
            canvas.drawText("No fuel entries recorded this month", MARGIN.toFloat(), y, textPaint)
            y += LINE_HEIGHT
        }
        
        // Footer
        y = PAGE_HEIGHT - MARGIN.toFloat()
        canvas.drawText("Generated by FleetControl App", MARGIN.toFloat(), y, textPaint)
        
        document.finishPage(page)
        return document
    }
    
    /**
     * Export driver report to PDF file (fallback to internal storage)
     * Returns the file path
     */
    suspend fun exportDriverReport(
        trips: List<TripEntity>,
        fuelEntries: List<FuelEntryEntity>,
        summary: DriverReportSummary,
        year: Int,
        month: Int
    ): String {
        val fileName = getSuggestedFileName(year, month)
        val file = File(exportDir, fileName)
        
        val document = createPdfDocument(trips, fuelEntries, summary, year, month)
        
        FileOutputStream(file).use { outputStream ->
            document.writeTo(outputStream)
        }
        
        document.close()
        
        return file.absolutePath
    }
}
