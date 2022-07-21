package no.nav.eessi.pensjon.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.ImportOptions
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import no.nav.eessi.pensjon.EessiPensjonApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ArchitectureTest {

    private val rootDir = EessiPensjonApplication::class.qualifiedName!! .replace("." + EessiPensjonApplication::class.simpleName, "")

    // Only include main module. Ignore test module and external deps
    private val classesToAnalyze = ClassFileImporter()
            .importClasspath(
                    ImportOptions()
                            .with(ImportOption.DoNotIncludeJars())
                            .with(ImportOption.DoNotIncludeArchives())
                            .with(ImportOption.DoNotIncludeTests())
            )

    @BeforeAll
    fun beforeAll() {
        // Validate number of classes to analyze
        assertTrue(classesToAnalyze.size > 100, "Sanity check on no. of classes to analyze")
        assertTrue(classesToAnalyze.size < 250, "Sanity check on no. of classes to analyze")
    }

    @Test
    fun `Packages should not have cyclic depenedencies`() {
        slices().matching("$rootDir.(*)..").should().beFreeOfCycles().check(classesToAnalyze)
    }

    @Test
    fun `Klienter should not depend on eachother`() {
        slices().matching("..$rootDir.klienter.(**)").should().notDependOnEachOther().check(classesToAnalyze)
    }

    @Test
    fun `Check architecture`() {
        // Root packages
        val config = "Config"
        val health = "Health"
        val eux = "EUX"
        val gcp = "GCP"
        val listeners = "Listeners"
        val klienter = "Klienter"
        val filtering = "Filter"
        val validering = "Validering"
        val oppgaveRouting = "Oppgaverouting"
        val personidentifisering = "pdl.personidentifisering"

        layeredArchitecture()
                //Define components
                .layer(config).definedBy("$rootDir.config")
                .layer(health).definedBy("$rootDir.health")
                .layer(eux).definedBy("$rootDir.handler")
                .layer(gcp).definedBy("$rootDir.gcp")
                .layer(listeners).definedBy("$rootDir.listeners")
                .layer(klienter).definedBy("$rootDir.klienter..")
                .layer(oppgaveRouting).definedBy("$rootDir.oppgaverouting")
                .layer(personidentifisering).definedBy("$rootDir.personidentifisering")
                .layer(filtering).definedBy("$rootDir.pdl.filtrering")
                .layer(validering).definedBy("$rootDir.pdl.validering")
                //define rules
                .whereLayer(config).mayNotBeAccessedByAnyLayer()
                .whereLayer(health).mayNotBeAccessedByAnyLayer()
                .whereLayer(listeners).mayNotBeAccessedByAnyLayer()
                .whereLayer(klienter).mayOnlyBeAccessedByLayers(listeners, filtering, oppgaveRouting, validering)
                //Verify rules
                .check(classesToAnalyze)
    }

    @Test
    fun `avoid JUnit4-classes`() {
        val junitReason = "We use JUnit5 (but had to include JUnit4 because spring-kafka-test needs it to compile)"

        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.junit",
                        "org.junit.runners",
                        "org.junit.experimental..",
                        "org.junit.function",
                        "org.junit.matchers",
                        "org.junit.rules",
                        "org.junit.runner..",
                        "org.junit.validator",
                        "junit.framework.."
                ).because(junitReason)
                .check(classesToAnalyze)

        noClasses()
                .should()
                .beAnnotatedWith("org.junit.runner.RunWith")
                .because(junitReason)
                .check(classesToAnalyze)

        noMethods()
                .should()
                .beAnnotatedWith("org.junit.Test")
                .orShould().beAnnotatedWith("org.junit.Ignore")
                .because(junitReason)
                .check(classesToAnalyze)
    }
}
