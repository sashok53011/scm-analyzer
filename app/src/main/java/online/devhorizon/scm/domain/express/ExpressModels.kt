package online.devhorizon.scm.domain.express

import online.devhorizon.scm.data.llm.ProviderConfig
import online.devhorizon.scm.domain.model.AnalysisResult
import java.io.File

data class ModelReport(
    val provider: ProviderConfig,
    val result: AnalysisResult,
    val file: File,
)

data class ExpressOutcome(
    val superResult: AnalysisResult,
    val superFile: File,
    val modelReports: List<ModelReport>,
    val providersUsed: List<ProviderConfig>,
    val superHtml: String,
)
