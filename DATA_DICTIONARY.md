# UniPlan Simple — data dictionary

All durations are minutes. Time is local 24-hour time; there are no semester dates or time zones. Fields have visible labels, tooltips and inline errors. Numeric spinners are editable: type a whole number and press Enter or leave the field to commit; invalid text reverts to the last valid value and shows the range error. Arrow step is 5 for time limit, students and capacity, and 1 for other numeric fields. Hour/minute time pickers remain noneditable (arrows or Up/Down keys). Text errors are validated beside the field. Cross-field time errors appear inside the day's card.

| Screen | Label | Data type | Example | Rule |
|---|---|---|---|---|
| 1 University | University (text) | String | Salahaddin University | Required, 1–100 characters, one line. |
| 1 University | College (text) | String | College of Engineering | Required, 1–100 characters, one line. |
| 1 University | Department (text) | String | Software & Informatics Engineering | Required, 1–100 characters, one line. |
| 2 Stages & groups | Stage (whole number) | Integer spinner | 4 | 1–4. Four stages are fixed; each needs at least one group. |
| 2 Stages & groups | Group (text) | String | A | Required, 1–100 characters, no commas, unique within stage ignoring case. Renaming a group updates its subjects. |
| 3 Subjects | Selected stage (int) | Choice of integers | 4 | One of 1, 2, 3, 4. Filters the form/list. |
| 3 Subjects | Selected group (text) | Choice of group names | A | Existing group in selected stage. |
| 3 Subjects | Subject name (text) | String | Machine Learning | Required, 1–100 characters. Same name and type cannot be added twice to the same group through the form; edit session count instead. |
| 3 Subjects | Type | Enumeration | Theory | Theory or Lab. Maps to engine room type Lecture or Lab. |
| 3 Subjects | Number of students (whole number) | Integer spinner | 35 | 1–1,000,000. Each subject has its own count; must fit an eligible room. |
| 3 Subjects | Sessions per week (whole number) | Integer spinner | 2 | 1–20. Exactly this many events are generated. |
| 3 Subjects | Session length (minutes) | Integer spinner | 90 | 15–240, exact duration of every weekly session. New subject default is the class length of the first selected teaching day. Existing values do not change silently. |
| 3 Subjects | Instructor (optional text) | String | Dr. Narin | 0–100 characters. Reuse exactly the same spelling for a shared instructor. Blank means genuinely unassigned; no actual instructor availability/conflict can be inferred. |
| 4 Days & times | Sunday … Thursday | Boolean checkboxes | Sunday selected | All five selected by default. At least one selected; disabled days preserve settings but are not scheduled. |
| 4 Days & times | Start time (24-hour) | Hour/minute spinners | 09:00 | Hour 0–23, minute 0–59; earlier than end. |
| 4 Days & times | End time (24-hour) | Hour/minute spinners | 15:00 | Same ranges; later than start. No overnight teaching. |
| 4 Days & times | Class length (minutes) | Integer spinner | 90 | 15–240. Divides each teaching span into complete class periods. Any short remainder is unavailable. |
| 4 Days & times | Break start (24-hour) | Hour/minute spinners | 10:30 | Inside the selected day, earlier than break end. |
| 4 Days & times | Break end (24-hour) | Hour/minute spinners | 11:00 | Inside the day, later than start. Breaks cannot overlap; at least one full class period must remain. |
| 4 Days & times | Copy to other days | Boolean selections | Monday and Tuesday | Replaces selected days' start/end/length/breaks; does not enable/disable days. |
| 5 Rooms | Room name (text) | String | Classroom 1 | Required, 1–100 characters, unique ignoring case. |
| 5 Rooms | Type | Enumeration | Classroom | Classroom or Lab. |
| 5 Rooms | Capacity (students) | Integer spinner | 40 | 1–1,000,000. Hard capacity constraint. |
| 6 Review | Go fix it | Navigation action | Go fix it → Rooms | Links each diagnostic to the relevant editable page. Red blocks generation; amber warns. |
| 7 Generate | Time limit (seconds; fixed-runtime hybrid or other methods) | Integer spinner | 60 | Default 60; range 1–86,400. Maximum solver search budget; native shutdown may add a small overhead. |
| 7 Generate / Advanced | Method | Enumeration | Hybrid | Methods: Hybrid (default), Random, Brute-force. Each run receives a fresh random seed; solver parallelism is automatic. |
| Timetable | Stage / Group / Room / Instructor | Choice filters | Stage 4 / 4 A | All or an existing value. Multiple filters intersect; CSV exports visible events, HTML exports the whole week. |

Advanced has **Hybrid stop condition**, saved as `hybridStopping`: `STAGNANT_GENERATIONS` (default, 1,000 consecutive generations), `STAGNANT_SECONDS` (default threshold 15 seconds), or `TIME_LIMIT` (uses `runtimeSeconds`, default 60). Both thresholds and runtime are editable. Strict improvements reset stagnation; ties do not. Only the selected rule stops the hybrid; fixed runtime continues even at an optimal score. The stagnation modes ignore total runtime and the legacy generation cap. Stopping preferences are excluded from the input hash so editing them does not invalidate a timetable.

Advanced also has **Finding best solution**, saved as `optimizationGoal`: `FEWER_GAPS` (default) or `EARLIER_FINISH`. The adapter writes this into the engine's `SearchObjective` table. Both goals sum `max(0, elapsed gap − scheduled rest minutes within that gap)` per consecutive pair of classes per group/day. Rest comes from the day's scheduled break intervals: add all break minutes inside that specific gap, with no fixed fallback. Breaks elsewhere and unavailable remainders do not reduce the score. Earlier finishes also sums the first class start's delay after 09:00 per group/day with classes. Classes cannot start before 09:00. The displayed score is `soft.hard`; compare hard violations first, then soft minutes. The dot separates integers, never a decimal value.

## Deliberately hidden/fixed values

The adapter sets zero per-subject rest (breaks come from daily inputs), 5-minute candidate-start steps inside each full class period, and no calendar dates. The selected SearchObjective overrides the legacy objective weights; daily balance does not contribute to either Simple app goal. Legacy developer datasets without SearchObjective retain their weighted objective.

A subject may be shorter than a day class period, but cannot cross its boundary. A 120-minute subject cannot use a day configured with 90-minute periods: Review explains which setting to change. Starts are selected every five minutes from the period's start, so a 09:10 period starts on its own grid, not on an assumed 09:00 grid.

Subjects have generated stable IDs in JSON, separate from their names. Group engine IDs combine stage and group name, e.g. `4 A`. Different groups are treated as disjoint student groups in Simple; cross-group individual student enrollment is not an input in this version. Use a separate subject row for each group. Stage A and B are not automatically combined into shared lectures.

| Screen / location | New field | Type | Example | Rule |
|---|---|---|---|---|
| Advanced | Feasible generator | Boolean checkbox | true | Random only; constructive when on, uniform when off; not persisted. |
| Advanced | Workers | Integer spinner | 8 | Editable 1–16, default min(8, available processors); not persisted. |
| JSON | result | Optional object | saved timetable | Omitted in old schema-1 files; inputs must hash-match and placements must pass evaluator. |
| JSON result | method / seed / score | String / integer / integer | CP / 7 / 8640 | Records method, seed and independently evaluated score. |
| JSON result | placements | List of objects | subjectId, occurrence, day, start, room | One per session; occurrence is one-based per subject; start is minutes after midnight. |
| JSON result | inputHash | SHA-256 hex string | 64 hexadecimal characters | Hashes serialized inputs excluding result; mismatch ignores saved timetable. |

| Screen / location | New field | Type | Example | Rule |
|---|---|---|---|---|
| JSON | pins | Optional list of placement objects | subjectId, occurrence=1, Sunday, start=540, room | One-based occurrence per subject; maps to zero-based existing Locks event index. |
| Instructors / JSON | daysOff | Optional instructor → list of weekday strings | Dr. A → [Monday] | Sunday–Thursday; affects all matching instructor names; all selected days off warns and prevents feasible placement. |
| Subjects / JSON | jointWith | Optional source-subject-ID → target-subject-ID map | S1A0 → S1B0 | Same stage, different groups, matching course/type/sessions/length/instructor; enrollments sum; each subject participates once. |
| Move class | Alternative slot | Eligible placement selection | Sunday 11:00 / Room 2 | Only independently feasible alternatives; pinned classes cannot move; saved through result placements. |
| Generate | Final option | Option 1/2/3 selection | Option 2, seed 8 | Seeds 7/8/9 share the time budget; only explicitly chosen timetable is exported or stored in result. |
| Print / Save as PDF | Stage or instructor | Enumeration | All / Stage 4 | Creates landscape HTML and opens browser; browser supplies PDF saving. |
