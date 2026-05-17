package com.swarapulse.di

import android.content.Context
import androidx.room.Room
import com.swarapulse.data.db.SwaraPulseDatabase
import com.swarapulse.data.db.dao.AppointmentDao
import com.swarapulse.data.db.dao.DraftVisitDao
import com.swarapulse.data.db.dao.PatientDao
import com.swarapulse.data.db.dao.VisitDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSwaraPulseDatabase(
        @ApplicationContext context: Context
    ): SwaraPulseDatabase {
        return Room.databaseBuilder(
            context,
            SwaraPulseDatabase::class.java,
            "swarapulse_db"
        ).build()
    }

    @Provides
    fun providePatientDao(database: SwaraPulseDatabase): PatientDao = database.patientDao()

    @Provides
    fun provideVisitDao(database: SwaraPulseDatabase): VisitDao = database.visitDao()

    @Provides
    fun provideAppointmentDao(database: SwaraPulseDatabase): AppointmentDao = database.appointmentDao()

    @Provides
    fun provideDraftVisitDao(database: SwaraPulseDatabase): DraftVisitDao = database.draftVisitDao()
}
