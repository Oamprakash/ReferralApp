package com.referral.app.resume

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Run with: ./gradlew :app:testDebugUnitTest */
class ResumeParserTest {

    private val android = """
        PRIYA RAMESH
        Senior Android Developer
        priya.ramesh@gmail.com | +91 98765 43210 | Chennai, Tamil Nadu
        linkedin.com/in/priyaramesh

        PROFESSIONAL SUMMARY
        Android developer with 6.5 years of experience building apps in Kotlin and Java.
        Led the migration to Jetpack Compose and Hilt. Strong in MVVM, Coroutines, Retrofit and Room database.

        SKILLS
        Kotlin, Java, Jetpack Compose, Coroutines, Hilt, Retrofit, Room DB, Firebase, Git, JUnit, Espresso

        EXPERIENCE
        Zoho Corporation — Senior Android Engineer (2021 – Present)
        Built Compose UI for the payments module; wrote JUnit and Espresso tests.
    """.trimIndent()

    private val backend = """
        Curriculum Vitae
        Name: arjun k. menon
        Email: ARJUN.MENON@Outlook.in
        Mobile: 09123456789
        Location: Bangalore

        Total Experience: 4 Yrs
        Spring Boot, Microservices, Kafka, PostgreSQL, Docker, Kubernetes, AWS, REST APIs, JavaScript, React.js
        Worked with the rest of the team on Node.js services. Docker Compose for local setup.
    """.trimIndent()

    @Test fun androidResume() {
        val r = ResumeParser.parse(android)
        assertEquals("Priya Ramesh", r.name)
        assertEquals("priya.ramesh@gmail.com", r.email)
        assertEquals("+919876543210", r.phone)
        assertEquals(6, r.experienceYears)
        assertEquals("Chennai", r.city)
        listOf("Android", "Kotlin", "Java", "Jetpack Compose", "Kotlin Coroutines", "Hilt", "Retrofit",
            "Room", "Firebase", "Git", "JUnit", "Espresso", "MVVM").forEach {
            assertTrue("missing $it in ${r.skills}", it in r.skills)
        }
        assertFalse("JavaScript must not come from Java", "JavaScript" in r.skills)
        assertEquals("most-mentioned first (3 each; ties keep list order)", listOf("Android", "Jetpack Compose"), r.skills.take(2))
    }

    @Test fun labelledFieldsAndAliases() {
        val r = ResumeParser.parse(backend)
        assertEquals("Arjun K. Menon", r.name)
        assertEquals("arjun.menon@outlook.in", r.email)
        assertEquals("+919123456789", r.phone)
        assertEquals(4, r.experienceYears)
        assertEquals("Bengaluru", r.city)
        listOf("Spring Boot", "Microservices", "Kafka", "PostgreSQL", "Docker", "Kubernetes", "AWS",
            "REST APIs", "JavaScript", "React", "Node.js").forEach {
            assertTrue("missing $it in ${r.skills}", it in r.skills)
        }
        assertFalse("'Java' is only inside JavaScript", "Java" in r.skills)
        assertFalse("'Docker Compose' isn't Jetpack Compose", "Jetpack Compose" in r.skills)
    }

    @Test fun phoneFormats() {
        fun p(s: String) = ResumeParser.parse(s).phone
        assertEquals("+919876543210", p("Ph: 9876543210"))
        assertEquals("+919876543210", p("(+91) 98765-43210"))
        assertEquals("+919876543210", p("+91-987-65-43210"))
        assertEquals("+919876543210", p("Phone 919876543210"))
        assertNull("landline-like / too short", p("Call 044 2345 6789"))
        assertNull("starts with 5", p("5876543210"))
        assertNull("part of a longer number", p("ID 1239876543210"))
    }

    @Test fun yearsTakesLargestBelievable() {
        fun y(s: String) = ResumeParser.parse(s).experienceYears
        assertEquals(8, y("8+ years of experience in QA. 3 years experience with Selenium."))
        assertEquals(5, y("Experience - 5 years"))
        assertEquals(3, y("3 yrs of hands-on Android experience"))
        assertNull(y("Graduated 2019. Worked at TCS."))
        assertNull("2019 is a year, not an amount", y("Experience since 2019 years"))
    }

    @Test fun nameSkipsHeadingsAndTitles() {
        val r = ResumeParser.parse("RESUME\nSoftware Engineer\nKarthik S\nkarthik@x.io")
        assertEquals("Karthik S", r.name)
        assertNull(ResumeParser.parse("Profile Summary\nAndroid Developer\n").name)
    }

    @Test fun emptyText() {
        assertTrue(ResumeParser.parse("").isEmpty)
    }

    @Test fun canonicalSkills() {
        assertEquals("Jetpack Compose", Skills.canonical(" compose "))
        assertEquals("Jetpack Compose", Skills.canonical("Jetpack-Compose"))
        assertEquals("Node.js", Skills.canonical("nodejs"))
        assertEquals("Kubernetes", Skills.canonical("K8s"))
        assertEquals("Some Niche Tool", Skills.canonical("Some  Niche Tool"))
        assertEquals(listOf("Kotlin", "Jetpack Compose", "Gradle"), Skills.normalizeList("kotlin, Compose, , KOTLIN, Gradle"))
    }
}
