package no.nav.eessi.pensjon.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import no.nav.eessi.pensjon.EessiPensjonApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

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

    @Test @Disabled("TODO Fix when we have found a good structure")
    fun `Check architecture`() {
        // Root packages
        val config = "Config"
        val health = "Health"
        val eux = "EUX"
        val gcp = "GCP"
        val pdlOppdatering = "PdlOppdatering"
        val klienter = "Klienter"
        val validering = "Validering"
        val oppgaveRouting = "Oppgaverouting"
        val personidentifisering = "pdl.personidentifisering"

        layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage(root)
                //Define components
                .layer(config).definedBy("$root.config")
                .layer(health).definedBy("$root.health")
                .layer(eux).definedBy("$root.handler")
                .layer(gcp).definedBy("$root.gcp")
                .layer(pdlOppdatering).definedBy("$root.pdl.oppdatering")
                .layer(klienter).definedBy("$root.klienter..")
                .layer(oppgaveRouting).definedBy("$root.oppgaverouting")
                .layer(personidentifisering).definedBy("$root.personidentifisering")
                .layer(validering).definedBy("$root.pdl.validering")
                //define rules
                .whereLayer(config).mayNotBeAccessedByAnyLayer()
                .whereLayer(health).mayNotBeAccessedByAnyLayer()
                .whereLayer(pdlOppdatering).mayNotBeAccessedByAnyLayer()
                .whereLayer(klienter).mayOnlyBeAccessedByLayers(pdlOppdatering, oppgaveRouting, validering)
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
