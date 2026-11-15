package edu.timetable;
import java.util.*;
import static edu.timetable.ProblemData.*;
import edu.timetable.Evaluator.*;
/** Independent feasibility and weighted-objective evaluation. */
final class DefaultEvaluator implements Evaluator {
    public Evaluation evaluate(ProblemData data,Candidate candidate){
        Score result=calculate(data,candidate.genes());
        return new Evaluation(result.valid(),result.minutes(),result.violations());
    }
    static Evaluator.Score calculate(ProblemData m,int[] genes){
        var errors=new ArrayList<String>();var gaps=new ArrayList<Gap>();
        Map<String,Map<String,List<Integer>>> agenda=new TreeMap<>();
        if(genes==null||genes.length!=m.events.size())return new Score(List.of("Wrong meeting count"),gaps,0);
        for(int i=0;i<genes.length;i++)if(genes[i]<0||genes[i]>=m.choices.get(i).size())return new Score(List.of("Invalid placement index for event "+i),gaps,0);
        for(int i=0;i<genes.length;i++){
            Time a=m.choices.get(i).get(genes[i]);Event e=m.events.get(i);
            for(int j=0;j<i;j++)for(String issue:m.conflicts(i,a,j,m.choices.get(j).get(genes[j])))
                errors.add(issue+": "+e.label()+" / "+m.events.get(j).label());
            for(String person:m.audience(e))agenda.computeIfAbsent(person,k->new TreeMap<>()).computeIfAbsent(a.day(),k->new ArrayList<>()).add(i);
        }
        errors.addAll(Objectives.overloads(m,genes));
        int sum=0;
        boolean custom=ScheduleObjective.goal(m)!=null;
        if(custom||errors.isEmpty())for(var person:agenda.entrySet())for(var day:person.getValue().entrySet()){
            var list=day.getValue();list.sort(Comparator.comparingInt(i->m.choices.get(i).get(genes[i]).start()));
            for(int k=1;k<list.size();k++){
                int i=list.get(k-1),j=list.get(k);var e=m.events.get(i);
                int elapsed=m.choices.get(j).get(genes[j]).start()-m.choices.get(i).get(genes[i]).start()-e.span();
                int finish=m.choices.get(i).get(genes[i]).start()+e.span();
                int excluded=custom?Math.min(elapsed,m.restBetween(day.getKey(),finish,finish+elapsed)):e.rest()+m.excluded(day.getKey(),finish+e.rest(),finish+elapsed);
                Gap gap=new Gap(person.getKey(),day.getKey(),i,j,elapsed,excluded);gaps.add(gap);sum+=gap.minutes();
            }
        }
        return new Score(errors,gaps,custom?ScheduleObjective.parts(m,genes).total():errors.isEmpty()?Objectives.total(m,Objectives.breakdown(m,genes,sum)):0);
    }
}
