package edu.timetable;
import java.util.*;
/** Safe necessary-condition checks; passing them does not prove feasibility. */
final class HealthReport {
    static List<String> inspect(ProblemData m){
        var out=new ArrayList<String>();
        for(int i=0;i<m.events.size();i++)if(m.choices.get(i).isEmpty()){
            var e=m.events.get(i);int capacity=m.data.rows.get("Rooms").stream().filter(r->r[1].equals(e.type())).mapToInt(r->Data.positive(r[2])).max().orElse(0);
            out.add(m.diagnostics.get(i));
            if(capacity>0&&capacity<e.seats())out.add("Suggestion: "+e.label()+" needs at least "+((e.seats()+capacity-1)/capacity)+" sections of at most "+capacity+" students. Create actual attendance groups before scheduling.");
        }
        for(var teacher:m.data.rows.get("Teachers")){
            int demand=m.events.stream().filter(e->e.teacher().equals(teacher[0])).mapToInt(Objectives::teaching).sum();
            for(var limit:m.data.rows.get("Workloads"))if(limit[0].equals(teacher[0])&&!limit[2].isBlank()&&demand>Data.positive(limit[2]))out.add("Instructor overload: "+teacher[0]+" requires "+demand+" weekly minutes, limit "+limit[2]);
        }
        var types=new TreeSet<String>();m.events.forEach(e->types.add(e.type()));
        for(String type:types){
            int demand=m.events.stream().filter(e->e.type().equals(type)).mapToInt(e->e.roomIntervals(0).stream().mapToInt(r->r[1]-r[0]).sum()).sum();
            int available=0;
            for(var room:m.data.rows.get("Rooms"))if(room[1].equals(type))for(var day:m.data.rows.get("Days")){
                for(int t=Data.minute(day[1]);t<Data.minute(day[2]);t++){
                    final int minute=t;
                    boolean open=m.periods.isEmpty()||m.periods.stream().anyMatch(p->p[0].equals(day[0])&&p[4].equals("Teaching")&&Data.minute(p[2])<=minute&&minute<Data.minute(p[3]));
                    if(open&&ProblemData.available(room[3],day[0],t,t+1))available++;
                }
            }
            if(demand>available)out.add("Room overload: "+type+" needs "+demand+" room-minutes but has "+available);
        }
        return out;
    }
}
