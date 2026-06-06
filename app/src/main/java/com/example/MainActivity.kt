package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.data.database.AppDatabase
import com.example.data.repository.ScanRepository
import com.example.ui.screens.MainSecurityScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ScanViewModel
import com.example.ui.viewmodel.ScanViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Build Room DB connection & Repository injection elements
        val database = AppDatabase.getDatabase(this)
        val repository = ScanRepository(database.scanLogDao())
        
        val viewModel: ScanViewModel by viewModels {
            ScanViewModelFactory(repository)
        }
        
        setContent {
            MyApplicationTheme {
                MainSecurityScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
