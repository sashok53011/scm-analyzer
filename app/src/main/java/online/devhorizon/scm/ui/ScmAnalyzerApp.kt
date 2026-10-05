package online.devhorizon.scm.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import online.devhorizon.scm.ui.screens.AnalyzeScreen
import online.devhorizon.scm.ui.screens.HomeScreen
import online.devhorizon.scm.ui.screens.ProvidersScreen
import online.devhorizon.scm.ui.screens.ReportScreen

object Routes {
    const val HOME = "home"
    const val ANALYZE = "analyze"
    const val PROVIDERS = "providers"
    const val REPORT = "report"
}

@Composable
fun ScmAnalyzerApp(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                onReady = { nav.navigate(Routes.ANALYZE) },
                onOpenProviders = { nav.navigate(Routes.PROVIDERS) },
            )
        }
        composable(Routes.ANALYZE) {
            AnalyzeScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onViewReport = { nav.navigate(Routes.REPORT) },
            )
        }
        composable(Routes.PROVIDERS) {
            ProvidersScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.REPORT) {
            ReportScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
