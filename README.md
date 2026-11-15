# UniPlan Simple

A simple Java desktop timetable generator for **one department with four stages**. Enter your inputs, review them and generate one repeating week. The UniPlan5 solver is retained; the complicated frontend is replaced.

## Run in IntelliJ IDEA — five steps

1. Extract **UniPlanSimple.zip** into a new folder. Do not overwrite UniPlan5.
2. In IntelliJ choose **File → Open**, select **UniPlanSimple/pom.xml**, and open it as a Maven project.
3. Set **Project SDK = JDK 21** and **Maven runner JRE = JDK 21**. Use a full JDK. If Maven is missing, right-click `pom.xml` → **Add as Maven Project**, then Reload Maven.
4. In IntelliJ's Terminal, from the UniPlanSimple folder, run `mvn test`. The first build needs internet to fetch dependencies and the native solver for your platform.
5. Run `mvn javafx:run`. Click **Restore default data**, then **Generate** in the left stepper and **Generate timetable**. Hybrid is the default, stopping after 1,000 consecutive generations without improvement.

The application launcher is **src/main/java/edu/timetable/Launcher.java** and the UI is **SimpleApp.java**. Prefer Maven launch so native resources are prepared. You do not run `ProblemData.java` or `ConstraintSearch.java` directly. The thin JAR alone is not a standalone installer.

## Filled editable sample and mutation

The app opens with sample data: Salahaddin University / College of Engineering / Software & Informatics Engineering, stages 1–4, groups A/B in each, **five subjects per group (three Theory, two Lab)**, ten classrooms and four labs. All values are examples and remain editable. Instructor names and enrollment are fictional, not official university records.

Theory subjects have two 90-minute sessions weekly; labs have one 90-minute session. This gives 40 subject entries and 64 sessions. You can change the session counts, lengths, names, student counts, instructors, groups, rooms, daily times and breaks using the existing screens. Startup and New both load the complete default dataset; Restore default data reloads it at any time.

Generate → Advanced → Method defaults to **Hybrid**. CP finds a starting timetable; genetic search uses tournament selection (3), uniform crossover, 12% per-gene mutation, elitism and CP repair. CP periodically optimizes the best timetable. The only available methods are Hybrid, Random and Brute-force. Mutation changes placements, never saved subject inputs. The UI reports actual mutations, completed generations and the selected stopping rule. Every run receives a fresh random seed. Hybrid also randomly retains equally good placements, so repeated runs can produce different timetables without treating ties as improvements. Population size is 16.

Choose one **Hybrid stop condition** under Advanced:

- **No improvement for generations** (default): stop after **1,000 consecutive generations** without a strictly better best score. The number is editable. Every improvement resets the counter. There is no overall time limit or hidden 100-generation cap.
- **No improvement for seconds**: stop after **15 seconds** without a strictly better best score. The duration is editable. The timer starts when search begins and resets whenever the best score improves, including improvements found by CP. There is no overall runtime limit.
- **Fixed running time**: run for the chosen number of seconds and return the best result found. The default runtime is 60 seconds. Improvement, stagnation and an optimal score do not stop this mode early.

Ties do not reset either inactivity counter. Cancel remains available in every mode. These settings are saved with the project and do not invalidate an existing timetable.

## The seven screens

1. **University:** University, College, Department, in that order.
2. **Stages & groups:** stages 1–4, with named groups such as A/B. Add, edit or remove groups; each stage needs one. You must remove a group's subjects before deleting that group.
3. **Subjects:** select stage/group, then enter subject name, Theory/Lab, its own student count, weekly sessions, exact length and optional instructor. Use the same instructor spelling wherever that person teaches. Blank instructors are explicitly unassigned.
4. **Days & times:** independently select Sunday–Thursday and set each day's start/end/class length/breaks. Copy a day's settings to selected other days. Green preview blocks teach; grey blocks are breaks or short unused remainders.
5. **Rooms:** Name, Classroom/Lab, Capacity. Theory uses Classroom; Lab uses Lab.
6. **Review:** red errors block generation; amber warnings remain visible. Every diagnostic links to an editing page. Oversized subjects suggest a larger room or splitting into real groups, without inventing enrollment.
7. **Generate:** default Hybrid and 1,000 generations without improvement; progress and Cancel. **Advanced** contains Method: Hybrid, Random, Brute-force, plus Finding best solution and Hybrid stop condition selectors. Timetable filters include stage, group, room and instructor. HTML exports the full week; CSV exports filtered visible classes.

The left stepper and Back/Next keep everything editable. No calendar dates, weights, raw tables, locks, custom rules, workloads, GA parameter boxes, experiments or ICS export are visible. Manual drag/drop is removed: this version presents solver-validated results only.

## Time semantics — please read

A day's **class length** divides its teaching spans into full periods, restarting after each break. For 09:00–15:00, 90-minute classes, breaks 10:30–11:00 and 12:30–13:00, the teaching periods are 09:00–10:30, 11:00–12:30 and 13:00–14:30. The final 30 minutes are unavailable.

A subject has its **own fixed session length**. It can be shorter than a period, but cannot cross the boundary or a break. Starts use five-minute steps relative to the period start. A 120-minute subject needs a day with a period at least 120 minutes long. Review explains this instead of silently shortening the subject.

Because subjects are entered before choosing a day for each session, new subject length defaults to the **first selected day's class length**, initially Sunday/90 minutes. Changing days later does not change existing subject durations. This assumption is visible on the Subjects page.

Student groups are disjoint attendance groups in this simplified UI. Cross-group individual student IDs and joint lecture creation are not exposed. Add a subject separately for each group. An unassigned instructor gets an internal unique placeholder; it does not cause every blank instructor to clash, but you must assign the actual person to check their real conflicts.

## What “best” means

Hard constraints: no simultaneous room, instructor or student-group use; exact session number/duration; room type/capacity; classes inside teaching periods, never breaks. All solutions are independently evaluated.

Choose **Finding best solution** under Advanced:

- **Least gap between subjects:** sum `max(0, next class start − previous class finish − scheduled rest minutes inside that gap)` in minutes for consecutive classes, per group/day across the week.
- **Earlier finishes of the day:** the same extra gaps, plus `max(0, first class start − 09:00)` for every group/day with classes.

Classes cannot start before the university opens at 09:00. Rest is taken directly from the scheduled breaks in **Days & times**. Only breaks inside that particular gap are deducted; multiple breaks are added together. A gap with no scheduled break receives no allowance. Breaks outside the gap, breaks on other days, and unavailable short remainders do not reduce the penalty. Penalties never become negative. For example, a 75-minute gap containing a configured 45-minute break costs 30 soft minutes; a 20-minute gap without a scheduled break costs 20. Days without classes add zero. Daily balance is no longer part of the Simple app's score. CP and the independent evaluator use the same formula. Legacy developer datasets without a SearchObjective setting retain their weighted objective.

Scores display as **soft.hard**, a pair of integers separated by a dot, not a decimal. Fewer hard violations always wins; ties use fewer soft minutes. Thus `60.2` beats `360.2`, and `1000.0` beats `0.1`. Sixty extra gap minutes plus five 10 AM starts gives `360.0` with no hard violations, or `360.2` with two. Genetic candidates may have hard violations during repair; published and saved timetables must have zero.

**OPTIMAL** means proven optimal for this model. **FEASIBLE** means valid, best found within the search, without proof. **INFEASIBLE** means a domain check or complete solver proof rules it out. **UNKNOWN** means no feasible candidate was found in time, not that the problem is impossible. Random and Brute-force are lecture baselines; brute-force enumeration is impractical for the demo.

## Save, recover and exports

Save/Open uses UTF-8 JSON with `format: UniPlanSimple` and `schemaVersion: 1`. Names containing `|`, commas or Unicode are supported, except group names cannot contain commas. Autosave writes changed inputs to `~/.uniplan-simple/autosave.json`; use **Recover autosave**. This is one recovery copy, not a backup history. Use Save JSON for your own named copies. Save/Open retains the selected goal and restores independently verified timetable results. Changing the goal invalidates the old result and requires regeneration.

The simplified document has a different schema from UniPlan5. Its old reader remains in developer code, but the Simple UI rejects UniPlan5 files instead of silently importing hidden rules/dates/weights. Keep the original files and use UniPlan5 to read them. The demo is `examples/simple-demo.json`.

Printable HTML uses the same green palette as the app. Open it in a browser and print or choose Save as PDF. CSV handles quotes and Unicode. Neither export sends your data anywhere.

## Tests and developer tools

```shell
mvn test
mvn -Dsimple.smoke=true javafx:run
mvn exec:java -Dexec.mainClass=edu.timetable.ExperimentRunner "-Dexec.args=30 2"
```

The smoke command on a graphical desktop traverses the seven screens, runs the 64-session demo through the UI, checks feasibility/filters and writes screenshots/exports to results/. It exits automatically. ExperimentRunner is inherited developer tooling, not a product screen; its synthetic datasets differ from Simple's department demo. It overwrites experiment files if run.

For this display-less Linux delivery environment, the exact launch command was:

```shell
mvn -Pheadless -Dheadless.adapter=/tmp/mvnkit/monocle.jar -Dsimple.smoke=true javafx:run
```

That optional profile requires an **externally supplied** compatible OpenJFX headless backend; the file is not shipped or needed on Windows. The real logs, measured runtime and screenshot files are in `results/`. See **READY_VERIFICATION.md** for commands, fixes, remaining limits and what was actually tested. See **EXPLAIN_TO_DR_GODAR.md** for a three-minute presentation and **DATA_DICTIONARY.md** for every field.

## Credits

Public Timefold school-timetabling and UniTime examples informed the weekly model and resource-conflict presentation. AtlantaFX and ControlsFX were compared but not added. OR-Tools, Gson, JavaFX, SLF4J and JUnit are retained with pinned versions. No public application code or theme was copied; no Spring, Quarkus or Timefold dependency was added. **CREDITS.md** contains the four-candidate comparison, observed update dates, license details and official links.

## Limits

Verified on Linux with real JavaFX rendering and native CP-SAT, using a headless backend. Windows/macOS runtime behavior, browser printing and very large universities are not verified. Native build profiles for Windows x64, Linux x64/ARM64 and macOS Intel/ARM64 select the appropriate OR-Tools resource package; only Linux x64 was executed. There is no Windows installer in this source ZIP.
