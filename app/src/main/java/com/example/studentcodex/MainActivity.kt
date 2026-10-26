package com.example.studentcodex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.studentcodex.ui.StudentCodexApp
import com.example.studentcodex.ui.theme.StudentCodexTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as StudentCodexApplication).container
        setContent {
            StudentCodexTheme {
                StudentCodexApp(container = container)
            }
        }
    }
}
