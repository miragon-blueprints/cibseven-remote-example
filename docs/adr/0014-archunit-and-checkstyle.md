# 0014 — Two architecture-test tools: ArchUnit (bytecode) and Checkstyle (source)

- **Status:** Accepted
- **Date:** 2026-10-05

## Context

The hexagonal rules are machine-enforced ([ADR-0002](0002-hexagonal-architecture-for-the-backend.md)),
and the suite that enforces them runs **two** tools — ArchUnit *and* Checkstyle. This record supersedes
[ADR-0007](0007-two-architecture-test-tools-archunit-and-konsist.md), which paired ArchUnit with
Konsist: Konsist reads only Kotlin source, so after the port to Java
([ADR-0013](0013-java-and-maven.md)) its rules need a new home. Running two tools for "architecture
tests" still looks redundant, so the choice needs a reason.

It comes down to what each tool can *see*:

- **ArchUnit** inspects compiled **bytecode**. It reasons about the resolved type and dependency graph
  — which package depends on which, whether a class is an interface, how many constructor parameters of
  a given type a class has. It cannot see facts that compilation erases — above all import style (a
  wildcard and an explicit import produce the same bytecode). How source is split into files is only
  indirectly visible (javac emits one class file per type and records its source file name), so a
  "one type per file" rule *could* be bent into an ArchUnit rule, but it is a source-shape rule and
  reads far more naturally where the source is.
- **Checkstyle** inspects Java **source** (the parsed syntax tree, file by file). It sees exactly those
  source-level facts — files, imports, top-level type declarations — that ArchUnit has lost. Without
  type resolution it is not the right tool for reasoning about the resolved dependency graph.

Each is blind to the other's domain; neither alone covers both.

## Decision

We use **both**, each for the rules it expresses naturally (all fail `./mvnw verify`):

- **ArchUnit (bytecode)** — unchanged in scope, in `service/common-architecture-tests`: the
  layered-dependency graph and naming/suffix rules (`HexagonalArchitectureTest`,
  `NamingConventionArchitectureTest`, `ServiceArchitectureTest`) plus basic coding guidelines
  (`BasicCodingGuidelinesTest`: no `println`/`System.out`, no package cycles). A service wires it in
  with one small class: `class ArchitectureTest extends ServiceArchitectureTest { ArchitectureTest() { super("io.miragon.blueprint"); } }`.
- **Checkstyle (source)** — conventions the bytecode no longer carries, in
  `config/checkstyle/source-guidelines.xml`: `OuterTypeNumber` = 1 (one top-level class, interface,
  record or enum per file; nested types are fine) and `AvoidStarImport` (no wildcard imports, `java.util`
  exempt). `maven-checkstyle-plugin` runs it from the root `pom.xml` in the **`validate`** phase on
  every module, main **and** test sources, and fails the build on any violation.

The Checkstyle configuration is deliberately minimal — the rules the architecture relies on, not a
style guide.

## Consequences

- **Positive:** each rule is written against the representation where it is natural and cheap — no
  contorting a source-shape rule through bytecode, or a dependency rule through text. The source rules
  run in `validate`, before compilation, so a violation fails fast.
- **Negative / trade-offs:** two configurations to learn and keep current (ArchUnit's Java API and a
  Checkstyle XML), and a contributor has to know which tool owns which kind of rule. The source rules
  are no longer part of the reusable `common-architecture-tests` dependency: they live in the root
  build, so a service outside this reactor has to bring the plugin configuration along.
- **Neutral:** this is the deliberate **ceiling**, not a starting point. New structural rules go into
  whichever of these two fits — we do **not** add a third architecture/guardrail framework on top.

## Implementation notes

- Because Checkstyle runs in `validate`, before code generation, the generated engine client under
  `service/common-cibseven-client/target/` is not checked; the committed `*ProcessApi` files under
  `service/example-service/src/main/java` are.
