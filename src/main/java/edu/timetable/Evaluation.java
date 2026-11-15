package edu.timetable;
import java.util.List;
record Evaluation(boolean feasible,int objective,List<String> violations){
    Evaluation{violations=List.copyOf(violations);}
}
