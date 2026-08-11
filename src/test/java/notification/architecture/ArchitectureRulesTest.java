package notification.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the package dependency matrix documented in REVIEW.md (S-01). Runs on every
 * `mvn test`. Replaces the earlier standalone tools/ArchitectureTest.java + verify-architecture.sh
 * (a hand-rolled checker, written before this repo had a build tool at all — see REVIEW.md r3/r4).
 *
 * <p>The rule: domain is a dependency sink (nothing it needs from elsewhere in this codebase);
 * api/spi.* depend only on domain; core depends on api+domain+spi.*; infra.* implement spi.*
 * ports and depend on domain+spi.* (infra.memory additionally depends on core, for the
 * RecordHandler port — see REVIEW.md S-09); notification.boot, the composition root, is the one
 * package allowed to depend on everything, main and infra alike, and is the only package allowed
 * to import Spring.
 */
@AnalyzeClasses(packages = "notification", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    private static final String DOMAIN = "notification.domain..";
    private static final String API = "notification.api..";
    private static final String SPI = "notification.spi..";
    private static final String SPI_PORT = "notification.spi.port..";
    private static final String SPI_CHANNEL = "notification.spi.channel..";
    private static final String SPI_RESILIENCE = "notification.spi.resilience..";
    private static final String CORE = "notification.core..";
    private static final String INFRA_CHANNEL = "notification.infra.channel..";
    private static final String INFRA_RESILIENCE = "notification.infra.resilience..";
    private static final String INFRA_MEMORY = "notification.infra.memory..";
    private static final String BOOT = "notification.boot..";
    private static final String JAVA = "java..";

    @ArchTest
    static final ArchRule domain_depends_on_nothing_in_this_codebase =
            noClasses().that().resideInAPackage(DOMAIN)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            API, SPI, SPI_PORT, SPI_CHANNEL, SPI_RESILIENCE, CORE,
                            INFRA_CHANNEL, INFRA_RESILIENCE, INFRA_MEMORY, BOOT);

    @ArchTest
    static final ArchRule api_only_depends_on_domain =
            classes().that().resideInAPackage(API)
                    .should().onlyDependOnClassesThat().resideInAnyPackage(API, DOMAIN, JAVA);

    @ArchTest
    static final ArchRule spi_port_only_depends_on_domain =
            classes().that().resideInAPackage(SPI_PORT)
                    .should().onlyDependOnClassesThat().resideInAnyPackage(SPI_PORT, DOMAIN, JAVA);

    @ArchTest
    static final ArchRule spi_channel_only_depends_on_domain =
            classes().that().resideInAPackage(SPI_CHANNEL)
                    .should().onlyDependOnClassesThat().resideInAnyPackage(SPI_CHANNEL, DOMAIN, JAVA);

    @ArchTest
    static final ArchRule spi_resilience_only_depends_on_domain =
            classes().that().resideInAPackage(SPI_RESILIENCE)
                    .should().onlyDependOnClassesThat().resideInAnyPackage(SPI_RESILIENCE, DOMAIN, JAVA);

    @ArchTest
    static final ArchRule core_depends_only_on_api_domain_and_spi =
            classes().that().resideInAPackage(CORE)
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            CORE, API, DOMAIN, SPI, SPI_PORT, SPI_CHANNEL, SPI_RESILIENCE, JAVA);

    @ArchTest
    static final ArchRule infra_channel_depends_only_on_domain_and_its_spi =
            classes().that().resideInAPackage(INFRA_CHANNEL)
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            INFRA_CHANNEL, DOMAIN, SPI_CHANNEL, SPI_PORT, JAVA);

    @ArchTest
    static final ArchRule infra_resilience_depends_only_on_domain_and_its_spi =
            classes().that().resideInAPackage(INFRA_RESILIENCE)
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            INFRA_RESILIENCE, DOMAIN, SPI_RESILIENCE, JAVA);

    @ArchTest
    static final ArchRule infra_memory_depends_only_on_domain_spi_port_and_core =
            classes().that().resideInAPackage(INFRA_MEMORY)
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            INFRA_MEMORY, DOMAIN, SPI_PORT, CORE, JAVA);

    @ArchTest
    static final ArchRule only_boot_imports_spring =
            noClasses().that().resideOutsideOfPackage(BOOT)
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..");
}
