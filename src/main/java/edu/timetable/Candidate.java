package edu.timetable;
/** Defensive copying prevents a mutation from changing the saved best candidate. */
record Candidate(int[] genes) {
    Candidate{genes=genes.clone();}
    public int[] genes(){return genes.clone();}
}
