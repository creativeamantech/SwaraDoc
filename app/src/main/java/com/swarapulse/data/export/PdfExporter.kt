package com.swarapulse.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.swarapulse.data.db.entity.Patient
import com.swarapulse.data.db.entity.Visit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class PdfExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun exportPatientReport(patient: Patient, visits: List<Visit>): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size roughly
        var page = document.startPage(pageInfo)
        var canvas: Canvas = page.canvas
        val paint = Paint().apply {
            textSize = 12f
        }

        var currentY = 50f

        // Page 1: Patient Info
        canvas.drawText("Patient Report: ${patient.name}", 50f, currentY, paint)
        currentY += 30f
        canvas.drawText("Age: ${patient.age}, Gender: ${patient.gender.name}", 50f, currentY, paint)
        currentY += 20f
        canvas.drawText("Mobile: ${patient.mobile}", 50f, currentY, paint)

        document.finishPage(page)

        // Pages 2+: Visits
        visits.forEachIndexed { index, visit ->
            val visitPageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 2).create()
            page = document.startPage(visitPageInfo)
            canvas = page.canvas
            currentY = 50f

            canvas.drawText("Visit Date: ${visit.dateTime}", 50f, currentY, paint)
            currentY += 30f
            canvas.drawText("Chief Complaint: ${visit.chiefComplaint}", 50f, currentY, paint)
            currentY += 20f
            canvas.drawText("Patient Element: ${visit.patientElement.name}, Nadi: ${visit.patientNadi.name}", 50f, currentY, paint)
            currentY += 20f
            canvas.drawText("Prescription: ${visit.prescription}", 50f, currentY, paint)

            document.finishPage(page)
        }

        val file = File(context.cacheDir, "patient_${patient.id}_report.pdf")
        document.writeTo(FileOutputStream(file))
        document.close()

        return file
    }
}
