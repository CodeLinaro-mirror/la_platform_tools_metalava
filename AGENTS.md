# Metalava Agent Instructions

Further background and tips can be found at `go/metalava-hints-and-tips`.

## Branch and Merge Workflow

- **Always develop in `aosp/metalava-main`**: All Metalava changes must be authored, tested, and
  submitted on the `aosp/metalava-main` branch first (see `DOWNLOADING.md`). Do not make direct
  changes to `tools/metalava` on `aosp/main` or internal branches.
- **Rebase and resync to keep git history linear**:
  - Maintain a linear commit history on `aosp/metalava-main`. Never merge `aosp/metalava-main` into
    a local branch; always rebase local commits onto the latest upstream changes instead.
  - Before uploading or submitting changes, resync with upstream and rebase your branch onto the
    tip of `aosp/metalava-main` (e.g., via `repo sync` or `git fetch` and `git rebase`) so that
    changes can be submitted cleanly without creating merge commits.
- **Merging to `aosp/main` and downstream branches**: Once changes are merged into
  `aosp/metalava-main`, merge them into `aosp/main` (or other target branches) using
  `scripts/merge-from-metalava-main.sh`:
  ```bash
  scripts/merge-from-metalava-main.sh [source-branch]
  ```
  Do not manually `git merge` or cherry-pick individual commits when syncing branches; the script
  fetches the source branch, queries Gerrit for the merged CLs, extracts all `Bug:` trailers, and
  constructs the standard merge commit message.
- **Verifying in the Android Platform build**:
  - After merging into an Android platform checkout, run `m checkapi`.
  - To verify that a Metalava change does not cause unexpected differences in generated Android
    build artifacts (stubs, doc stubs, signature files, `updated-baseline.txt` compatibility
    baselines, JDiff XMLs, `api-versions.xml`, annotations, hiddenapi flags), run
    `scripts/gather-android-metalava-artifacts.py <before-dir>` before the change and
    `scripts/gather-android-metalava-artifacts.py <after-dir>` after the change, then diff the two
    directories.

## Commit Structure and Git Hygiene

- **Single logically coherent change per commit**: Each commit must contain a single logically
  coherent change that builds cleanly and passes all tests on its own.
- **Separate commits by purpose**: Plan and structure changes as a stack of small, self-contained
  commits ordered as:
  1. **Test commits**: Every bug fix or behavioral change—regardless of size—must have its tests
     added in a separate preceding commit, unless it is impossible to add the test before the
     change (e.g., when adding unit tests for new APIs). Always add **new** test cases
     demonstrating the current (pre-fix) behavior with a `// TODO(b/<bug>): ...` comment explaining
     what should change, rather than modifying existing tests.
  2. **Refactoring commits**: Extract helpers, rename symbols, or restructure code without changing
     behavior.
  3. **Functional change commits**: Implement the behavioral change and update the tests added in
     earlier commits (removing the temporary `TODO` comments) in the same commit as the fix. Tests
     may only be included in the same commit when it is impossible to add them beforehand (e.g.,
     unit tests for new APIs).
- **Preserve `Change-Id` trailers**:
  - Always preserve the existing `Change-Id` trailer when rewriting or modifying a commit (e.g.,
    using `git commit -C <commit>` or keeping the `Change-Id:` line).
  - When splitting an existing commit into multiple commits, preserve the original commit's
    `Change-Id` trailer on the primary commit so that it updates the existing Gerrit change.
- **Commit messages**:
  - Wrap commit message lines to 72 characters maximum.
  - Explain **why** the change is being made and the **impact** it has on existing behavior.
  - If a change is being made in preparation for follow-up changes (e.g., a preparatory refactoring
    or pre-fix test commit), state that explicitly and explain why it is needed so developers can
    understand how the commit fits within the stack of changes.
  - Include `Bug: <bug-id>`, `Test: <test-command>`, and `Change-Id: I...` trailers (enforced by
    `PREUPLOAD.cfg`).
  - Do **not** add `Flag:` trailers/labels as they are unnecessary for Metalava.

## API Reference Checking

- **Always use `ApiReferenceStrategy` to validate references to items**: Any code that needs to
  check whether a reference to a `SelectableItem` is valid must use `ApiReferenceStrategy` (obtained
  via `codebase.apiSurfaces.referenceStrategy`), calling `checkReference(from, to, kind, ...)` or
  `isValidReference(from, to, kind, ...)` with the appropriate `ReferenceKind`
  (`ReferenceKind.CODE` or `ReferenceKind.DOCUMENTATION`).
- **Do not check item visibility or surface state ad-hoc**: Never inspect `item.emit`,
  `item.selectedApi`, `item.isHiddenOrRemoved()`, visibility modifiers, `@doconly`, `@removed`, or
  `@FlaggedApi` annotations directly to determine whether a reference to an item is allowed.
  `ApiReferenceStrategy` is the single source of truth for reference validity across API surfaces,
  variants (`CORE`, `DOC_ONLY`, `REMOVED`), and `@FlaggedApi` addition/removal states.

## Documentation and Comments

- **KDoc required on declarations**: All new or modified classes, interfaces, objects, methods,
  functions, and properties must have KDoc comments associated with them (unless overriding a
  declaration whose supertype KDoc already fully describes its contract).
- **Comment non-trivial code blocks**: Any genuinely non-trivial block of code or logic must be
  accompanied by a comment explaining what it is doing and why.
- **Comments must be helpful, not noise**:
  - Do **not** write boilerplate or tautological comments that merely restate the symbol name,
    parameter names, type signature, or what the code obviously does.
  - Focus on information that is not immediately obvious from the code itself: **why** something is
    done, design rationale, invariants, edge cases, or how a component fits into the broader
    system.
  - Do **not** narrate straightforward code line-by-line.
  - Do **not** add comments to existing code that is not otherwise being modified.

## Building and Testing

- **Full build and verification**:
  ```bash
  ./gradlew
  ```
  This runs the default tasks (`installDist`, `test`, `publish`, `createArchive`, `lint`,
  `ktCheck`, etc.).
- **Running targeted tests**:
  ```bash
  ./gradlew :metalava:test --tests com.android.tools.metalava.SomeTest
  ./gradlew :metalava:test --tests "com.android.tools.metalava.SomeTest.Test method name*"
  ```
- **Updating test baselines**: If model test suite or source model provider baseline expectations
  change intentionally, run:
  ```bash
  scripts/refresh-baselines.py [project...]
  ```

## Formatting

- **Do not reformat existing code**: Never make formatting changes (such as adding trailing commas,
  adjusting blank lines, or reformatting comments/strings) to existing code unless that code is
  already being modified as part of the change. All formatting guidelines below apply **only** to
  new or modified code.
- **Formatters**:
  - **Kotlin**: Run `./gradlew ktFormat` to format and `./gradlew ktCheck` to verify (enforced by
    `PREUPLOAD.cfg`).
  - **Python scripts**: Format scripts under `scripts/` using:
    ```bash
    pyformat -s 4 --force_quote_type double -i scripts/<script>.py
    ```
  - **Blueprint files**: Format `Android.bp` files with `bpfmt`.
  - **`AGENTS.md` and Markdown files**: Wrap lines to 100 characters maximum and preserve this
    100-character line wrapping whenever editing `AGENTS.md`.
- **Minimize diffs and use trailing commas**:
  - Do **not** make unrelated formatting changes to existing code that is not being modified.
  - When writing new code or modifying existing code, format it to minimize diffs in subsequent
    commits. For example, if a commit adds a call to a method and a follow-up commit will add
    another argument to that call, include a trailing comma in the first commit to simplify the
    follow-up diff.
  - New or modified declarations and call sites for methods and constructors that take many optional
    parameters (such as `DriverTest.check(...)`) or that are likely to (or have a history of) having
    parameters added **must** use a trailing comma. Do **not** add trailing commas to untouched
    declarations or call sites.
- **Blank lines before comments**:
  - A comment should be preceded by a blank line **if and only if** the preceding non-blank line has
    the **same indentation** as the comment.
  - Do **not** put a blank line before a comment when the preceding line is less indented (for
    example, right after an opening brace `{` or parenthesis `(`).
  - Always put a blank line before a comment when the preceding line is at the same indentation
    level.
- **Multi-line strings (`trimIndent()`)**:
  - Multi-line strings (`"""..."""`) that are trimmed using `trimIndent()` — whether via an
    immediate `.trimIndent()` call or inside a helper/DSL property that calls `trimIndent()` before
    use (such as `signature(...)`, `java(...)`, `kotlin(...)`, `expectedIssues`, etc.) — **must**
    have:
    - The opening and closing `"""` quotes aligned at the same column.
    - The contents of the string indented by **4 spaces** relative to the `"""` quotes.
  - This alignment and 4-space content indentation must be maintained across `ktFormat`
    re-formatting and other code changes (since `ktFormat` does not re-indent the contents of raw
    strings when surrounding indentation changes).
  - Example:
    ```kotlin
    expectedIssues =
        """
            released-api.txt:5: error: Removed method [RemovedMethod]
        """
    ```

## Dependency Verification Metadata

- Whenever adding, removing, or upgrading Gradle dependencies (e.g. in `gradle/libs.versions.toml`
  or `build.gradle.kts`), update `gradle/verification-metadata.xml` and
  `gradle/verification-keyring.keys` by running:
  ```bash
  ./scripts/update-verification-metadata.sh
  ```
- **Never** edit `gradle/verification-metadata.xml` manually or run
  `./gradlew --write-verification-metadata` directly without the script.
  `scripts/update-verification-metadata.sh` backs up the Gradle state, resets
  `gradle/verification-metadata.xml` from `scripts/verification-metadata-template.xml` so stale
  entries do not accumulate, and strips volatile `version=` attributes from `<trusted-key>` entries.
- If you want to restore your previous Gradle build cache/state after running the script, run:
  ```bash
  ./scripts/restore-state.sh ~/metalava-build-state-backup
  ```
