package edu.timetable;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import com.google.gson.*;

/** Small UI-facing document. Conversion is the boundary to the unchanged solver model. */
final class SimpleProject {
    static final int MAX_ENROLLMENT=1_000_000;
    static final int MAX_HYBRID_POPULATION=1_000;
    static final List<String> WEEK=List.of("Sunday","Monday","Tuesday","Wednesday","Thursday");
    static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    int schemaVersion=1;
    String format="UniPlanSimple",university="",college="",department="";
    List<Group> groups=new ArrayList<>();
    List<Subject> subjects=new ArrayList<>();
    List<Day> days=new ArrayList<>();
    List<Room> rooms=new ArrayList<>();
    SavedResult result;
    ScheduleObjective.Goal optimizationGoal=ScheduleObjective.Goal.FEWER_GAPS;
    HybridStopping hybridStopping=HybridStopping.defaults();
    int hybridPopulation=16;
    int runtimeSeconds=60;
    List<Placement> pins;
    Map<String,List<String>> daysOff;
    Map<String,String> jointWith;
    List<Placement> pins(){return pins==null?List.of():pins;}
    List<String> off(String instructor){return daysOff==null?List.of():daysOff.getOrDefault(instructor,List.of());}
    String canonical(String id){return jointWith==null?id:jointWith.entrySet().stream().filter(e->e.getValue().equals(id)).map(Map.Entry::getKey).findFirst().orElse(id);}
    List<Subject> scheduledSubjects(){return subjects.stream().filter(s->jointWith==null||!jointWith.containsValue(s.id())).toList();}
    Subject subject(String id){return subjects.stream().filter(s->s.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown subject "+id));}
    Subject jointTarget(Subject s){return jointWith==null||!jointWith.containsKey(s.id())?null:subject(jointWith.get(s.id()));}
    int enrollment(Subject s){var target=jointTarget(s);return s.students()+(target==null?0:target.students());}
    int eventIndex(Placement pin){int index=0;for(var s:scheduledSubjects()){if(s.id().equals(canonical(pin.subjectId()))){if(pin.occurrence()<1||pin.occurrence()>s.sessions())throw new IllegalArgumentException("Invalid occurrence for "+s.name());return index+pin.occurrence()-1;}index+=s.sessions();}throw new IllegalArgumentException("Missing pinned subject "+pin.subjectId());}
    String pinName(Placement p){String name=subjects.stream().filter(s->s.id().equals(p.subjectId())).map(Subject::name).findFirst().orElse(p.subjectId());return name+" #"+p.occurrence()+" — "+p.day()+" "+Data.clock(p.start())+", "+p.room();}
    Placement placement(SearchEngine search,int event){var e=search.m.events.get(event);int occurrence=1;for(int i=0;i<event;i++)if(search.m.events.get(i).code().equals(e.code()))occurrence++;var p=search.m.choices.get(event).get(search.best[event]);return new Placement(e.code(),occurrence,p.day(),p.start(),p.room());}
    boolean isPinned(Placement p){return pins().stream().anyMatch(x->canonical(x.subjectId()).equals(canonical(p.subjectId()))&&x.occurrence()==p.occurrence());}
    void pin(Placement p){eventIndex(p);var list=new ArrayList<>(pins());list.removeIf(x->canonical(x.subjectId()).equals(canonical(p.subjectId()))&&x.occurrence()==p.occurrence());list.add(p);pins=list;}
    void unpin(Placement p){var list=new ArrayList<>(pins());list.removeIf(x->canonical(x.subjectId()).equals(canonical(p.subjectId()))&&x.occurrence()==p.occurrence());pins=list.isEmpty()?null:list;}
    void removeSubject(String id){subjects.removeIf(s->s.id().equals(id));if(pins!=null)pins.removeIf(p->p.subjectId().equals(id));if(jointWith!=null)jointWith.entrySet().removeIf(e->e.getKey().equals(id)||e.getValue().equals(id));}
    boolean compatible(Subject a,Subject b){return !a.id().equals(b.id())&&a.stage()==b.stage()&&!a.group().equals(b.group())&&a.name().equalsIgnoreCase(b.name())&&a.type().equals(b.type())&&a.sessions()==b.sessions()&&a.minutes()==b.minutes()&&a.instructor().trim().equals(b.instructor().trim());}
    void join(String sourceId,String targetId){var source=subject(sourceId);var target=subject(targetId);if(!compatible(source,target))throw new IllegalArgumentException("Joint classes need the same stage, subject name, type, sessions, length and instructor in different groups.");if(jointWith==null)jointWith=new LinkedHashMap<>();for(var e:jointWith.entrySet())if(!e.getKey().equals(sourceId)&&(Set.of(e.getKey(),e.getValue()).contains(sourceId)||Set.of(e.getKey(),e.getValue()).contains(targetId)))throw new IllegalArgumentException("A subject is already part of another joint class.");jointWith.put(sourceId,targetId);}
    List<Integer> moveOptions(SearchEngine search,int event){var options=new ArrayList<Integer>();if(isPinned(placement(search,event)))return options;for(int k=0;k<search.m.choices.get(event).size();k++){if(k==search.best[event])continue;int[] candidate=search.best.clone();candidate[event]=k;if(Evaluator.evaluate(search.m,candidate).valid())options.add(k);}return options;}
    SearchEngine move(SearchEngine search,int event,int choice){if(!moveOptions(search,event).contains(choice))throw new IllegalArgumentException("That slot conflicts with the timetable or the class is pinned.");int[] candidate=search.best.clone();candidate[event]=choice;var moved=new SearchEngine(search.m,search.settings,()->false);moved.inspect(candidate);moved.status="MANUAL";return moved;}
    SearchEngine newSearch(double seconds,java.util.function.BooleanSupplier cancelled){
        var search=new SearchEngine(new ProblemData(toData()),new SearchEngine.Settings(SearchEngine.freshSeed(),seconds,hybridPopulation,10,.12,Long.MAX_VALUE),cancelled);
        search.hybridStopping=hybridStopping;search.reportDiagnostics=false;return search;
    }
    record Placement(String subjectId,int occurrence,String day,int start,String room){}
    record SavedResult(String method,int seed,int score,List<Placement> placements,String inputHash){}
    String inputHash(){
        var inputs=JSON.toJsonTree(this).getAsJsonObject();inputs.remove("result");
        inputs.remove("hybridStopping");inputs.remove("hybridPopulation");inputs.remove("runtimeSeconds");
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(JSON.toJson(inputs).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    void captureResult(SearchEngine search,String method){
        if(search.best==null)return;var score=Evaluator.evaluate(search.m,search.best);if(!score.valid())throw new IllegalArgumentException("Cannot save an invalid timetable");
        var placements=new ArrayList<Placement>();var occurrences=new HashMap<String,Integer>();
        for(int i=0;i<search.best.length;i++){var e=search.m.events.get(i);var p=search.m.choices.get(i).get(search.best[i]);placements.add(new Placement(e.code(),occurrences.merge(e.code(),1,Integer::sum),p.day(),p.start(),p.room()));}
        result=new SavedResult(method,search.settings.seed(),score.minutes(),placements,inputHash());
    }
    SearchEngine restoreResult(){
        if(result==null||!inputHash().equals(result.inputHash()))return null;
        try{
            if(!Set.of("Hybrid","Random","Brute-force","CP","GA","Exhaustive","CP + Genetic","Manual").contains(result.method()))return null;
            var m=new ProblemData(toData());if(result.placements()==null||result.placements().size()!=m.events.size())return null;
            var placements=new HashMap<String,Placement>();for(var p:result.placements())if(p==null||placements.put(p.subjectId()+"#"+p.occurrence(),p)!=null)return null;
            int[] genes=new int[m.events.size()];var occurrences=new HashMap<String,Integer>();
            for(int i=0;i<genes.length;i++){String code=m.events.get(i).code();var saved=placements.get(code+"#"+occurrences.merge(code,1,Integer::sum));if(saved==null)return null;genes[i]=-1;
                for(int k=0;k<m.choices.get(i).size();k++){var p=m.choices.get(i).get(k);if(p.day().equals(saved.day())&&p.start()==saved.start()&&p.room().equals(saved.room())){genes[i]=k;break;}}if(genes[i]<0)return null;
            }
            var score=Evaluator.evaluate(m,genes);if(!score.valid()||score.minutes()!=result.score())return null;
            var search=new SearchEngine(m,new SearchEngine.Settings(result.seed(),60,16,100,.12,Long.MAX_VALUE),()->false);search.inspect(genes);search.status="RESTORED";return search;
        }catch(RuntimeException ex){return null;}
    }
    record Group(int stage,String name){String id(){return stage+" "+name;} public String toString(){return "Stage "+stage+" · Group "+name;}}
    record Subject(String id,int stage,String group,String name,String type,int students,int sessions,int minutes,String instructor){}
    record Break(int start,int end){}
    record Day(String name,boolean enabled,int start,int end,int classMinutes,List<Break> breaks){Day{breaks=List.copyOf(breaks);}}
    record Room(String name,String type,int capacity){}
    record Issue(boolean error,int step,String message){}
    static SimpleProject blank(){
        var p=new SimpleProject();for(int s=1;s<=4;s++)p.groups.add(new Group(s,"A"));
        for(String day:WEEK)p.days.add(new Day(day,true,540,900,90,List.of(new Break(630,660),new Break(750,780))));return p;
    }
    int defaultMinutes(){return days.stream().filter(Day::enabled).mapToInt(Day::classMinutes).findFirst().orElse(90);}
    String json(){return JSON.toJson(this);}
    static SimpleProject parse(String content){
        var p=JSON.fromJson(content,SimpleProject.class);
        if(p==null||!"UniPlanSimple".equals(p.format)||p.schemaVersion!=1||p.groups==null||p.subjects==null||p.days==null||p.rooms==null||p.university==null||p.college==null||p.department==null)
            throw new IllegalArgumentException("Open a UniPlan Simple JSON file (schema 1). UniPlan5 files use a different format.");
        if(p.optimizationGoal==null)throw new IllegalArgumentException("Choose a valid best solution goal.");
        if(p.hybridPopulation<2||p.hybridPopulation>MAX_HYBRID_POPULATION)throw new IllegalArgumentException("Initial hybrid population must be between 2 and "+MAX_HYBRID_POPULATION+".");
        if(p.hybridStopping==null||p.runtimeSeconds<1||p.runtimeSeconds>86400)throw new IllegalArgumentException("Invalid search stop settings.");
        if(p.days.size()!=5||!new HashSet<>(p.days.stream().map(Day::name).toList()).equals(new HashSet<>(WEEK)))throw new IllegalArgumentException("The file must contain Sunday through Thursday exactly once.");
        if(p.groups.stream().anyMatch(g->g==null||g.name()==null)||p.subjects.stream().anyMatch(s->s==null||s.id()==null||s.name()==null||s.type()==null||s.group()==null||s.instructor()==null)||p.rooms.stream().anyMatch(r->r==null||r.name()==null||r.type()==null))throw new IllegalArgumentException("The file contains a missing field.");
        if(p.pins!=null&&p.pins.stream().anyMatch(x->x==null||x.subjectId()==null||x.day()==null||x.room()==null))throw new IllegalArgumentException("A pin has a missing field.");
        if(p.daysOff!=null&&p.daysOff.entrySet().stream().anyMatch(e->e.getKey()==null||e.getValue()==null||e.getValue().stream().anyMatch(d->!WEEK.contains(d))))throw new IllegalArgumentException("Days off must use Sunday–Thursday.");
        if(p.jointWith!=null&&p.jointWith.entrySet().stream().anyMatch(e->e.getKey()==null||e.getValue()==null))throw new IllegalArgumentException("A joint class has a missing subject.");
        return p;
    }
    static SimpleProject load(Path file)throws IOException{return parse(Files.readString(file));}
    void save(Path file)throws IOException{
        Path target=file.toAbsolutePath();Files.createDirectories(target.getParent());Path tmp=Files.createTempFile(target.getParent(),"uniplan-", ".tmp");
        try{Files.writeString(tmp,json());try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}
    }
    static boolean validName(String value){return value!=null&&!value.isBlank()&&value.length()<=100&&!value.contains("\n");}
    static boolean validGroup(String value){return validName(value)&&!value.contains(",");}
    /** Breaks are fixed blocked periods; leftover time shorter than a full daily slot is unavailable. */
    static List<String[]> periods(Day day){
        var result=new ArrayList<String[]>();int cursor=day.start();
        var breaks=day.breaks().stream().sorted(Comparator.comparingInt(Break::start)).toList();
        for(var pause:breaks){teaching(result,day,cursor,pause.start());result.add(new String[]{day.name(),"Break",Data.clock(pause.start()),Data.clock(pause.end()),"Required break"});cursor=pause.end();}
        teaching(result,day,cursor,day.end());return result;
    }
    static void teaching(List<String[]> out,Day d,int start,int end){
        while(start+d.classMinutes()<=end){int next=start+d.classMinutes();out.add(new String[]{d.name(),"Class period",Data.clock(start),Data.clock(next),"Teaching"});start=next;}
        if(start<end)out.add(new String[]{d.name(),"Short remainder",Data.clock(start),Data.clock(end),"Unavailable"});
    }
    static String dayError(Day d){
        if(d.start()<0||d.end()>1439||d.start()>=d.end())return d.name()+": end must be after start (00:00–23:59).";
        if(d.classMinutes()<15||d.classMinutes()>240)return d.name()+": class length must be 15–240 minutes.";
        int previous=d.start();for(var b:d.breaks().stream().sorted(Comparator.comparingInt(Break::start)).toList()){
            if(b.start()<previous||b.end()<=b.start()||b.end()>d.end())return d.name()+": breaks must be inside the day, end after start and never overlap.";previous=b.end();
        }
        if(periods(d).stream().noneMatch(r->r[4].equals("Teaching")))return d.name()+": no complete class period fits. Shorten the class length or extend the day.";
        return "";
    }
    List<Issue> health(){
        var issues=new ArrayList<Issue>();
        if(!validName(university))issues.add(new Issue(true,0,"Enter University (1–100 characters)."));
        if(!validName(college))issues.add(new Issue(true,0,"Enter College (1–100 characters)."));
        if(!validName(department))issues.add(new Issue(true,0,"Enter Department (1–100 characters)."));
        var ids=new HashSet<String>();for(var g:groups)if(g.stage()<1||g.stage()>4||!validGroup(g.name())||!ids.add(g.id().toLowerCase(Locale.ROOT)))issues.add(new Issue(true,1,"Groups need a stage 1–4 and a unique nonempty name within that stage, without commas."));
        if(groups.isEmpty())issues.add(new Issue(true,1,"Add at least one stage/group."));
        if(subjects.isEmpty())issues.add(new Issue(true,2,"Add at least one subject."));
        if(days.stream().noneMatch(Day::enabled))issues.add(new Issue(true,3,"Select at least one teaching day."));
        for(var d:days)if(d.enabled()&&!dayError(d).isEmpty())issues.add(new Issue(true,3,dayError(d)));
        if(rooms.isEmpty())issues.add(new Issue(true,4,"Add at least one classroom or lab."));
        var roomNames=new HashSet<String>();for(var r:rooms)if(!validName(r.name())||!roomNames.add(r.name().toLowerCase(Locale.ROOT))||!Set.of("Classroom","Lab").contains(r.type())||r.capacity()<1||r.capacity()>MAX_ENROLLMENT)issues.add(new Issue(true,4,"Rooms need unique names, a Classroom/Lab type and capacity 1–1,000,000."));
        var subjectIds=new HashSet<String>();
        for(var s:subjects){
            if(!subjectIds.add(s.id())||!validName(s.name())||!Set.of("Theory","Lab").contains(s.type())||s.students()<1||s.students()>MAX_ENROLLMENT||s.sessions()<1||s.sessions()>20||s.minutes()<15||s.minutes()>240||groups.stream().noneMatch(g->g.stage()==s.stage()&&g.name().equals(s.group()))) {issues.add(new Issue(true,2,"Check subject "+s.name()+": group, type, enrollment, sessions and length."));continue;}
            int capacity=rooms.stream().filter(r->r.type().equals(s.type().equals("Theory")?"Classroom":"Lab")).mapToInt(Room::capacity).max().orElse(0);
            if(capacity<s.students())issues.add(new Issue(true,4,"Subject "+s.name()+" ("+s.stage()+s.group()+") has "+s.students()+" students but the biggest "+(s.type().equals("Theory")?"classroom":"lab")+" holds "+capacity+". Add a room or split the group."));
            if(days.stream().filter(Day::enabled).filter(d->dayError(d).isEmpty()).noneMatch(d->d.classMinutes()>=s.minutes()))issues.add(new Issue(true,3,"Subject "+s.name()+" needs "+s.minutes()+" minutes. Increase a day's class length to fit it."));
            if(s.instructor().isBlank())issues.add(new Issue(false,2,s.name()+" ("+s.stage()+s.group()+"): instructor not assigned; no real instructor conflict can be checked for this subject."));
        }
        for(var g:groups)if(subjects.stream().noneMatch(s->s.stage()==g.stage()&&s.group().equals(g.name())))issues.add(new Issue(false,2,g+" has no subjects yet."));
        if(jointWith!=null)for(var joint:jointWith.entrySet())try{var a=subject(joint.getKey());var b=subject(joint.getValue());if(!compatible(a,b)||jointWith.containsKey(b.id())||jointWith.values().stream().filter(id->id.equals(b.id())).count()!=1)throw new IllegalArgumentException("Joint subjects must match and belong to only one joint class");int capacity=rooms.stream().filter(r->r.type().equals(a.type().equals("Theory")?"Classroom":"Lab")).mapToInt(Room::capacity).max().orElse(0);if(enrollment(a)>capacity)issues.add(new Issue(true,4,"Joint class "+a.name()+" has "+enrollment(a)+" students; biggest suitable room holds "+capacity+"."));}catch(RuntimeException ex){issues.add(new Issue(true,2,"Joint class: "+ex.getMessage()));}
        if(daysOff!=null)for(var entry:daysOff.entrySet())if(days.stream().filter(Day::enabled).allMatch(d->entry.getValue().contains(d.name())))issues.add(new Issue(false,2,entry.getKey()+" has every teaching day off. Their classes cannot be scheduled."));
        var seenPins=new HashSet<Integer>();for(var pin:pins())try{int event=eventIndex(pin);if(pin.start()<ScheduleObjective.OPEN)throw new IllegalArgumentException("university opens at 09:00");var sub=subject(canonical(pin.subjectId()));if(!seenPins.add(event))throw new IllegalArgumentException("duplicate session pin");var day=days.stream().filter(d->d.enabled()&&d.name().equals(pin.day())).findFirst().orElseThrow(()->new IllegalArgumentException("day is not selected"));var room=rooms.stream().filter(r->r.name().equals(pin.room())).findFirst().orElseThrow(()->new IllegalArgumentException("room is missing"));if(!dayError(day).isEmpty()||periods(day).stream().noneMatch(p->p[4].equals("Teaching")&&Data.minute(p[2])<=pin.start()&&pin.start()+sub.minutes()<=Data.minute(p[3])&&(pin.start()-Data.minute(p[2]))%5==0))throw new IllegalArgumentException("outside an eligible teaching slot");if(!room.type().equals(sub.type().equals("Theory")?"Classroom":"Lab")||room.capacity()<enrollment(sub))throw new IllegalArgumentException("room type/capacity does not fit");if(off(sub.instructor().trim()).contains(pin.day()))throw new IllegalArgumentException("instructor is off that day");}catch(RuntimeException ex){issues.add(new Issue(true,6,"Pin "+pinName(pin)+": "+ex.getMessage()));}
        for(int i=0;i<pins().size();i++)for(int j=0;j<i;j++)try{var a=pins().get(i);var b=pins().get(j);var x=subject(canonical(a.subjectId()));var y=subject(canonical(b.subjectId()));var xgroups=new HashSet<>(List.of(x.group()));var xt=jointTarget(x);if(xt!=null)xgroups.add(xt.group());var ygroups=new HashSet<>(List.of(y.group()));var yt=jointTarget(y);if(yt!=null)ygroups.add(yt.group());xgroups.retainAll(ygroups);if(a.day().equals(b.day())&&a.start()<b.start()+y.minutes()&&b.start()<a.start()+x.minutes()&&(a.room().equals(b.room())||!x.instructor().isBlank()&&x.instructor().trim().equals(y.instructor().trim())||x.stage()==y.stage()&&!xgroups.isEmpty()))issues.add(new Issue(true,6,"Conflicting pins: "+pinName(a)+" / "+pinName(b)));}catch(RuntimeException ignored){}
        return issues;
    }
    Data toData(){
        var errors=health().stream().filter(Issue::error).toList();if(!errors.isEmpty())throw new IllegalArgumentException(errors.getFirst().message());
        Data d=new Data();d.rows.get("Campus").add(new String[]{university,department,"Weekly timetable","5","90","0","after","yes","groups"});
        d.rows.get("Institution").add(new String[]{college,"1","1","0"});
        d.rows.get("Weights").add(new String[]{"1","0","1","0","0","0","0","0","0"});
        d.rows.get("Preferences").add(new String[]{"00:00","23:59","12:00","13:00",Integer.toString(Math.min(8,Runtime.getRuntime().availableProcessors()))});
        d.rows.get("SearchObjective").add(new String[]{optimizationGoal.name()});
        for(int s=1;s<=4;s++)d.rows.get("Stages").add(new String[]{"Stage "+s});
        for(var g:groups)d.rows.get("Groups").add(new String[]{g.id(),"Stage "+g.stage()});
        for(var day:days)if(day.enabled()){d.rows.get("Days").add(new String[]{day.name(),Data.clock(day.start()),Data.clock(day.end())});d.rows.get("Periods").addAll(periods(day));}
        var teachers=new HashSet<String>();for(var s:scheduledSubjects()){
            String teacher=s.instructor().isBlank()?"__unassigned_"+s.id():s.instructor().trim();
            if(teachers.add(teacher)){String allowed=days.stream().filter(Day::enabled).map(Day::name).filter(day->!off(teacher).contains(day)).map(day->day+"@00:00-23:59").collect(java.util.stream.Collectors.joining(","));d.rows.get("Teachers").add(new String[]{teacher,"",allowed.isEmpty()?"Sunday@00:00-00:01":allowed});}
            d.rows.get("Subjects").add(new String[]{s.id(),s.name(),teacher,new Group(s.stage(),s.group()).id()+(jointTarget(s)==null?"":","+new Group(jointTarget(s).stage(),jointTarget(s).group()).id()),"",""+enrollment(s),s.type().equals("Theory")?"Lecture":"Lab",""+s.sessions(),""+s.minutes(),"0","after","yes"});
        }
        for(var r:rooms)d.rows.get("Rooms").add(new String[]{r.name(),r.type().equals("Classroom")?"Lecture":"Lab",""+r.capacity(),"*"});for(var pin:pins())d.rows.get("Locks").add(new String[]{Integer.toString(eventIndex(pin)),"pin",pin.day(),Data.clock(pin.start()),pin.room()});return d;
    }
    int copySubjects(Group source,List<Group> targets){
        var original=subjects.stream().filter(s->s.stage()==source.stage()&&s.group().equals(source.name())).toList();int count=0;
        for(var target:new LinkedHashSet<>(targets)){
            if(target.equals(source)||!groups.contains(target))continue;
            for(var subject:original){
                if(subjects.stream().anyMatch(s->s.stage()==target.stage()&&s.group().equals(target.name())&&s.name().equalsIgnoreCase(subject.name())))continue;
                subjects.add(new Subject(UUID.randomUUID().toString(),target.stage(),target.name(),subject.name(),subject.type(),subject.students(),subject.sessions(),subject.minutes(),subject.instructor()));count++;
            }
        }
        return count;
    }
    static SimpleProject tinyDemo(){
        var p=demo();p.groups=new ArrayList<>(List.of(new Group(1,"A")));p.subjects.clear();p.rooms=new ArrayList<>(List.of(new Room("Tiny room","Classroom",40)));
        for(int i=0;i<6;i++)p.subjects.add(new Subject("tiny-"+i,1,"A","Tiny subject "+(i+1),"Theory",30,1,30,"Tiny instructor"));
        for(int i=0;i<p.days.size();i++)p.days.set(i,new Day(WEEK.get(i),i==0,540,720,30,List.of()));return p;
    }
    static SimpleProject demo(){
        var p=blank();p.university="Salahaddin University";p.college="College of Engineering";p.department="Software & Informatics Engineering";p.groups.clear();
        String[][] names={{"Programming I","Mathematics I","English","Programming Lab","Computer Fundamentals Lab"},{"Data Structures","Database Systems","Mathematics II","Database Lab","Networks Lab"},{"Operating Systems","Web Development","Software Testing","Web Lab","Systems Lab"},{"Artificial Intelligence","Machine Learning","Optimization Methods","AI Lab","Embedded Systems Lab"}};
        for(int stage=1;stage<=4;stage++)for(String group:List.of("A","B")){
            p.groups.add(new Group(stage,group));for(int n=0;n<5;n++)p.subjects.add(new Subject("S"+stage+group+n,stage,group,names[stage-1][n],n<3?"Theory":"Lab",n<3?35:25,n<3?2:1,90,"Demo Instructor "+(1+(stage*3+n)%14)));
        }
        for(int r=1;r<=10;r++)p.rooms.add(new Room("Classroom "+r,"Classroom",r<=5?40:60));
        for(int r=1;r<=4;r++)p.rooms.add(new Room("Lab "+r,"Lab",30));return p;
    }
}
