package com.referral.app.resume

/**
 * One list of skill names, shared by the resume parser, the profile form and the post-a-job form.
 * Matching in the database compares skill names exactly (case-insensitive), so both sides must
 * use the same spelling: "compose", "Jetpack Compose" and "jetpack-compose" all become "Jetpack Compose".
 *
 * Each entry: canonical name to regex alternatives (lowercase). Word edges are added by [Skills].
 * Ambiguous short words (Go, R, C, Swift as a verb…) are left out or need a qualifier on purpose.
 */
object Skills {

    private val DICTIONARY: List<Pair<String, List<String>>> = listOf(
        // Mobile
        "Android" to listOf("android"),
        "Kotlin" to listOf("kotlin"),
        "Jetpack Compose" to listOf("jetpack[ -]?compose", "compose ui", "(?<!docker )(?<!docker-)compose"),
        "Kotlin Coroutines" to listOf("coroutines?", "kotlin flows?"),
        "Swift" to listOf("swift(?! ?ui)"),
        "SwiftUI" to listOf("swiftui"),
        "iOS" to listOf("ios"),
        "Flutter" to listOf("flutter"),
        "Dart" to listOf("dart"),
        "React Native" to listOf("react[ -]native"),
        "Hilt" to listOf("hilt", "dagger[ -]?hilt"),
        "Dagger" to listOf("dagger ?2?"),
        "Room" to listOf("room (?:db|database|persistence)"),
        "Retrofit" to listOf("retrofit2?"),
        "MVVM" to listOf("mvvm"),
        "Clean Architecture" to listOf("clean architecture"),
        // Languages
        "Java" to listOf("java(?! ?script)", "core java", "java ?(?:8|11|17|21)"),
        "JavaScript" to listOf("javascript", "js(?=[ ,/]|$)", "es6"),
        "TypeScript" to listOf("typescript", "ts(?=[ ,/]|$)"),
        "Python" to listOf("python ?3?"),
        "Go" to listOf("golang", "go ?lang", "go \\(golang\\)"),
        "C++" to listOf("c\\+\\+", "cpp"),
        "C#" to listOf("c#", "c sharp", "csharp"),
        "Rust" to listOf("rust ?lang", "rust(?= |,|/|$)"),
        "PHP" to listOf("php"),
        "Ruby" to listOf("ruby(?! on rails)"),
        "Scala" to listOf("scala"),
        "SQL" to listOf("sql(?! ?server)", "pl/sql", "t-sql"),
        // Web / backend
        "React" to listOf("react(?:\\.?js)?(?![ -]native)"),
        "Angular" to listOf("angular(?:js)?(?: ?\\d+)?"),
        "Vue" to listOf("vue(?:\\.?js)?"),
        "Next.js" to listOf("next\\.?js"),
        "Node.js" to listOf("node[ .]?js"),
        "Express" to listOf("express[ .]?js"),
        "HTML" to listOf("html5?"),
        "CSS" to listOf("css3?", "scss", "sass"),
        "Tailwind CSS" to listOf("tailwind(?: ?css)?"),
        "Spring Boot" to listOf("spring[ -]?boot", "spring (?:framework|mvc|security|cloud)"),
        "Hibernate" to listOf("hibernate", "jpa"),
        "Django" to listOf("django"),
        "Flask" to listOf("flask"),
        "FastAPI" to listOf("fast ?api"),
        ".NET" to listOf("\\.net(?: core)?", "asp\\.net", "dotnet"),
        "Ruby on Rails" to listOf("ruby on rails", "rails"),
        "Laravel" to listOf("laravel"),
        "GraphQL" to listOf("graphql"),
        "REST APIs" to listOf("rest(?:ful)?(?: ?apis?| services| web services)", "restful"),
        "Microservices" to listOf("micro[ -]?services?"),
        "Kafka" to listOf("(?:apache )?kafka"),
        "RabbitMQ" to listOf("rabbit ?mq"),
        // Data
        "PostgreSQL" to listOf("postgres(?:ql)?"),
        "MySQL" to listOf("mysql"),
        "SQL Server" to listOf("sql ?server", "mssql"),
        "Oracle" to listOf("oracle(?: db| database)?"),
        "MongoDB" to listOf("mongo(?:db)?"),
        "Redis" to listOf("redis"),
        "Elasticsearch" to listOf("elastic ?search", "elk"),
        "Firebase" to listOf("firebase"),
        "Spark" to listOf("(?:apache |py)?spark"),
        "Pandas" to listOf("pandas"),
        "Machine Learning" to listOf("machine learning", "ml(?= |,|/|$)"),
        "Deep Learning" to listOf("deep learning"),
        "TensorFlow" to listOf("tensor ?flow"),
        "PyTorch" to listOf("py ?torch"),
        "Power BI" to listOf("power ?bi"),
        "Tableau" to listOf("tableau"),
        // Cloud / DevOps
        "AWS" to listOf("aws", "amazon web services"),
        "Azure" to listOf("(?:microsoft )?azure"),
        "GCP" to listOf("gcp", "google cloud(?: platform)?"),
        "Docker" to listOf("docker"),
        "Kubernetes" to listOf("kubernetes", "k8s"),
        "Terraform" to listOf("terraform"),
        "Jenkins" to listOf("jenkins"),
        "CI/CD" to listOf("ci ?/ ?cd", "github actions", "gitlab ci"),
        "Git" to listOf("git(?:hub|lab)?(?! actions| ci)"),
        "Linux" to listOf("linux", "unix"),
        // Testing / QA
        "Selenium" to listOf("selenium"),
        "Appium" to listOf("appium"),
        "JUnit" to listOf("junit ?\\d?"),
        "Espresso" to listOf("espresso"),
        "Manual Testing" to listOf("manual testing"),
        "Automation Testing" to listOf("automation testing", "test automation"),
        "Cypress" to listOf("cypress"),
        "Playwright" to listOf("playwright"),
        // Other
        "Figma" to listOf("figma"),
        "Agile" to listOf("agile", "scrum"),
        "JIRA" to listOf("jira"),
        "Salesforce" to listOf("salesforce"),
        "SAP" to listOf("sap(?: [a-z]{2,4})?(?= |,|/|$)"),
    )

    /** Letters/digits/+/# around a match mean it's part of a bigger word ("javascript" isn't "java"). */
    private const val BEFORE = "(?<![a-z0-9+#.])"
    private const val AFTER = "(?![a-z0-9+#])"

    private val patterns: List<Pair<String, Regex>> = DICTIONARY.map { (name, aliases) ->
        name to Regex(BEFORE + "(?:" + aliases.joinToString("|") + ")" + AFTER)
    }

    private val wholePatterns: List<Pair<String, Regex>> = DICTIONARY.map { (name, aliases) ->
        name to Regex("^(?:" + aliases.joinToString("|") + ")$")
    }

    val all: List<String> get() = DICTIONARY.map { it.first }

    /**
     * Skills mentioned in [text], most-mentioned first (ties keep dictionary order). At most [limit].
     */
    fun find(text: String, limit: Int = 20): List<String> {
        val lower = text.lowercase()
        return patterns
            .mapIndexedNotNull { i, (name, re) ->
                val n = re.findAll(lower).count()
                if (n > 0) Triple(name, n, i) else null
            }
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
            .take(limit)
            .map { it.first }
    }

    /** "compose" -> "Jetpack Compose"; unknown skills are kept as typed (trimmed). */
    fun canonical(raw: String): String {
        val t = raw.trim().replace(Regex("\\s+"), " ")
        val lower = t.lowercase()
        return wholePatterns.firstOrNull { (_, re) -> re.matches(lower) }?.first ?: t
    }

    /** Split a comma-separated field, canonicalise, drop blanks and duplicates (case-insensitive). */
    fun normalizeList(raw: String, limit: Int = 20): List<String> =
        raw.split(',', ';', '\n')
            .map { canonical(it) }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(limit)
}
