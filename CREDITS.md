# Credits and open-source research

Research performed on **14 November 2026**, before frontend code changes. Public GitHub pages, project documentation and release pages were searched. This table distinguishes a visible release/activity date from an assurance about the newest commit. No public application's source code was pasted into this project.

| Candidate and official link | License | Last update observed | What we learned | Decision |
|---|---|---|---|---|
| [Timefold school-timetabling quickstart](https://github.com/TimefoldAI/timefold-quickstarts) | Apache-2.0, repository license | GitHub organization listed activity **3 Nov 2026** | A weekly Lesson/Timeslot/Room model and separate room, teacher and student conflicts are easier to explain than calendar-driven scheduling. | Use the modeling idea only. Do not add Timefold, Spring or Quarkus; retain the lecture baselines and existing CP engine. |
| [UniTime](https://github.com/UniTime/unitime) | Apache-2.0 for current 4.x; [license](https://github.com/UniTime/unitime/blob/master/LICENSE) | Release **4.9.164**, **1 Nov 2026**, on [releases](https://github.com/UniTime/unitime/releases) | University scheduling needs clear diagnostics, resource views and an inspectable timetable grid. | Reference only. Its full institutional workflow is much broader than this department app. No source/library reuse. |
| [AtlantaFX](https://github.com/mkpaz/atlantafx) | MIT | **3.0.0**, **24 Sep 2026**, in [changelog](https://github.com/mkpaz/atlantafx/blob/master/CHANGELOG.md) | Consistent spacing, color tokens and control styling make a desktop UI calmer. | Do not add it: the latest release raises Java requirements beyond this Java 21 project, and native controls plus one green stylesheet suffice. No CSS copied. |
| [ControlsFX](https://github.com/controlsfx/controlsfx) | BSD-3-Clause | GitHub organization listed activity **15 Oct 2026**; releases page listed 11.2.5 | Validation should be visible beside fields and follow predictable controls. | No extra dependency: small labeled field helpers, spinners and ComboBox controls cover this app. No source copied. |

Sources consulted: [Timefold Java hello-world guide](https://docs.timefold.ai/timefold-solver/1.x/quickstart/hello-world/hello-world-quickstart), [Timefold organization activity](https://github.com/TimefoldAI), [ControlsFX organization activity](https://github.com/controlsfx), [ControlsFX releases](https://github.com/controlsfx/controlsfx/releases), [AtlantaFX releases](https://github.com/mkpaz/atlantafx/releases). Some individual GitHub license-file fetches failed; repository license metadata and the successfully retrieved official license/documentation pages were used. Dates above are the visible evidence retrieved, not invented timestamps.

## Solver and build references

- [Google OR-Tools job-shop guide](https://developers.google.com/optimization/scheduling/job_shop): intervals and NoOverlap per exclusive resource. UniPlan5 already used this modeling approach; it is retained. Simple mode additionally places fixed blocked intervals into the resource constraints. No example code was copied.
- [OpenJFX Maven guide](https://openjfx.io/openjfx-docs/maven) and [JavaFX Maven plugin](https://github.com/openjfx/javafx-maven-plugin): supported Maven launch configuration. Inspecting the published 0.0.8 plugin source explained why non-modular projects drop native JARs with invalid automatic module names. The fix uses the Apache Maven dependency plugin to unpack only the selected platform's OR-Tools native resources into the build output; no plugin code was copied.
- Supplied Dr. Godar lectures, Weeks 0–2: data, decisions, objective, constraints, candidate representation, exhaustive/random baselines, seeds and budgets. The exact CP implementation is an existing project extension, not claimed to be copied from those lectures.

## Retained dependencies and exact versions

| Dependency/tool | Version | License / official project | Use |
|---|---|---|---|
| OpenJFX controls, graphics and base | **21.0.9** | [OpenJFX](https://github.com/openjdk/jfx), GPL-2.0 with Classpath Exception | Existing explicitly requested JavaFX framework; updated from 21.0.2 to remove duplicate-profile warnings. No JavaFX implementation source is copied. |
| OR-Tools Java and platform native resources | **9.11.4210** | [OR-Tools](https://github.com/google/or-tools), Apache-2.0 | Existing CP-SAT engine. Native platform version is pinned in the build. |
| Gson | **2.11.0** | [Gson](https://github.com/google/gson), Apache-2.0 | JSON persistence. |
| SLF4J simple/API | **2.0.16** | [SLF4J license](https://www.slf4j.org/license.html), MIT | Existing engine logging. |
| JUnit Jupiter | **5.11.3** | [JUnit](https://github.com/junit-team/junit5), EPL-2.0 | Existing explicitly requested test framework; test scope only. |
| JavaFX Maven plugin | **0.0.8** | [Plugin](https://github.com/openjfx/javafx-maven-plugin), Apache-2.0 | Desktop launch. |
| Maven compiler / Surefire / JAR / dependency plugins | **3.13.0 / 3.5.2 / 3.4.1 / 3.7.0** | [Apache Maven plugins](https://maven.apache.org/plugins/), Apache-2.0 | Compile, test, package, unpack native resources. |
| Exec Maven plugin | **3.5.0** | [MojoHaus](https://www.mojohaus.org/exec-maven-plugin/), Apache-2.0 | Developer-only experiments. |

Transitive dependencies retained by these libraries include protobuf-java 4.26.1 (BSD-3-Clause), JNA/JNA-platform 5.14.0 (dual Apache-2.0/LGPL; use Apache-2.0), error_prone_annotations 2.27.0 (Apache-2.0), JUnit Platform 1.11.3 (EPL-2.0), OpenTest4J 1.3.0 and API Guardian 1.1.2 (Apache-2.0). Maven resolves their original published metadata/licenses.

The user's required JavaFX and JUnit dependencies are retained with their actual licenses; they are not falsely relabeled as MIT/Apache. No new GPL application/library code was reused. No new production UI library was added. Headless delivery testing uses an externally supplied OpenJFX Monocle backend already available in the test environment, patched into javafx.graphics; it is **not included in the ZIP or added as an application dependency**. A normal desktop run does not need it.

Patch investigation: Google OR-Tools issue #5453 (https://github.com/google/or-tools/issues/5453) informed the intermediate-objective handling and a small independently written Java regression model. No library version changes.

This update compares hard/soft conventions in CONSTRAINTS.md, using the official Timefold quickstart and UniTime modeling documentation. No public implementation copied.
