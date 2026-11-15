package edu.timetable;

/** Exactly one hybrid stopping rule is active at a time. */
record HybridStopping(Mode mode,int generations,double seconds) {
    enum Mode {
        STAGNANT_GENERATIONS("No improvement for generations"),
        STAGNANT_SECONDS("No improvement for seconds"),
        TIME_LIMIT("Fixed running time");
        final String label;
        Mode(String label){this.label=label;}
        public String toString(){return label;}
    }
    HybridStopping {
        ProblemData.require(mode!=null&&generations>0&&seconds>0&&Double.isFinite(seconds),"Invalid hybrid stop settings");
    }
    static HybridStopping defaults(){return new HybridStopping(Mode.STAGNANT_GENERATIONS,1000,15);}
}
