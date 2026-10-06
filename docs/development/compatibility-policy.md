# Multiversion Compatibility Policy

Use this document to decide how a Minecraft-version or loader difference should be represented.

The goal is not “maximum shared code.” The goal is readable behavior, low accidental duplication, predictable processing, and a future agent being able to find the version difference without reverse-engineering Gradle string manipulation.

## Decision Order

Use the first mechanism that fits.

### 1. No compatibility code

If the same source/resource works on all affected targets, keep it unchanged.

Never add a branch for a difference that has not been observed.

### 2. Native Stonecutter source condition

Use when the difference is local and both variants remain easy to understand beside each other.

Good cases:

- import/package move;
- method rename;
- one changed argument;
- short alternate call;
- one annotation/interface difference;
- local target class/method difference;
- small loader/version branch.

This is the default for ordinary API drift.

Do not let a class become dominated by conditional blocks.

### 3. Narrow deterministic replacement

Use only for truly mechanical drift.

Requirements:

- narrow scope;
- deterministic;
- behavior-preserving;
- exact enough that the expected match can be asserted;
- reversible/bidirectional behavior understood when Stonecutter applies replacements both ways;
- not dependent on fragile formatting when avoidable.

Do not use a broad package-prefix or regex rewrite because several classes happen to look similar.

### 4. Parsed resource/data transform

Use when behavior is the same but a serialized format changed.

Examples:

- recipe ingredient representation;
- loader metadata fields;
- item definition/model schema;
- advancement icon representation;
- data component/predicate layout.

Rules:

- parse structured data when practical;
- scope by target/generation;
- preserve unaffected fields;
- make the transform deterministic;
- verify processed output;
- package and run a resource/gameplay check when the result is user-visible.

Do not turn JSON transformation into Java source transformation.

### 5. Separate compatibility implementation

Use a real source/resource file when the old and new implementations are materially different.

Good reasons:

- renderer architecture changed;
- menu/screen lifecycle changed;
- entity model/render-state design changed;
- large portions of a class require different imports/types;
- mixin injection strategy is fundamentally different;
- a large conditional block would be harder to read than two implementations.

Keep the compatibility area small. Override only the files that truly diverge.

Example:

```text
gradle/compat/
  legacy/
    common/
    fabric/
    neoforge/
```

Do not copy the canonical tree wholesale.

## What Not to Build

Do not make the normal architecture:

```text
build.matrix.gradle
→ read Java source as text
→ broad regex replacements
→ inject multiline Java strings
→ compile generated Java
```

A tiny verified mechanical replacement may exist, but Gradle must not become the place where application behavior is hidden.

Version behavior should be visible in:

- canonical Java;
- Stonecutter conditions;
- clearly named compatibility source;
- deterministic resource transforms.

## When to Split a Class

Prefer a separate implementation when:

- conditions repeat throughout the file;
- the two versions use different lifecycle models;
- imports/types differ across most of the implementation;
- a large block is alternate behavior;
- mixin descriptors/targets are fundamentally different;
- the conditional version is harder to review than two files.

There is no fixed line-count threshold.

## When Not to Split

Do not create an alternate class merely because:

- one import moved;
- one method was renamed;
- one constructor gained an argument;
- one constant changed location;
- one registration call has a short old/new form.

Those are prime Stonecutter-condition cases.

## Loader Differences

Minecraft version and loader are separate compatibility axes.

If only Fabric differs, prefer Fabric source.

If only NeoForge differs, prefer NeoForge source.

Do not put loader branching in common source when loader-specific source can own the difference cleanly.

Do not force both loaders through an abstraction that is harder to understand than two small native implementations.

## Mixins

Compilation is weak evidence for mixins.

When a mixin changes across versions/loaders, inspect:

- class existence;
- method name;
- descriptor;
- actual dispatch/override path;
- injection point;
- target-side remapping;
- whether the mixin should exist at all on that target.

A method with the same name on a superclass is not proof that the game dispatches through that superclass.

Use local conditions for a small target/descriptor difference. Use a compatibility mixin when the injection strategy itself changes.

## Optional Integrations

Optional compatibility must remain optional.

Rules:

- no accidental required loader metadata;
- no accidental jar-in-jar/bundling;
- no direct dependency declaration for a transitive dependency that users do not independently need;
- development runtimes may attach compatible optional mods;
- test supported integration present and absent;
- a detected but incompatible reflection/API shape should warn clearly rather than silently pretending support.

If an optional material/item is absent, user-facing integrations such as JEI must filter invalid/air placeholders rather than requiring the optional mod.

## Resource Compatibility

A resource transform is not accepted merely because packaging succeeds.

For transformed models/recipes/advancements/metadata:

1. inspect processed output;
2. verify the release JAR contains the intended file;
3. run resource reload or relevant gameplay/visual evidence;
4. reject self-referential or structurally valid-but-broken output.

## Runtime Dependency ABI

A target can compile while failing because the loader/runtime bundles a different supporting-library ABI.

When a crash implicates annotations, MixinExtras, loader-bundled libraries, or similar runtime components:

1. identify the actual runtime versions;
2. compare the known-good/known-bad loader combination;
3. change the narrow runtime pin when the incompatibility is proven;
4. do not “fix” application source around a loader ABI bug without evidence.

## Compatibility Rule Log

Record only durable, proven boundaries.

Use:

```text
Boundary:
Affected targets:
Affected files/system:
Mechanism:
Reason:
Validation:
```

Keep one-off compiler typos and raw logs out of the policy.

## Proven Sagittary Examples

These are examples of the decision process, not universal Minecraft rules.

- Small modern moves such as entity constants, changed method arguments, and a hand-render method rename were handled with local conditions.
- Legacy item/use/tooltip and registry differences were handled locally rather than by copying whole classes.
- Legacy rendering/GUI architecture that materially diverged used a small number of alternate implementations.
- Legacy serialized recipe/model/advancement formats use parsed transforms.
- Legacy/current Fletching Table interaction required checking the actual dispatch path: the legacy subclass override had to be targeted rather than assuming the base method was sufficient.
- Optional JEI entries filter unavailable optional materials instead of turning Spelunkery into a hard dependency.
- Standalone matrix nodes still need the Architectury API dependency but do not automatically need the old Architectury Gradle transformation layer.
- Original Trinkets and Trinkets Updated are different integration boundaries; legacy NeoForge intentionally has no Trinkets-family provider in the current support policy.

Detailed historical discoveries belong in `history/sagittary-migration-notes.md`, not in the default policy.
