# 0013 — Java 21 and Maven instead of Kotlin and Gradle

- **Status:** Accepted
- **Date:** 2026-10-05

## Context

The blueprint was written in **Kotlin** (2.4) and built with **Gradle** (Kotlin DSL, a
`gradle/libs.versions.toml` version catalog). The maintainers asked for it to be ported to **Java 21**
and **Maven**. The port changes the language and the build tool only: the behaviour, the hexagonal
structure ([ADR-0002](0002-hexagonal-architecture-for-the-backend.md)), the contracts
([ADR-0003](0003-openapi-as-the-checked-in-contract.md)), the gates
([ADR-0004](0004-mutation-testing-as-a-blocking-pr-gate.md)), the BPMN models, the ports, the Bruno
suite and the `stack/` compose stay as they are.

## Decision

We write every module in **Java 21** and build it with **Maven**.

- **Language.** Records replace `data class`/`value class` for value objects, aggregates, DTOs and
  commands; JSpecify `@Nullable` marks what Kotlin expressed as `T?`, and port lookups that may find
  nothing return `Optional`. **SLF4J** replaces kotlin-logging. Kotlin's `require`/`error` become
  `IllegalArgumentException`/`IllegalStateException`, which the REST error mapping still turns into
  400/404.
- **Tests.** JUnit 5 + **Mockito** (`@MockitoBean` in `@WebMvcTest`) + AssertJ replace MockK /
  springmockk; each test keeps its original description via `@DisplayName`.
- **Build.** The Maven wrapper (`./mvnw`, Maven 3.10.0) drives a root `pom.xml` whose parent is
  `spring-boot-starter-parent` 4.1.1 and which aggregates the four `service/*` modules. Versions the
  Spring Boot BOM does not manage are pinned in the root `pom.xml` `<properties>` — the replacement for
  `gradle/libs.versions.toml`.
- **Source rules.** Konsist only reads Kotlin, so its source-structure rules move to **Checkstyle**
  ([ADR-0014](0014-archunit-and-checkstyle.md)); the ArchUnit suite keeps its scope.
- **Generated code is Java too.** The engine client comes from `openapi-generator-maven-plugin`
  (`java` generator, `restclient` library, not committed); the typed process API from
  `bpmn-to-code-maven` (`outputLanguage JAVA`, committed under `src/main/java`).

| Gradle (before) | Maven (now) |
|---|---|
| `./gradlew build` | `./mvnw verify` |
| `./gradlew :service:engine-service:bootRun` | `./mvnw -pl service/engine-service spring-boot:run` |
| `./gradlew :service:example-service:bootRun` | `./mvnw -pl service/example-service -am spring-boot:run` |
| `./gradlew :service:example-service:pitest -PmutationTargetClasses=…` | `./mvnw -pl service/example-service -am test-compile pitest:mutationCoverage -DtargetClasses=…` |
| `./gradlew :service:example-service:bootBuildImage` | `./mvnw -pl service/example-service -am -DskipTests package spring-boot:build-image-no-fork` |

## Consequences

- **Positive:** no Kotlin compiler plugins (`kotlin-spring`, `kotlin-jpa`) are needed — Spring proxies
  and JPA entities work on plain Java classes. PIT no longer sees Kotlin's synthetic bytecode
  (`value class` null checks, `data class` accessors), so those equivalent mutants are gone (ADR-0004).
- **Negative / trade-offs:** more code for the same behaviour — no default or named arguments, explicit
  constructors and getters on JPA entities, `LinkedHashMap` where Kotlin's ordered, null-tolerant
  `mapOf` was relied on. Null-safety is annotation-based: JSpecify documents intent, but javac does not
  enforce it the way Kotlin's type system did.
- **Neutral:** two observable details differ by design. The four body-less `202` endpoints return
  `ResponseEntity<Void>`, so the committed contract no longer lists an empty `"content": {}` for them
  (still no body). The not-found detail reads `Unknown application <uuid>` rather than Kotlin's
  `Unknown application ApplicationId(value=<uuid>)` (the old text was a by-product of Kotlin's
  `toString`).
- **Neutral:** the Kotlin standard library still arrives on the worker's classpath — transitively, via
  the Kotlin-built `bpmn-to-code-runtime` (a third-party dependency, not our code). Build output moves
  from `build/` to `target/`, the mutation report to `service/example-service/target/pit-reports`.

## Implementation notes

- The generated Java `ApiClient` builds absolute URLs from its **own** `basePath` and ignores the
  `RestClient`'s `baseUrl`, so the APIs are built from `new ApiClient(restClient).setBasePath(engineBaseUrl)`.
  `ProcessModelDeploymentAdapter` still uses a hand-built `RestClient` for the multipart deployment (see
  the [`common-cibseven-client`](../../service/common-cibseven-client/README.md) README).
- Only the two apps run and build images: the root `pom.xml` sets `spring-boot.run.skip` and
  `spring-boot.build-image.skip` to `true` and the apps flip them back (`skipPitest` likewise, flipped
  only in the worker), so a `-pl … -am` reactor run starts, images or mutates just the target app.
- IntelliJ names imported Maven modules by `artifactId`, so the run configurations under `run/`
  reference `engine-service` / `example-service`.
