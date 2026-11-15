> Historical release notes. See READY_VERIFICATION.md and CONSTRAINTS.md for the current filled-demo / GA update.

# UniPlan5 → UniPlan Simple

## Kept

- ProblemData, Candidate, Evaluation, Evaluator/DefaultEvaluator, Objectives, SearchEngine and interval-based ConstraintSearch.
- Independent feasibility/objective verification, seeded Random and Exhaustive baselines, CP-SAT, JSON persistence pattern and autosave.
- Week grid, filters, printable HTML, CSV and a single green palette.
- All eight existing JUnit tests, with new Simple-specific tests added. CLI experiments remain available.

## Removed from the product UI

- Semester dates/holidays/exam ranges and ICS.
- Workloads, preferred days, weights, raw tables and lock/rule editors.
- GA/Hybrid choices and parameter fields; experiment charts/tables.
- Multiple-semester portfolio switching, alternative timetables and manual drag/drop.
- Old Studio/Setup/RecordForm/TermEditor/ObjectiveEditor frontend classes and its old smoke harness. CalendarExport and legacy JSON/model helpers remain only because the retained tests/developer tools exercise them.

## Added

- Seven-screen SimpleApp with left stepper, typed labeled controls, inline errors, tooltips and editing dialogs.
- Fixed stages 1–4, editable group lists, per-group subjects with independent enrollment/session count/duration and optional instructor.
- Independent daily start/end/class length/breaks, copy action, live green/grey preview.
- SimpleProject adapter/document schema 1, explicit validation and fixed gap/balance weights.
- Friendly health report with linked fixes, CP default, 20-second limit, responsive progress and Cancel.
- 4-stage/8-group demo with 56 sessions, 10 classrooms and 4 labs.
- Subject IDs independent of names, unique internal placeholders for blank instructors, no false collision between all unassigned teachers.
- Screenshots, real test/launch logs, data dictionary, research/credits and a three-minute explanation.

## Bugs fixed

- JavaFX 21.0.2's duplicate Maven profile IDs generated six warnings: upgraded the existing JavaFX dependency to 21.0.9.
- JavaFX Maven plugin 0.0.8 drops OR-Tools native JARs with invalid automatic module names in non-modular projects. Its includePathExceptions setting does not fix that branch. Native resources now unpack from the same pinned OR-Tools version during process-resources, for the selected OS only; they are not put through module-name inference.
- OR-Tools 9.11's built-in loader assumes a jar resource URI; it cannot unpack a plain classes-directory URI. The small NativeSolver adapter loads the JNI library directly for file resources, and delegates to the original loader for jar resources. No search algorithm was rewritten.
- HTML used stale navy colors and called a weighted score “gap minutes.” It now derives colors from studio.css and labels the weighted objective correctly.
- Simple mode adds fixed blocked intervals for breaks/remainders to each existing resource NoOverlap constraint. Legacy policies with post-class rest retain their original domain semantics, so those tests continue to pass.
- Error handling now logs the actual cause of a failed background task, including native initialization errors, instead of showing only “null.”

## Intentional choices

Daily class length defines full teaching periods; subject duration can be shorter but cannot cross periods. Short remainders are unavailable. New subjects default to the first selected day's class length. Exact semantics are shown in the UI and README.

The new JSON schema does not silently import advanced UniPlan5 settings. The old reader remains in the code for legacy tests/tools. Four-stage Simple inputs and legacy UniPlan5 inputs remain separate documents.

## Targeted search/UI patch
- Random: constructive generator toggle (default on); 2,000-sample uniform diagnostic.
- Exhaustive: smallest-domain backtracking, conflict pruning, node counts and honest status.
- Added one-group six-session tiny demo and large-space warning.
- CP: 10% constructive warm start, full Boolean hints, editable workers (1–16), 60 s default.
- Kept model/scoring files and objective weights unchanged; empty days were weak soft quality.
- UI: 150 ms step fade, 8 px spacing, interaction states, stage colors, frozen grid axes.
- Task 4.1: multi-target subject copy, unique IDs and duplicate-name skipping; one JUnit test.
- Task 4.2: optional schema-1 result with input hash; evaluator-verified restoration; one JUnit test.
- Final: 25 tests pass; Random/tiny Exhaustive/JavaFX checks passed; measured logs in results/.
- CP seed 7: 20 s before 16560; 20 s after 9360; 60 s after 8640 (all feasible).
- Unfinished: Tasks 4.3–4.8. Windows and visual polish on a physical desktop unverified.

## Completed remaining features (supersedes earlier unfinished list)
- Pins: right-click Pin here/Unpin, Generate-page removal, existing Locks mapping, named conflict errors.
- Instructor days off: editable dialog, teacher-availability mapping and all-days-off warnings.
- Print / Save as PDF: stage/instructor HTML sections, landscape print CSS and default-browser opening.
- Joint classes: matching same-stage subjects share one row, summed enrollment and capacity validation.
- Manual moves: evaluator-approved alternatives, new score, one-step undo and pin protection.
- Three options: seeds 7/8/9, divided budget, day-count summaries and explicit final-choice export/save.
- Optional pins/daysOff/jointWith fields keep schema version 1; legacy JSON still opens.
- Verification: 31 JUnit tests pass; remaining-feature UI workflows pass without screenshots.
- Full demo: three CP options in 60.27 s, scores 9180/9360/8820, all with zero hard violations.
- Windows and actual browser Print/Save as PDF execution remain unverified; HTML generation is tested.
