# Three-minute explanation for Dr. Godar

**0:00–0:35 — The problem and the data**

“My project is UniPlan Simple. It builds a repeating weekly timetable for a university department with four stages. The administrator enters the university, college and department, groups, subjects, daily teaching times and breaks, and rooms. These are the **data**. Every subject has its own number of students, sessions per week and exact session length. An instructor is optional, but we need the actual name to check that person's conflicts.”

**0:35–1:10 — Decisions and constraints**

“For each session, the **decision** is its day, start time and room. The program first builds the eligible placements. One **candidate** is an integer array: each element selects one placement for one session. The **search space** is the product of the numbers of eligible placements.

A candidate is **feasible** only if every hard **constraint** passes. A room, instructor or student group cannot attend two sessions at the same time. Room type and capacity must match. Each class must fit inside a teaching period and cannot overlap a break. The required number and length of weekly sessions must stay exact.”

**1:10–1:45 — The objective**

“Among feasible candidates, I minimize an **objective**: student idle-gap minutes plus a daily balance penalty. This encourages fewer gaps and teaching spread across days. These are preferences, not permission to break hard rules. Lower objective is better under this particular model.

The same independent **Evaluator** checks every solver result. It reports feasibility, the objective and any violations. The CP solver's score is checked against the evaluator, so disagreement is treated as a program bug.”

**1:45–2:20 — Methods, baseline and seed**

“The default method is CP-SAT. It uses interval variables and **NoOverlap** for shared resources. Room intervals are optional because a session chooses one room. Breaks are fixed blocked intervals in Simple mode.

GA also uses tournament selection, uniform crossover and mutation. Each gene has a 12 percent chance to switch to a different eligible placement; CP repairs conflicts and the evaluator rejects invalid offspring. Elitism keeps the best feasible timetable. Under Advanced I keep Random and Exhaustive as **baseline** methods from the lectures. Random samples candidates; Exhaustive tries every combination and is useful only on tiny instances. I use a fixed **seed**, 7, and an entered time budget, normally 20 seconds. A seed makes random experiments easier to reproduce, but time-limited runs can still vary between computers.”

**2:20–3:00 — Demonstration and honest conclusion**

“The interface has seven short screens. I can edit each day independently, copy daily times and see teaching and break periods before generating. Review explains problems and links to the right screen. Generation runs in a background task, with progress and Cancel. The result shows all stages together and can be filtered or exported.

The supplied demo has four stages, A/B groups, five subjects per group, including two labs, ten classrooms and four labs. In the executed Linux test it produced a feasible 64-session timetable in under 30 seconds with zero hard violations. I report its measured runtime in the verification file. **FEASIBLE** means a valid solution was found; it does not mean I proved the best possible timetable. Windows and production-wide university scale remain unverified.”

## If the instructor asks

- **Why not calendar dates?** This version solves a repeating week; dates are unnecessary for its decision model.
- **Why CP instead of only random search?** Hard resource constraints make random feasible candidates rare. CP uses constraints to guide the search.
- **Is zero hard violations the same as a zero objective?** No. A feasible timetable can still have idle gaps or uneven days.
- **What does UNKNOWN mean?** The method did not find a feasible solution in its budget; it did not prove impossibility.
- **What did you simplify?** The interface and input adapter. ProblemData, Evaluator and SearchEngine remain, and ConstraintSearch keeps interval NoOverlap with only a native-loader fix and explicit Simple-mode blocked periods.
- **Did you copy a public scheduler?** No. I researched modeling/UI practices and credited them; no public application source was pasted and no heavyweight framework was added.
- **Verification or validation?** Tests verify the implementation against the model. Real department staff still need to validate that the entered data and rules represent their actual university.
