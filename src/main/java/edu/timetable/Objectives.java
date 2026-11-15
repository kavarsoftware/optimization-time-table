package edu.timetable;
import java.util.*;
import static edu.timetable.Data.*;
/** Nine nonnegative integer penalties; weights multiply explicitly reported units. */
final class Objectives {
    static final String[] NAMES={"Student gaps","Instructor gaps","Balance","Early late","Lunch","Single class days","Room consistency","Wasted seats","Preferred days"};
    static int[] weights(ProblemData m){
        if(m.data.rows.get("Weights").isEmpty())return new int[]{1,0,0,0,0,0,0,0,0};
        var w=Arrays.stream(m.data.rows.get("Weights").getFirst()).mapToInt(Integer::parseInt).toArray();
        ProblemData.require(Arrays.stream(w).allMatch(v->v>=0&&v<=10000),"Weights must be 0–10000.");return w;
    }
    static String[] preferences(ProblemData m){return m.data.rows.get("Preferences").isEmpty()?new String[]{"09:00","15:00","12:00","13:00","1"}:m.data.rows.get("Preferences").getFirst();}
    static int teaching(ProblemData.Event e){return e.duration()-(e.mode().equals("inside")?e.pause():0);}
    static long[] breakdown(ProblemData m,int[] genes,int studentGaps){
        long[] score=new long[9];score[0]=studentGaps;
        var teacherAgenda=new TreeMap<String,List<Integer>>();var groupDays=new TreeMap<String,List<Integer>>();
        var roomSets=new TreeMap<String,Set<String>>();var groups=new TreeSet<String>();
        var capacities=new HashMap<String,Integer>();for(var r:m.data.rows.get("Rooms"))capacities.put(r[0],positive(r[2]));
        var prefs=preferences(m);
        for(int i=0;i<genes.length;i++){
            var e=m.events.get(i);var p=m.choices.get(i).get(genes[i]);
            teacherAgenda.computeIfAbsent(e.teacher()+"\0"+p.day(),k->new ArrayList<>()).add(i);
            for(String g:m.audience(e)){groups.add(g);groupDays.computeIfAbsent(g+"\0"+p.day(),k->new ArrayList<>()).add(i);}
            roomSets.computeIfAbsent(e.code(),k->new HashSet<>()).add(p.room());
            score[3]+=Math.max(0,minute(prefs[0])-p.start())+Math.max(0,p.start()+e.span()-minute(prefs[1]));
            score[4]+=Math.max(0,Math.min(p.start()+e.span(),minute(prefs[3]))-Math.max(p.start(),minute(prefs[2])));
            score[7]+=capacities.get(p.room())-e.seats();
            for(var t:m.data.rows.get("Workloads"))if(t[0].equals(e.teacher())&&!t[3].isBlank()&&!names(t[3]).contains(p.day()))score[8]++;
        }
        for(var list:teacherAgenda.values()){
            list.sort(Comparator.comparingInt(i->m.choices.get(i).get(genes[i]).start()));
            for(int k=1;k<list.size();k++){int a=list.get(k-1),b=list.get(k);var x=m.choices.get(a).get(genes[a]);var y=m.choices.get(b).get(genes[b]);
                score[1]+=m.activeMinute(x.day(),y.start())-m.activeMinute(x.day(),x.start()+m.events.get(a).span());}
        }
        for(String group:groups){
            long total=0;var loads=new ArrayList<Integer>();
            for(String day:m.days){var list=groupDays.getOrDefault(group+"\0"+day,List.of());
                int load=list.stream().mapToInt(i->teaching(m.events.get(i))).sum();loads.add(load);total+=load;
                if(list.size()==1)score[5]++;
            }
            for(int load:loads)score[2]+=Math.abs(m.days.size()*load-total);
        }
        for(var rooms:roomSets.values())score[6]+=rooms.size()-1;
        return score;
    }
    static int total(ProblemData m,long[] penalties){
        int[] w=weights(m);long result=0;for(int i=0;i<w.length;i++)result+=w[i]*penalties[i];
        if(result>Integer.MAX_VALUE)throw new IllegalArgumentException("Objective exceeds integer limit. Reduce weights or dataset size.");
        return (int)result;
    }
    static List<String> overloads(ProblemData m,int[] genes){
        var errors=new ArrayList<String>();
        for(var rule:m.data.rows.get("Workloads")){
            int weekly=0;var daily=new HashMap<String,Integer>();
            for(int i=0;i<genes.length;i++)if(m.events.get(i).teacher().equals(rule[0])){
                int minutes=teaching(m.events.get(i));weekly+=minutes;daily.merge(m.choices.get(i).get(genes[i]).day(),minutes,Integer::sum);
            }
            if(!rule[2].isBlank()&&weekly>positive(rule[2]))errors.add(rule[0]+" exceeds weekly teaching limit");
            for(var day:daily.entrySet())if(!rule[1].isBlank()&&day.getValue()>positive(rule[1]))errors.add(rule[0]+" exceeds daily limit on "+day.getKey());
        }
        return errors;
    }
}
