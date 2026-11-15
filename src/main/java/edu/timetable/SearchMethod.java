package edu.timetable;
/** Common search entry contract used by the UI and experiment runner. */
interface SearchMethod {
    void run(String method,SearchEngine.ConstraintSolver constraints);
}
