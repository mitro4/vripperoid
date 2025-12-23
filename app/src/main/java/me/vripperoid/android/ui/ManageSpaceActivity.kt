package me.vripperoid.android.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.File

class ManageSpaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // This activity is called when user selects "Clear Data" in system settings if manageSpaceActivity is defined
        // or we can assume the intent is to clear cache.
        // However, standard Android "Uninstall" does not trigger this.
        // There is no standard way to run code ON UNINSTALL.
        // The closest is android:manageSpaceActivity which adds a button to "Clear Data" screen.
        
        // But the request was: "In the process of uninstall... force clear cache".
        // Android creates a sandbox for each app. When an app is uninstalled, Android AUTOMATICALLY deletes:
        // 1. Internal storage (files, cache, databases)
        // 2. External storage (private directories in Android/data/package_name)
        
        // Files saved via SAF (Custom Location) or MediaStore (Public Pictures) are NOT deleted by default, 
        // and they SHOULD NOT be deleted automatically on uninstall as they are user data.
        
        // If the user meant "Clear application cache/data" manually:
        try {
            cacheDir.deleteRecursively()
            externalCacheDir?.deleteRecursively()
            // We can't delete shared prefs or databases easily while running without killing self,
            // but the system handles this on "Clear Data".
            
            Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        finish()
    }
}
