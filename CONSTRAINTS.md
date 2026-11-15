# Hard constraints, soft constraints and mutation

Research checked 14 November 2026. There is no single universal list: local policy decides which preferences matter. This project uses a conventional resource-conflict model and keeps the user's capacity and breaks mandatory.

| Rule in this project | Level | Enforcement |
|---|---|---|
| One class per room at a time | Hard | CP optional room intervals / NoOverlap; independent evaluator |
| An instructor cannot teach overlapping classes | Hard | Instructor NoOverlap; evaluator |
| A student group cannot attend overlapping classes | Hard | Group NoOverlap; evaluator |
| Classroom for Theory, Lab for Lab, enough seats | Hard | Eligible placement domain; health diagnostics |
| Exact number and duration of weekly sessions | Hard | One event per session, exactly one placement per event |
| Only selected days and teaching periods, never breaks | Hard | Placement domain and fixed blocked periods in Simple mode |
| Minimize extra gaps for students | Soft | Sum max(0, elapsed gap − scheduled rest minutes within that gap) for consecutive classes per group/day |
| Earlier finishes (selected goal) | Soft | Extra gaps plus max(0, first class start − 09:00), per group/day with classes |

The Simple app selects either extra gaps alone or extra gaps plus first-start delay after 09:00. There is no daily balance penalty. Scores display as soft.hard, two integers separated by a dot: compare hard first, then soft. Scheduled breaks within each gap are deducted at their configured durations; multiple breaks are summed and a gap with no break gets no allowance; starts before 09:00 are ineligible; empty days add zero. Only configured required-break intervals count as rest. Breaks outside a gap and unavailable short remainders are not deducted. The earlier-finish first-start delay still measures the full time after 09:00. Legacy developer datasets without SearchObjective keep their weighted objective. A feasible timetable must satisfy every hard rule. A lower soft score can never compensate for a hard violation. Optional blank instructors cannot protect against conflicts for a real unnamed person: enter their same name everywhere to enforce this.

## What the public projects teach us

- Timefold school quickstart lists room/teacher/group conflicts as hard, teacher room stability/time efficiency and student subject variety as soft: https://github.com/TimefoldAI/timefold-quickstarts/blob/stable/use-cases/school-timetabling/README.md
- Timefold university course model illustrates that conventions differ: its ITC 2007 model treats room capacity as soft. Here capacity stays **hard**, as requested: https://docs.timefold.ai/timefold-solver/0.8.x/use-cases-and-examples/course-timetabling/course-timetabling
- UniTime explains resource constraints and required/prohibited versus preferred distributions: https://help-unitime.unitime.org/uct_description.php ; https://help.unitime.org/manuals/courses-entry

We apply the shared modeling practices rather than claiming to implement every rule in those larger products. Travel time, subject variety, instructor room stability, dates and personal preferences are not additional hidden rules in this version. No public code or framework was added.

## Genetic algorithm and mutation

Hybrid is the default under Advanced. CP creates a seed; genetic generations use selection, crossover, mutation and CP repair, with periodic CP optimization. Select one stopping rule: 1,000 consecutive generations without improvement (default), 15 seconds without improvement, or a fixed total runtime. All thresholds are editable. Only a strictly better best score resets the generation counter and inactivity timer. The hybrid has no hidden 100-generation cap or overall time cap in the two inactivity modes. Fixed runtime continues until its deadline even at an optimal score. Only Hybrid, Random and Brute-force are offered. Hybrid uses a fresh random seed for every run and can randomly retain equally good timetables. Constructive seeding shuffles event order, places the most constrained domains first, and tries random eligible placements without resource conflicts. The full evaluator checks each completed seed. If no feasible seed is constructed, CP gets a longer fallback attempt. A candidate is an array of placement indices, one per session. Selection picks the best of three randomly sampled parents. Uniform crossover chooses each gene from either parent. With probability 0.12 per gene, mutation selects a **different** placement from that session's eligible domain. One-choice genes cannot mutate. Random seeds are generated afresh for every run; the stopping settings are editable.

A domain-valid mutation can still conflict with another event. The evaluator counts each violated resource or rule separately, and CP attempts repair within its allocated budget. Genetic selection compares fewer hard violations first, then fewer soft minutes; unrepaired candidates can remain in the population but can never replace a feasible saved best. Elitism preserves the best feasible timetable. Mutation therefore explores alternatives without publishing invalid timetables. GA is a heuristic: neither mutations nor time limits guarantee a global optimum. Hybrid continues until its selected stopping rule is met, even at zero. Equal-quality replacement never resets the stagnation counter or timer. The UI displays the actual number of changed genes, not attempted probability draws.
