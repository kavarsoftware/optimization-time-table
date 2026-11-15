package edu.timetable;

/** The dot separates two integers; it is never a decimal fitness value. */
record SolutionScore(int soft,int hard) implements Comparable<SolutionScore> {
    SolutionScore {if(soft<0||hard<0)throw new IllegalArgumentException("Negative solution penalty");}
    public int compareTo(SolutionScore other){
        int order=Integer.compare(hard,other.hard);
        return order!=0?order:Integer.compare(soft,other.soft);
    }
    public String toString(){return soft+"."+hard;}
}
