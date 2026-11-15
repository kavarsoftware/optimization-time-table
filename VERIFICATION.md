> Historical release notes. See READY_VERIFICATION.md and CONSTRAINTS.md for the current filled-demo / GA update.

# Verification — 14 November 2026

## Final results (executed, not estimated)

- `mvn test`: **16 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS. Eight original tests retained and eight Simple tests added.
- `mvn javafx:run` with the optional headless profile: **BUILD SUCCESS**. Real JavaFX controls/rendering and native OR-Tools ran; no simulated timetable or screenshot mockup.
- Demo: **56 sessions, four stages, eight groups, ten classrooms, four labs**. CP result **FEASIBLE**, objective **10350**, **20.072596 seconds** including model construction, **zero hard violations**. The configured solver budget was 20 seconds. Feasible is not a proof of optimality.
- UI smoke passed all seven screens, independent daily inputs, cancellation, stage/group/room/instructor filters, JSON round trip, HTML and CSV export. Screenshots were rendered and visually inspected.
- Final test and launch logs contain no `[WARNING]` or `[ERROR]` lines. Exact output is in `results/test-output.txt`, `results/javafx-run.txt` and `results/demo-verification.txt`.

## Exact commands in this environment

The environment supplies Java 21.0.13 but no javac executable. A compiler wrapper calls the installed Java compiler module. Maven 3.9.9 and dependencies were downloaded before offline verification. These machine-specific paths are not needed on your full JDK installation.

```sh
/tmp/mvnkit/apache-maven-3.9.9/bin/mvn -o -f UniPlanSimple/pom.xml -Dmaven.compiler.fork=true -Dmaven.compiler.executable=/tmp/mvnkit/javac test
/tmp/mvnkit/apache-maven-3.9.9/bin/mvn -o -f UniPlanSimple/pom.xml -Pheadless -Dheadless.adapter=/tmp/mvnkit/monocle.jar -Dsimple.smoke=true javafx:run
```

Normal IntelliJ/Windows commands are `mvn test` then `mvn javafx:run` with JDK 21. Do not enable headless mode on your desktop.

## Errors and warnings encountered, and their resolution

The original UniPlan5 baseline passed eight tests. Its ordinary graphical launch did not succeed in this environment. Full baseline/intermediate logs remain in `results/baseline/`. `results/warning-error-index.txt` lists every captured Maven warning/error line, including repeated stack-summary messages.

| Observation | Resolution / status |
|---|---|
| Offline launch: no plugin found for prefix javafx; uncached descriptors for clean, install, deploy, site, antrun, assembly, dependency and release plugins | Downloaded required Maven artifacts online, then verified offline successfully. |
| Six effective-model warnings for JavaFX controls 21.0.2, from duplicate platform/architecture profile IDs | Pinned JavaFX 21.0.9. Final logs are warning-free. |
| Three native JAR path exceptions: linux-x86-64, darwin-x86-64, win32-x86-64 module names rejected by the JavaFX Maven launcher | Platform-native artifact is unpacked into application resources at process-resources. Unused native dependencies excluded. |
| Unable to open DISPLAY during ordinary graphical launch | No display server is available here. Xorg could not bind its sockets. Ran actual JavaFX using an external headless backend instead. Ordinary Windows desktop launch remains unverified. |
| Intermediate native initialization failure: missing native resource after launcher ignored JARs | Corrected native resource packaging above. |
| Intermediate loader failure: Path component should be '/' and subsequent NoClassDefFoundError; seven test errors | Added small NativeSolver adapter for unpacked file resources, retaining original jar loading otherwise. All 16 tests now pass. |

Original ProblemData, Evaluator, DefaultEvaluator and SearchEngine remain unchanged. ConstraintSearch retains its interval NoOverlap implementation with a native loading adapter and explicit fixed blocked periods for Simple mode. See `results/core-diff.txt`.

## Evidence and scope

`results/01-university.png`, `02-daily-times.png`, `03-timetable-all-stages.png`, and `04-stage-four.png` show the actual application. The grid scrolls vertically through the week and shows all eight group columns together. HTML exports the full timetable; sample CSV contains Stage 4 / A.

Not verified: Windows or macOS execution, a native desktop window manager, physical/browser printing, installer packaging, very large datasets, or production deployment. The tests do not constitute exhaustive UX validation of every possible input sequence. The supplied demo satisfies the under-30-second target on this machine, not a performance guarantee on every computer. Fixed seeds aid repeatability; time limits and machine speed can still affect outcomes.

The Simple UI uses its own versioned JSON schema and does not import old UniPlan5 UI JSON. This limitation is explicit in README. The original engine fixtures and readers remain for developer tools.
