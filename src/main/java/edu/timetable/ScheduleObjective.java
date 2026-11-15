package edu.timetable;

import java.util.*;

/** Weekly penalties, summed once per student group on each day with classes. */
final class ScheduleObjective {
    static final int OPEN=9*60;
    enum Goal {
        FEWER_GAPS("Least gap between subjects"),EARLIER_FINISH("Earlier finishes of the day");
        final String label;
        Goal(String label){this.label=label;}
        public String toString(){return label;}
    }
    static Goal goal(ProblemData m){
        var rows=m.data.rows.get("SearchObjective");
        return rows.isEmpty()?null:Goal.valueOf(rows.getFirst()[0]);
    }
    record Parts(int gaps,int starts){int total(){return Math.addExact(gaps,starts);}}
    static Parts parts(ProblemData m,int[] genes){
        var agendas=new TreeMap<String,List<Integer>>();
        for(int i=0;i<genes.length;i++){
            var p=m.choices.get(i).get(genes[i]);
            for(String group:m.audience(m.events.get(i)))agendas.computeIfAbsent(group+"\0"+p.day(),k->new ArrayList<>()).add(i);
        }
        int gaps=0,starts=0;
        for(var list:agendas.values()){
            list.sort(Comparator.comparingInt((Integer i)->m.choices.get(i).get(genes[i]).start()).thenComparingInt(i->i));
            if(goal(m)==Goal.EARLIER_FINISH)starts=Math.addExact(starts,Math.max(0,m.choices.get(list.getFirst()).get(genes[list.getFirst()]).start()-OPEN));
            for(int k=1;k<list.size();k++){
                int a=list.get(k-1),b=list.get(k);
                var previous=m.choices.get(a).get(genes[a]);var next=m.choices.get(b).get(genes[b]);
                int finish=previous.start()+m.events.get(a).span();
                int gap=next.start()-finish;
                gaps=Math.addExact(gaps,Math.max(0,gap-m.restBetween(previous.day(),finish,next.start())));
            }
        }
        return new Parts(gaps,starts);
    }
}
