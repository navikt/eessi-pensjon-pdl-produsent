package no.nav.eessi.pensjon.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import no.nav.eessi.pensjon.EessiPensjonApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.web.bind.annotation.RestController

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ArchitectureTest {

    private val root = EessiPensjonApplication::class.qualifiedName!! .replace("." + EessiPensjonApplication::class.simpleName, "")

    // Only include main module. Ignore test module and external deps
    private val classesToAnalyze = ClassFileImporter()
        .withImportOptions(listOf(
            ImportOption.DoNotIncludeJars(),
            ImportOption.DoNotIncludeArchives(),
            ImportOption.DoNotIncludeTests()))
        .importPackages(root)

    @BeforeAll
    fun beforeAll() {
        // Validate number of classes to analyze
        assertTrue(classesToAnalyze.size in 80..250, "Sanity check on no. of classes to analyze (is ${classesToAnalyze.size})")
    }

    @Test
    fun `Packages should not have cyclic depenedencies`() {
        slices().matching("$root.(*)..").should().beFreeOfCycles().check(classesToAnalyze)
    }

    @Test
    fun `Klienter should not depend on eachother`() {
        slices().matching("..$root.klienter.(**)").should().notDependOnEachOther().check(classesToAnalyze)
    }

    @Test
    fun `controllers should not call each other`() {
        classes().that()
            .areAnnotatedWith(RestController::class.java)
            .should().onlyHaveDependentClassesThat().areNotAnnotatedWith(RestController::class.java)
            .because("Controllers should not call each other")
            .check(classesToAnalyze)
    }

    @Test
    fun `Check architecture`() {
        // Root packages
        val config = "Config"
        val health = "Health"
        val gcp = "GCP"
        val klienter = "Klienter"
        val validering = "Validering"
        val oppgaveRouting = "Oppgaverouting"
        val personidentifisering = "personidentifisering"
        val adresseidentifisering = "adresseoppdatering"
        val gjenlevidentifisering = "pdl.identoppdateringgjenlev"

        layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage(root)
                //Define components
                .layer(config).definedBy("$root.config")
                .layer(health).definedBy("$root.shared.api.health")
                .layer(gcp).definedBy("$root.gcp")
                .layer(klienter).definedBy("$root.klienter..")
                .layer(oppgaveRouting).definedBy("$root.oppgaverouting")
                .layer(personidentifisering).definedBy("$root.pdl.identoppdatering")
                .layer(adresseidentifisering).definedBy("$root.pdl.adresseoppdatering")
                .layer(gjenlevidentifisering).definedBy("$root.pdl.identoppdateringgjenlev")
                .layer(validering).definedBy("$root.pdl.validering")
                //define rules
                .whereLayer(config).mayNotBeAccessedByAnyLayer()
                .whereLayer(health).mayNotBeAccessedByAnyLayer()
                .whereLayer(klienter).mayOnlyBeAccessedByLayers(oppgaveRouting, validering)
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
