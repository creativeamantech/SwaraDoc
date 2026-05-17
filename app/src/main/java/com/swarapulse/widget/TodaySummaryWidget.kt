package com.swarapulse.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class TodaySummaryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // In a real app we would load actual counts from DB using a repository/use case
        val appointmentCount = 5
        val followupCount = 2

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Today's Summary", style = TextStyle(fontWeight = androidx.glance.text.FontWeight.Bold))
                Spacer(GlanceModifier.height(8.dp))
                Row {
                    Text("Appointments: $appointmentCount")
                }
                Row {
                    Text("Follow-ups: $followupCount")
                }
            }
        }
    }
}

class TodaySummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodaySummaryWidget()
}
