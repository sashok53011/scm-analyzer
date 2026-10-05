package online.devhorizon.scm

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import online.devhorizon.scm.ui.ScmAnalyzerApp
import online.devhorizon.scm.ui.theme.ScmTheme

class MainActivity : AppCompatActivity() {
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
