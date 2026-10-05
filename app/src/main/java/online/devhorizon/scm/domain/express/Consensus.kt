package online.devhorizon.scm.domain.express

import online.devhorizon.scm.domain.assess.Assessment
import online.devhorizon.scm.domain.model.Badge

data class ConsensusVerdict(
    val badge: Badge,
    val disputed: Boolean,
    val votes: Int,
    val total: Int,
    val note: String,
    val counts: Map<Badge, Int> = emptyMap(),
)

/** Majority vote across models; escalates to the most severe verdict when they disagree. */
object Consensus {

    private fun severity(b: Badge): Int = when (b) {
        Badge.PASS -> 0
        Badge.INFO -> 1
        Badge.WARNING -> 2
        Badge.VULNERABILITY -> 3
    }

    fun compute(
        providerIds: List<String>,
        elementId: String,
        verdicts: Map<String, Map<String, Assessment>>,
        fallback: Assessment,
    ): ConsensusVerdict {
        val votes = providerIds.mapNotNull { verdicts[it]?.get(elementId)?.badge }
        if (votes.isEmpty()) {
            return ConsensusVerdict(fallback.badge, disputed = false, votes = 0, total = providerIds.size, note = "Not reviewed by any model.")
        }
        val counts = votes.groupingBy { it }.eachCount()
        val majority = counts.maxByOrNull { it.value }!!.key
        val disputed = counts.size > 1
        val mostSevere = votes.maxByOrNull { severity(it) }!!
        val badge = if (disputed) mostSevere else majority
        val breakdown = counts.entries.sortedByDescending { severity(it.key) }
            .joinToString(", ") { "${it.value}× ${it.key.label}" }
        val note = if (disputed) {
            "Models disagree ($breakdown) — escalated to ${badge.label}."
        } else {
            "All ${votes.size} model(s) agree: ${badge.label}."
        }
        return ConsensusVerdict(badge, disputed, votes.size, providerIds.size, note, counts)
    }
}
