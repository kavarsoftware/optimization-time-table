package edu.timetable;
import java.util.*;
import static edu.timetable.Data.*;

class ProblemData {
    record Time(String day,int start,String room){}
    record Event(String code,String name,String teacher,Set<String> groups,Set<String> students,int seats,String type,int duration,int pause,String mode,boolean reserve,int occurrence){
        int span(){return duration+(mode.equals("split")?pause:0);}
        int rest(){return mode.equals("after")?pause:0;}
        int splitAt(){return (span()-pause)/2;}
        String label(){return name+" #"+occurrence;}
        List<int[]> roomIntervals(int start){
            if(reserve||pause==0||mode.equals("after"))return List.of(new int[]{start,start+span()});
            int middle=start+splitAt();
            return List.of(new int[]{start,middle},new int[]{middle+pause,start+span()});
        }
    }
    final List<Event> events=new ArrayList<>();
    final List<List<Time>> choices=new ArrayList<>();
    final List<String[]> rules;
    final Map<String,String> stages=new LinkedHashMap<>();
    final List<String> days=new ArrayList<>();
    final boolean individual;
    final Data data;
    final List<String[]> periods;
    final List<String> diagnostics=new ArrayList<>();
    ProblemData(Data d){
        data=Data.parse(d.text());
        TermCalendar.dates(d);
        if(d.rows.get("Campus").size()!=1)throw new IllegalArgumentException("Enter exactly one campus row.");
        String[] c=d.rows.get("Campus").getFirst();int step=positive(c[3]);
        individual=switch(c[8]){case "students"->true;case "groups"->false;default->throw new IllegalArgumentException("Score: groups or students");};
        periods=d.rows.get("Periods").stream().map(String[]::clone).toList();
        var teachers=index(d,"Teachers");var rooms=index(d,"Rooms");var stage=index(d,"Stages");var calendar=index(d,"Days");
        for(var row:index(d,"Groups").values()){require(stage.containsKey(row[1]),"Unknown stage "+row[1]);stages.put(row[0],row[1]);}
        for(var day:calendar.values()){require(minute(day[2])>minute(day[1]),"Invalid teaching window");days.add(day[0]);}
        for(var p:periods){
            require(calendar.containsKey(p[0]),"Unknown period day "+p[0]);
            int start=minute(p[2]),end=minute(p[3]);var day=calendar.get(p[0]);
            require(start<end&&start>=minute(day[1])&&end<=minute(day[2]),"Invalid period range: "+p[1]);
            require(Set.of("Teaching","Required break","Unavailable").contains(p[4]),"Unknown period kind");
        }
        for(int i=0;i<periods.size();i++)for(int j=0;j<i;j++){
            var a=periods.get(i);var b=periods.get(j);
            require(!a[0].equals(b[0])||!overlap(minute(a[2]),minute(a[3]),minute(b[2]),minute(b[3])),"Overlapping periods: "+a[1]+" / "+b[1]);
        }
        Set<String> codes=new HashSet<>();
        for(var s:d.rows.get("Subjects")){
            var groups=names(s[3]);var students=names(s[4]);codes.add(s[0]);
            require(!groups.isEmpty()&&stages.keySet().containsAll(groups),"Check groups for "+s[0]);
            require(teachers.containsKey(s[2]),"Unknown teacher "+s[2]);
            int seats=positive(s[5]);require(students.size()<=seats&&(!individual||students.size()==seats),"Check enrollment list for "+s[0]);
            int duration=positive(or(s[8],c[4])),pause=Integer.parseInt(or(s[9],c[5]));
            String mode=or(s[10],c[6]);require(Set.of("split","inside","after").contains(mode)&&pause>=0&&pause<=1440&&(!mode.equals("inside")||pause<duration),"Invalid break for "+s[0]);
            List<Integer> lengths=Collections.nCopies(positive(s[7]),duration);
            for(var part:d.rows.get("Parts"))if(part[0].equals(s[0])&&names(part[1]).equals(groups)&&part[2].equals(s[6]))
                lengths=WeeklyTime.lengths(positive(part[3]),positive(part[5]),part[4]).stream().map(value->value+(mode.equals("inside")?pause:0)).toList();
            for(int i=1;i<=lengths.size();i++){
                duration=lengths.get(i-1);
                require(!mode.equals("inside")||pause<duration,"Inside break must be shorter than each session: "+s[0]);
                Event e=new Event(s[0],s[1],s[2],groups,students,seats,s[6],duration,pause,mode,flag(or(s[11],c[7])),i);
                events.add(e);List<Time> domain=new ArrayList<>();
                for(var day:calendar.values())for(int t:starts(day,e,step)){
                    if(!available(teachers.get(e.teacher)[2],day[0],t,t+e.span()))continue;
                    for(var room:rooms.values()){
                        if(!room[1].equals(e.type)||positive(room[2])<seats)continue;
                        boolean ok=true;for(int[] p:e.roomIntervals(t))ok &= available(room[3],day[0],p[0],p[1]);
                        if(ok)domain.add(new Time(day[0],t,room[0]));
                    }
                }
                if(ScheduleObjective.goal(this)!=null)domain.removeIf(p->p.start()<ScheduleObjective.OPEN);
                int eventIndex=events.size()-1;
                for(var lock:d.rows.get("Locks"))if(Integer.parseInt(lock[0])==eventIndex){
                    require(Set.of("pin","forbid").contains(lock[1]),"Lock kind must be pin or forbid");
                    domain.removeIf(p->{
                        boolean same=p.day().equals(lock[2])&&p.start()==minute(lock[3])&&(lock[4].isBlank()||p.room().equals(lock[4]));
                        return lock[1].equals("pin")?!same:same;
                    });
                }
                choices.add(List.copyOf(domain));
                if(domain.isEmpty()){
                    int capacity=rooms.values().stream().filter(r->r[1].equals(e.type)).mapToInt(r->positive(r[2])).max().orElse(0);
                    diagnostics.add(capacity<seats?e.label()+" has "+seats+" students; largest "+e.type+" holds "+capacity+". Add a larger room or create smaller sections.":e.label()+" needs "+e.span()+" minutes. No available window matches its duration, instructor and room availability.");
                }else diagnostics.add("");

            }
        }
        require(!events.isEmpty(),"Enter at least one subject.");
        for(var lock:d.rows.get("Locks"))require(Integer.parseInt(lock[0])>=0&&Integer.parseInt(lock[0])<events.size(),"Lock refers to missing meeting; update or remove it.");
        rules=new ArrayList<>();
        for(var r:d.rows.get("Rules"))if(flag(r[0])){
            require(Set.of("different days","no overlap","once daily","after","consecutive").contains(r[1]),"Unknown rule "+r[1]);
            require(codes.contains(r[2])&&(r[1].equals("once daily")||codes.contains(r[3])),"Unknown subject in rule");
            require(r[4].isBlank()||stages.containsKey(r[4]),"Unknown rule group");rules.add(r.clone());
        }
    }
    List<Integer> starts(String[] day,Event event,int step){
        var starts=new TreeSet<Integer>();
        if(periods.isEmpty()){
            for(int t=minute(day[1]);t+event.span()<=minute(day[2]);t+=step)starts.add(t);
        }else{
            var windows=periods.stream().filter(p->p[0].equals(day[0])&&p[4].equals("Teaching")).sorted(Comparator.comparingInt(p->minute(p[2]))).toList();
            boolean span=!data.rows.get("Term").isEmpty()&&flag(data.rows.get("Term").getFirst()[4]);
            for(int i=0;i<windows.size();i++){
                int begin=minute(windows.get(i)[2]),end=minute(windows.get(i)[3]);
                if(span)while(i+1<windows.size()&&minute(windows.get(i+1)[2])==end)end=minute(windows.get(++i)[3]);
                for(int t=begin;t+event.span()<=end;t+=step)starts.add(t);
            }
        }
        return List.copyOf(starts);
    }
    // Compress globally excluded time; equal objective in the evaluator and CP model.
    int activeMinute(String day,int time){
        int excluded=0;
        for(var p:periods)if(p[0].equals(day)&&!p[4].equals("Teaching"))
            excluded+=Math.max(0,Math.min(time,minute(p[3]))-minute(p[2]));
        return time-excluded;
    }
    int excluded(String day,int start,int end){
        return end-start-(activeMinute(day,end)-activeMinute(day,start));
    }
    /** A clock that removes scheduled rest only, keeping unavailable remainders chargeable. */
    int restMinute(String day,int time){
        int rest=0;
        for(var p:periods)if(p[0].equals(day)&&p[4].equals("Required break"))
            rest+=Math.max(0,Math.min(time,minute(p[3]))-minute(p[2]));
        return time-rest;
    }
    int restBetween(String day,int start,int end){
        return Math.max(0,end-start-(restMinute(day,end)-restMinute(day,start)));
    }
    static Map<String,String[]> index(Data d,String table){
        Map<String,String[]> map=new LinkedHashMap<>();for(var r:d.rows.get(table))require(!r[0].isBlank()&&map.put(r[0],r)==null,"Duplicate or empty name in "+table);return map;
    }
    static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
    static boolean available(String windows,String day,int start,int end){
        if(windows.isBlank()||windows.equals("*"))return true;
        for(String window:windows.split(",")){String[] w=window.trim().split("@"), t=w[1].split("-");if(w[0].equals(day)&&minute(t[0])<=start&&end<=minute(t[1]))return true;}
        return false;
    }
    Set<String> audience(Event e){return individual?e.students:e.groups;}
    static boolean overlap(int a,int b,int c,int d){return a<d&&c<b;}
    String conflict(int i,Time a,int j,Time b){
        var issues=conflicts(i,a,j,b);return issues.isEmpty()?null:issues.getFirst();
    }
    List<String> conflicts(int i,Time a,int j,Time b){
        var issues=new ArrayList<String>();
        Event left=events.get(i),right=events.get(j);
        for(var r:rules)if((r[1].equals("after")||r[1].equals("consecutive"))&&(r[4].isBlank()||left.groups.contains(r[4])&&right.groups.contains(r[4]))){
            boolean forward=left.code.equals(r[2])&&right.code.equals(r[3]),reverse=right.code.equals(r[2])&&left.code.equals(r[3]);
            if(!forward&&!reverse)continue;
            Time first=forward?a:b,second=forward?b:a;Event previous=forward?left:right;
            int finish=days.indexOf(first.day)*2880+first.start+previous.span()+previous.rest();
            int next=days.indexOf(second.day)*2880+second.start;
            if(r[1].equals("after")&&next<finish)issues.add("Required subject order");
            if(r[1].equals("consecutive")&&(!first.day.equals(second.day)||next!=finish))issues.add("Consecutive classes rule");
        }
        if(!a.day.equals(b.day))return issues;
        Event x=events.get(i),y=events.get(j);
        boolean simultaneous=overlap(a.start,a.start+x.span(),b.start,b.start+y.span());
        if(x.teacher.equals(y.teacher)&&simultaneous)issues.add("Instructor "+x.teacher+" overlaps");
        if(!Collections.disjoint(x.groups,y.groups)||!Collections.disjoint(x.students,y.students)){
            if(overlap(a.start,a.start+x.span()+x.rest(),b.start,b.start+y.span()+y.rest()))issues.add("Student overlap or required rest");
        }
        if(a.room.equals(b.room)&&x.roomIntervals(a.start).stream().anyMatch(p->y.roomIntervals(b.start).stream().anyMatch(q->overlap(p[0],p[1],q[0],q[1]))))
            issues.add("Room "+a.room+" overlaps");
        for(var r:rules){
            if(r[1].equals("after")||r[1].equals("consecutive"))continue;
            if(!r[4].isBlank()&&!(x.groups.contains(r[4])&&y.groups.contains(r[4])))continue;
            String second=r[1].equals("once daily")?r[2]:r[3];
            if(!(x.code.equals(r[2])&&y.code.equals(second)||y.code.equals(r[2])&&x.code.equals(second)))continue;
            if(!r[1].equals("no overlap")||simultaneous)issues.add(r[1]+" rule");
        }
        return issues;
    }
}
