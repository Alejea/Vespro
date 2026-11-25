package com.example.core

import android.content.Context
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

object VesproFirestoreProvider {
    @Volatile
    private var configuredDb: FirebaseFirestore? = null

    fun getInstance(context: Context): FirebaseFirestore {
        val existing = configuredDb
        if (existing != null) return existing

        return synchronized(this) {
            configuredDb ?: run {
                val appContext = context.applicationContext
                val databaseId = appContext.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                try {
                    db.firestoreSettings = firestoreSettings {
                        setLocalCacheSettings(persistentCacheSettings { })
                    }
                } catch (_: IllegalStateException) {
                    // Firestore instance already started; keep active settings
                }
                configuredDb = db
                db
            }
        }
    }
}
