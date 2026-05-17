package com.swarapulse.data.export

import android.content.Context
import com.swarapulse.data.db.entity.Patient
import com.swarapulse.data.db.entity.Visit
import dagger.hilt.android.qualifiers.ApplicationContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class ExcelExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun exportAllData(patients: List<Patient>, visits: List<Visit>): File {
        val workbook = XSSFWorkbook()

        // Sheet 1: Patient Info
        val patientSheet = workbook.createSheet("Patient Info")
        val patientHeaderRow = patientSheet.createRow(0)
        patientHeaderRow.createCell(0).setCellValue("ID")
        patientHeaderRow.createCell(1).setCellValue("Name")
        patientHeaderRow.createCell(2).setCellValue("Age")
        patientHeaderRow.createCell(3).setCellValue("Gender")
        patientHeaderRow.createCell(4).setCellValue("Mobile")

        patients.forEachIndexed { index, patient ->
            val row = patientSheet.createRow(index + 1)
            row.createCell(0).setCellValue(patient.id.toDouble())
            row.createCell(1).setCellValue(patient.name)
            row.createCell(2).setCellValue(patient.age.toDouble())
            row.createCell(3).setCellValue(patient.gender.name)
            row.createCell(4).setCellValue(patient.mobile)
        }

        // Sheet 2: All Visits
        val visitSheet = workbook.createSheet("All Visits")
        val visitHeaderRow = visitSheet.createRow(0)
        visitHeaderRow.createCell(0).setCellValue("Visit ID")
        visitHeaderRow.createCell(1).setCellValue("Patient ID")
        visitHeaderRow.createCell(2).setCellValue("Date Time")
        visitHeaderRow.createCell(3).setCellValue("Chief Complaint")
        visitHeaderRow.createCell(4).setCellValue("Patient Nadi")
        visitHeaderRow.createCell(5).setCellValue("Patient Element")

        visits.forEachIndexed { index, visit ->
            val row = visitSheet.createRow(index + 1)
            row.createCell(0).setCellValue(visit.id.toDouble())
            row.createCell(1).setCellValue(visit.patientId.toDouble())
            row.createCell(2).setCellValue(visit.dateTime.toString())
            row.createCell(3).setCellValue(visit.chiefComplaint)
            row.createCell(4).setCellValue(visit.patientNadi.name)
            row.createCell(5).setCellValue(visit.patientElement.name)
        }

        // Sheet 3: Analytics summary
        val analyticsSheet = workbook.createSheet("Analytics Summary")
        val analyticsHeaderRow = analyticsSheet.createRow(0)
        analyticsHeaderRow.createCell(0).setCellValue("Metric")
        analyticsHeaderRow.createCell(1).setCellValue("Value")

        val row1 = analyticsSheet.createRow(1)
        row1.createCell(0).setCellValue("Total Patients")
        row1.createCell(1).setCellValue(patients.size.toDouble())

        val row2 = analyticsSheet.createRow(2)
        row2.createCell(0).setCellValue("Total Visits")
        row2.createCell(1).setCellValue(visits.size.toDouble())

        val file = File(context.cacheDir, "swarapulse_backup.xlsx")
        FileOutputStream(file).use { outputStream ->
            workbook.write(outputStream)
        }
        workbook.close()

        return file
    }
}
