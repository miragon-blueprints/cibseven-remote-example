import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springframework)
    alias(libs.plugins.spring.dependency)
}

configurations.all {
    // CIB seven 2.2.0 pulls in both the generic `cibseven-webclient-web` and the Spring-Boot-4
    // variant `cibseven-webclient-web-spring-boot-4`. The generic one calls
    // PathMatchConfigurer.setUseSuffixPatternMatch(...), which was removed in Spring 7 / Spring
    // Boot 4, and crashes the app on start-up. Drop it so only the SB4 variant remains.
    exclude(group = "org.cibseven.webapp", module = "cibseven-webclient-web")
}

/**
 * The engine host. It only runs the CIB seven engine and *deploys* the process contract from
 * `common-package` (BPMN/DMN/forms are picked up from that module's jar via the classpath deployment
 * pattern). It carries no business logic: the service tasks are external tasks handled remotely by the
 * `example-service` worker. Cockpit/Tasklist are exposed at `/camunda`.
 */
dependencies {
    implementation(project(":service:common-package"))
    implementation(libs.bundles.defaultService)
    implementation(libs.bundles.database)
    implementation(libs.bundles.cibseven)
    testImplementation(libs.bundles.test)
    testImplementation(libs.bundles.cib7ProcessTest)
}

tasks.test {
    useJUnitPlatform()
    // Each process test drives a fresh in-memory engine; fork per test class to isolate engine state.
    forkEvery = 1
}

tasks.withType<BootJar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

java.sourceCompatibility = JavaVersion.VERSION_21
