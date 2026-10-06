package com.evlarus.spendinglimit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Architecture rules that the compiler cannot enforce. */
@AnalyzeClasses(packages = "com.evlarus.spendinglimit", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsPlainJava = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "org.jspecify.annotations..", "..domain..")
            .because("business rules must not depend on Spring, JPA or Jackson: they are tested without a context"
                    + " and persistence details (lazy loading, extra queries) cannot leak into them");

    @ArchTest
    static final ArchRule featuresHaveNoCycles = slices()
            .matching("com.evlarus.spendinglimit.(*)..")
            .should().beFreeOfCycles();
}
