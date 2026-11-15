package edu.timetable;
import java.util.*;
interface Evaluator {
    record Gap(String audience,String day,int before,int after,int elapsed,int rest){int minutes(){return elapsed-rest;}}
    record Score(List<String> violations,List<Gap> gaps,int minutes){
        boolean valid(){return violations.isEmpty();}
        SolutionScore ranking(){return new SolutionScore(minutes,violations.size());}
    }
    Evaluation evaluate(ProblemData data,Candidate candidate);
    static Score evaluate(ProblemData data,int[] genes){return DefaultEvaluator.calculate(data,genes);}
}
