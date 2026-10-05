package online.devhorizon.scm.domain.assess

import online.devhorizon.scm.domain.model.Badge
import online.devhorizon.scm.domain.model.CodeElement
import online.devhorizon.scm.domain.parse.SymbolKinds

data class Assessment(
    val badge: Badge,
    val summary: String,
    val detail: String,
    val source: String,
    /** Stable rule key (e.g. "hardcoded-secret") for localization; null for LLM results. */
    val ruleKey: String? = null,
)

internal data class Rule(
    val name: String,
    val badge: Badge,
    val regex: Regex,
    val summary: String,
    val detail: String,
)

/** Offline, deterministic quality/security rules. Provide column-4 content when the LLM is off. */
object StaticRules {

    private val rules: List<Rule> = listOf(
        Rule(
            "hardcoded-secret", Badge.VULNERABILITY,
            Regex("""(?i)(api[_-]?key|secret|password|passwd|passphrase|token|private[_-]?key|access[_-]?key)\s*[:=]\s*["'][^"']{6,}["']"""),
            "Possible hardcoded credential.",
            "A secret-looking value is assigned to a variable or property. Move it to secure storage / environment and rotate it.",
        ),
        Rule(
            "sql-injection", Badge.VULNERABILITY,
            Regex("""(?i)(select|insert|update|delete|where)\b[^\n]*(\+|\$\{|\{\}|%s|\.format\(|f"|f')"""),
            "Possible SQL injection via string concatenation.",
            "A SQL statement is assembled from dynamic values. Use parameterized queries / prepared statements.",
        ),
        Rule(
            "command-injection", Badge.VULNERABILITY,
            Regex("""(Runtime\.getRuntime\(\)\.exec|ProcessBuilder|os\.system|subprocess\.(run|call|Popen)[^\n]*shell\s*=\s*True|execSync|child_process)"""),
            "Possible command injection.",
            "A shell/system command is built from dynamic input. Validate input and avoid the shell where possible.",
        ),
        Rule(
            "code-eval", Badge.VULNERABILITY,
            Regex("""\b(eval|exec|Function)\s*\(|pickle\.loads|yaml\.load\(|ObjectInputStream|Marshal\.load"""),
            "Dynamic code / unsafe deserialization.",
            "Executing code or deserializing untrusted data can lead to remote code execution. Use safe parsers and avoid eval.",
        ),
        Rule(
            "tls-disable", Badge.VULNERABILITY,
            Regex("""(?i)(trustAllCerts|ALLOW_ALL_HOSTNAME_VERIFIER|hostnameVerifier\s*=\s*\{\s*true|checkServerTrusted[^\n]*\{\s*\}|X509TrustManager[^\n]*return\s*null|InsecureSkipVerify\s*:\s*true)"""),
            "TLS verification disabled.",
            "Certificate or hostname verification is bypassed, enabling man-in-the-middle attacks. Remove this override.",
        ),
        Rule(
            "xss", Badge.WARNING,
            Regex("""(dangerouslySetInnerHTML|\.innerHTML\s*=|document\.write\(|v-html)"""),
            "Potential XSS sink.",
            "Untrusted data written into the DOM can execute scripts. Sanitize input or use safe text APIs.",
        ),
        Rule(
            "weak-crypto", Badge.WARNING,
            Regex("""(?i)\b(md5|sha-?1|des|rc4|ecb)\b"""),
            "Weak cryptography algorithm.",
            "MD5/SHA-1/DES/RC4 are broken for security purposes. Use SHA-256+ and AES-GCM.",
        ),
        Rule(
            "insecure-random", Badge.WARNING,
            Regex("""new\s+Random\s*\(|Math\.random\s*\(|random\.randint\s*\("""),
            "Non-cryptographic randomness.",
            "Random values used for tokens or security decisions should come from a cryptographically secure source.",
        ),
        Rule(
            "cleartext-http", Badge.WARNING,
            Regex("""http://(?!localhost|127\.0\.0\.1|0\.0\.0\.0|10\.|192\.168\.|\[::1\])"""),
            "Cleartext HTTP endpoint.",
            "Traffic over http:// is unencrypted. Prefer https:// to protect data in transit.",
        ),
        Rule(
            "empty-catch", Badge.WARNING,
            Regex("""catch\s*\([^)]*\)\s*\{\s*\}"""),
            "Empty catch block swallows errors.",
            "Silently ignoring exceptions hides failures. Log or handle the error explicitly.",
        ),
        Rule(
            "todo-marker", Badge.WARNING,
            Regex("""\b(TODO|FIXME|XXX|HACK)\b"""),
            "Unresolved TODO/FIXME.",
            "Outstanding work marker found; verify it does not represent incomplete or unsafe behavior.",
        ),
        Rule(
            "debug-output", Badge.WARNING,
            Regex("""(System\.out\.println|console\.log\(|printStackTrace\(|Debug\.Log)"""),
            "Debug output in code.",
            "Leftover debug logging can leak information or hurt performance in production.",
        ),
        Rule(
            "insecure-cors", Badge.WARNING,
            Regex("""Access-Control-Allow-Origin["']?\s*[:=]\s*["']\*"""),
            "Permissive CORS policy.",
            "A wildcard CORS origin allows any site to read responses. Restrict to trusted origins.",
        ),
    )

    fun assess(element: CodeElement): Assessment {
        if (element.kind == SymbolKinds.BLANK) {
            return Assessment(
                badge = Badge.INFO,
                summary = "Blank line(s) — no code to assess.",
                detail = "These lines carry no executable content; they are listed to keep line coverage complete.",
                source = "static:blank",
                ruleKey = "blank",
            )
        }
        val code = element.code
        for (rule in rules) {
            if (rule.regex.containsMatchIn(code)) {
                return Assessment(
                    badge = rule.badge,
                    summary = rule.summary,
                    detail = rule.detail,
                    source = "static:${rule.name}",
                    ruleKey = rule.name,
                )
            }
        }
        return Assessment(
            badge = Badge.PASS,
            summary = "No issues detected by static rules.",
            detail = "No known anti-pattern matched in this snippet. Enable LLM review for a deeper semantic assessment.",
            source = "static:clean",
            ruleKey = "clean",
        )
    }
}
