package edu.timetable;
import java.util.*;
/** Allocates exactly the requested weekly minutes before placement search. */
final class WeeklyTime {
    static List<Integer> lengths(int total,int count,String allowed){
        var values=Arrays.stream(allowed.split(",")).map(String::trim).map(Data::positive).distinct().sorted(Comparator.reverseOrder()).toList();
        ProblemData.require(total>0&&count>0&&count<=100,"Weekly minutes and session count must be positive; maximum 100 sessions.");
        var result=new ArrayList<Integer>();
        ProblemData.require(fill(total,count,values,result,new HashSet<>()),"Weekly minutes cannot be divided into the selected session count and allowed lengths.");
        return List.copyOf(result);
    }
    static boolean fill(int remaining,int count,List<Integer> values,List<Integer> result,Set<String> failed){
        if(count==0)return remaining==0;
        if(remaining<count*values.getLast()||remaining>count*values.getFirst()||!failed.add(remaining+":"+count))return false;
        for(int value:values){result.add(value);if(fill(remaining-value,count-1,values,result,failed))return true;result.removeLast();}
        return false;
    }
}
