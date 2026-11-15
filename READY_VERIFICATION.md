# Filled sample and GA update — 14 November 2026

## What changed

The app opens with an editable sample department. Four stages each have groups A and B. Every group has five subject entries: three Theory and two Lab. Theory has two weekly sessions, labs one, all initially 90 minutes. Total: 40 subject entries, 64 sessions, ten classrooms and four labs. Names, enrollments, instructors, sessions, durations, groups, rooms, capacities, days, daily start/end and breaks remain editable. These are fictional sample inputs, not an official Salahaddin timetable.

Hard constraints already existed and remain enforced. Soft preferences remain student gaps and daily balance with fixed internal weights. CONSTRAINTS.md compares the rules with official Timefold and UniTime sources, including differences from those examples. No universal all-university constraint list is claimed.

Advanced now offers GA as well as CP, Random and Exhaustive. GA uses tournament selection, uniform crossover, different-placement mutation at 12 percent, elitism and CP repair. Constructive feasible seeding was added after testing revealed that the old short CP seeding budgets could yield UNKNOWN before finding a candidate. If constructive seeds fail, a longer CP fallback is available. No domain or evaluator rewrite, no new dependencies.

## Real results

| Check | Result |
|---|---|
| JUnit | 23 tests, zero failures, errors or skipped |
| CP full UI demo | FEASIBLE, objective 16560, 20.374402 seconds, zero hard violations |
| GA full UI demo | FEASIBLE, objective 9990, 20.154418 seconds, zero hard violations, 472 changed genes |
| UI | All seven pages, Cancel, filters, JSON round trip, HTML and CSV export passed |

These are individual runs, not a statistically controlled comparison of method quality. Both have a 20-second budget, seed 7 and one CP worker. FEASIBLE does not prove optimality. The final screenshots show the GA run. Failed intermediate seeding attempts are preserved in results/ga-short-seed-failure.txt and ga-guided-seed-failure.txt; the final successful log is ready-ga-run.txt.

## Commands executed

```sh
/tmp/mvnkit/apache-maven-3.9.9/bin/mvn -o -f UniPlanSimple_Ready/pom.xml -Dmaven.compiler.fork=true -Dmaven.compiler.executable=/tmp/mvnkit/javac test
/tmp/mvnkit/apache-maven-3.9.9/bin/mvn -o -f UniPlanSimple_Ready/pom.xml -Pheadless -Dheadless.adapter=/tmp/mvnkit/monocle.jar -Dsimple.smoke=true javafx:run
/tmp/mvnkit/apache-maven-3.9.9/bin/mvn -o -f UniPlanSimple_Ready/pom.xml -Pheadless -Dheadless.adapter=/tmp/mvnkit/monocle.jar -Dsimple.smoke=true -Dsimple.smoke.method=GA javafx:run
```

Maven 3.9.9, Java 21 and a compatible externally supplied headless JavaFX backend were used. Those temporary paths are environment-specific. On your computer use JDK 21 and `mvn javafx:run` normally. Do not enable headless mode.

See results/ready-tests.txt, ready-cp-run.txt, ready-cp-verification.txt, ready-ga-run.txt and demo-verification.txt. Older verification documents/logs remain historical. Windows, macOS, real departmental policies, very large optimization instances and physical printing remain unverified. Existing JSON input files still open; load demo data only when you want to replace the current inputs.
