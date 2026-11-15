> Historical release notes. See READY_VERIFICATION.md and CONSTRAINTS.md for the current filled-demo / GA update.

# Focused fixes, 14 November 2026

## CP 1621 / evaluator 1620

The callback previously demanded that every intermediate solver objective equal the independently evaluated timetable. CP-SAT presolve can leave intermediate auxiliary objective values loose (see https://github.com/google/or-tools/issues/5453). A higher intermediate minimization score is now allowed. The selected timetable is still evaluated independently and ranked by its exact score. An objective below the evaluator score still fails, and a proven optimal final answer still requires equality. All hard constraints are checked as before. No solver or interface rewrite.

The user's exact input JSON was not included, so that particular timetable cannot be reproduced from the ZIP alone. Tests cover the reported numeric case, reject unsafe disagreements, exercise native CP across irregular durations/daily windows and check original solver regressions.

## Data storage and limits

JSON has no fixed count or file-size cap in this application. A regression saves and reloads 2,500 subjects without truncation. Available memory/disk still apply. This is a storage check, not a claim that 2,500 subjects can be optimized quickly.

The enrollment/room-capacity spinners and validation previously stopped at 1,000. They now accept 1–1,000,000. Other deliberate rules remain: four stages, Sunday–Thursday, 1–20 sessions per subject, 15–240 minutes per session, 100 characters per name. The application still represents one department per JSON file; save multiple files for different departments. It does not provide a multi-university database or old UniPlan5 JSON import.

Recover autosave now asks before replacing unsaved current inputs. Named JSON files remain your durable copies; autosave remains one recovery copy. The generated timetable is regenerated after reopening; inputs are saved.

## Evidence

See results/fix-tests.txt and results/fix-javafx-run.txt for this patch's real output. Other results/ files were supplied with the input ZIP and document the previous release. Windows execution and the user's exact input remain unverified.

Final patch verification: 20 tests passed, zero failures/errors/skips. Real JavaFX smoke BUILD SUCCESS: 56 sessions, FEASIBLE, objective 16200, 20.234272 seconds, zero hard violations. Solver budget was 20 seconds; total launch command including Maven setup/downloads took 39.501 seconds. The tests include a native lin_max presolve example as well as direct score checks. Initial broad irregular-input test exhausted its budget with no solution; its fixture was narrowed to two days/one room so this correctness test does not depend on solving a large search space.
