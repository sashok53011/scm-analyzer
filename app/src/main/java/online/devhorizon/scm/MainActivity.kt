package online.devhorizon.scm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import online.devhorizon.scm.ui.ScmAnalyzerApp
import online.devhorizon.scm.ui.theme.ScmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScmTheme {
                ScmAnalyzerApp()
            }
        }
    }
}
