package edu.timetable;
import com.google.ortools.sat.*;
import java.util.*;
/** Interval CP model. Resource conflicts use NoOverlap, never placement-pair clauses. */
class ConstraintSearch implements SearchEngine.ConstraintSolver {
    static {NativeSolver.load();}
    static LinearExpr offset(IntVar start,int value){return LinearExpr.newBuilder().add(start).add(value).build();}
    static void resource(Map<String,List<IntervalVar>> map,String key,IntervalVar interval){map.computeIfAbsent(key,k->new ArrayList<>()).add(interval);}
    public int[] solve(SearchEngine run,int[] child,boolean optimize,double seconds){
        long deadline=System.nanoTime()+(long)(seconds*1e9);ProblemData m=run.m;CpModel cp=new CpModel();int n=m.events.size();
        BoolVar[][] take=new BoolVar[n][];IntVar[] start=new IntVar[n],day=new IntVar[n];
        Map<String,List<IntervalVar>> resources=new LinkedHashMap<>();
        boolean[] move=new boolean[n];
        if(child!=null){
            if(optimize){for(int i=0;i<n;i++)move[i]=run.rng.nextDouble()<.3;move[run.rng.nextInt(n)]=true;}
            else for(int i=0;i<n;i++)for(int j=0;j<i;j++)if(m.conflict(i,m.choices.get(i).get(child[i]),j,m.choices.get(j).get(child[j]))!=null)move[i]=move[j]=true;
            if(!optimize&&!Evaluator.evaluate(m,child).valid()&&!any(move))Arrays.fill(move,true);
        }
        for(int i=0;i<n;i++){
            if(run.stop()||System.nanoTime()>deadline)return null;
            var e=m.events.get(i);take[i]=new BoolVar[m.choices.get(i).size()];
            var starts=LinearExpr.newBuilder();var days=LinearExpr.newBuilder();
            Map<String,List<BoolVar>> rooms=new LinkedHashMap<>();
            for(int k=0;k<take[i].length;k++){
                var p=m.choices.get(i).get(k);var selected=cp.newBoolVar("e"+i+"p"+k);take[i][k]=selected;
                int d=m.days.indexOf(p.day());starts.addTerm(selected,d*2880+p.start());days.addTerm(selected,d);
                rooms.computeIfAbsent(p.room(),r->new ArrayList<>()).add(selected);
            }
            cp.addExactlyOne(take[i]);start[i]=cp.newIntVar(0,m.days.size()*2880,"start"+i);day[i]=cp.newIntVar(0,m.days.size()-1,"day"+i);
            cp.addEquality(start[i],starts);cp.addEquality(day[i],days);
            if(child!=null&&!move[i])cp.addEquality(take[i][child[i]],1);
            var teaching=cp.newFixedSizeIntervalVar(start[i],e.span(),"class"+i);
            resource(resources,"teacher:"+e.teacher(),teaching);
            var attending=cp.newFixedSizeIntervalVar(start[i],e.span()+e.rest(),"attendance"+i);
            for(String group:e.groups())resource(resources,"group:"+group,attending);
            for(String student:e.students())resource(resources,"student:"+student,attending);
            for(var room:rooms.entrySet()){
                var selected=cp.newBoolVar("room"+i+room.getKey());cp.addEquality(selected,LinearExpr.sum(room.getValue().toArray(BoolVar[]::new)));
                for(var range:e.roomIntervals(0))
                    resource(resources,"room:"+room.getKey(),cp.newOptionalFixedSizeIntervalVar(offset(start[i],range[0]),range[1]-range[0],selected,"roomInterval"+i));
            }
        }
        // Simple mode has no post-class rest: blocked periods can also be explicit fixed intervals.
        // Legacy rest policies remain enforced by their original domain/attendance semantics.
        if(m.events.stream().allMatch(e->e.pause()==0))for(var p:m.periods)if(!p[4].equals("Teaching")){
            int begin=m.days.indexOf(p[0])*2880+Data.minute(p[2]);
            var blocked=cp.newFixedSizeIntervalVar(cp.newConstant(begin),Data.minute(p[3])-Data.minute(p[2]),"blockedPeriod");
            resources.values().forEach(intervals->intervals.add(blocked));
        }
        for(var intervals:resources.values())if(intervals.size()>1)cp.addNoOverlap(intervals);
        addRules(cp,m,start,day);
        workload(cp,m,take);
        if(optimize){if(ScheduleObjective.goal(m)!=null)addScheduleObjective(cp,m,take,start);else addObjective(cp,m,take);}
        else if(child==null)for(int i=0;i<n;i++)cp.addHint(take[i][run.rng.nextInt(take[i].length)],1);
        if(child==null&&run.hint!=null)for(int i=0;i<n;i++)for(int k=0;k<take[i].length;k++)cp.addHint(take[i][k],k==run.hint[i]?1:0);
        double remaining=Math.min(run.left(),(deadline-System.nanoTime())/1e9);if(remaining<=0||run.stop())return null;
        CpSolver solver=new CpSolver();solver.getParameters().setMaxTimeInSeconds(remaining).setNumSearchWorkers(run.workers).setRandomSeed(run.rng.nextInt(Integer.MAX_VALUE));
        final RuntimeException[] mismatch={null};
        var callback=new CpSolverSolutionCallback(){
            public void onSolutionCallback(){
                int[] candidate=new int[n];for(int i=0;i<n;i++)for(int k=0;k<take[i].length;k++)if(booleanValue(take[i][k]))candidate[i]=k;
                var evaluation=Evaluator.evaluate(m,candidate);
                if(!evaluation.valid()||optimize&&!objectiveAgrees(objectiveValue(),evaluation.minutes(),false)){
                    mismatch[0]=new IllegalStateException("CP/evaluator disagreement: "+evaluation.violations()+" CP="+objectiveValue()+" evaluator="+evaluation.minutes());stopSearch();return;
                }
                run.inspect(candidate);if(run.stop())stopSearch();
            }
        };
        Thread watcher=Thread.ofVirtual().start(()->{try{while(!Thread.currentThread().isInterrupted()){if(run.stop())solver.stopSearch();Thread.sleep(40);}}catch(InterruptedException ignored){Thread.currentThread().interrupt();}});
        CpSolverStatus result;
        try{result=solver.solve(cp,callback);}finally{watcher.interrupt();}
        if(mismatch[0]!=null)throw mismatch[0];
        if(result==CpSolverStatus.INFEASIBLE&&child==null)run.proved=true;
        if(result!=CpSolverStatus.FEASIBLE&&result!=CpSolverStatus.OPTIMAL)return null;
        if(optimize&&child==null&&result==CpSolverStatus.OPTIMAL)run.proved=true;
        int[] answer=new int[n];for(int i=0;i<n;i++)for(int k=0;k<take[i].length;k++)if(solver.booleanValue(take[i][k]))answer[i]=k;
        // CP-SAT may report a loose auxiliary objective for intermediate incumbents.
        // The evaluator gives the exact score of their selected timetable. At proven
        // optimality the auxiliary objective must be tight; never conceal a mismatch.
        var evaluation=Evaluator.evaluate(m,answer);
        if(!evaluation.valid()||optimize&&!objectiveAgrees(solver.objectiveValue(),evaluation.minutes(),result==CpSolverStatus.OPTIMAL))
            throw new IllegalStateException("Final CP/evaluator disagreement: CP="+solver.objectiveValue()+" evaluator="+evaluation.minutes());
        return answer;
    }
    static boolean objectiveAgrees(double cpScore,int exactScore,boolean optimal){
        long reported=Math.round(cpScore);
        return optimal?reported==exactScore:reported>=exactScore;
    }
    static boolean any(boolean[] b){for(boolean v:b)if(v)return true;return false;}
    /** A chronological circuit identifies consecutive classes, including the first class.
     * Each day's dummy node closes the path; absent classes use self loops. */
    void addScheduleObjective(CpModel cp,ProblemData m,BoolVar[][] take,IntVar[] start){
        var objective=LinearExpr.newBuilder();var audiences=new TreeSet<String>();
        m.events.forEach(e->audiences.addAll(m.audience(e)));
        int bound=m.days.size()*2880;
        var restStart=new IntVar[m.events.size()];var restFinish=new IntVar[m.events.size()];
        for(int i=0;i<m.events.size();i++){
            var begins=LinearExpr.newBuilder();var ends=LinearExpr.newBuilder();
            for(int k=0;k<take[i].length;k++){
                var p=m.choices.get(i).get(k);int offset=m.days.indexOf(p.day())*2880;
                begins.addTerm(take[i][k],offset+m.restMinute(p.day(),p.start()));
                ends.addTerm(take[i][k],offset+m.restMinute(p.day(),p.start()+m.events.get(i).span()));
            }
            restStart[i]=cp.newIntVar(0,bound,"startWithoutRest");restFinish[i]=cp.newIntVar(0,bound,"finishWithoutRest");
            cp.addEquality(restStart[i],begins);cp.addEquality(restFinish[i],ends);
        }
        for(String audience:audiences)for(int d=0;d<m.days.size();d++){
            String day=m.days.get(d);var members=new ArrayList<Integer>();
            for(int i=0;i<m.events.size();i++)if(m.audience(m.events.get(i)).contains(audience)&&m.choices.get(i).stream().anyMatch(p->p.day().equals(day)))members.add(i);
            if(members.isEmpty())continue;
            var circuit=cp.addCircuit();var count=LinearExpr.newBuilder();
            var empty=cp.newBoolVar("emptyDay");circuit.addArc(0,0,empty);
            for(int a=0;a<members.size();a++){
                int i=members.get(a);var onDay=LinearExpr.newBuilder();
                for(int k=0;k<take[i].length;k++)if(m.choices.get(i).get(k).day().equals(day))onDay.add(take[i][k]);
                var present=cp.newBoolVar("present");cp.addEquality(present,onDay);count.add(present);
                circuit.addArc(a+1,a+1,present.not());
                var first=cp.newBoolVar("firstClass");circuit.addArc(0,a+1,first);
                circuit.addArc(a+1,0,cp.newBoolVar("lastClass"));
                if(ScheduleObjective.goal(m)==ScheduleObjective.Goal.EARLIER_FINISH)
                    selectedPenalty(cp,objective,LinearExpr.newBuilder().add(start[i]).add(-d*2880-ScheduleObjective.OPEN),first,bound);
                for(int b=0;b<members.size();b++)if(a!=b){
                    int j=members.get(b);var next=cp.newBoolVar("nextClass");circuit.addArc(a+1,b+1,next);
                    cp.addGreaterOrEqual(start[j],offset(start[i],m.events.get(i).span())).onlyEnforceIf(next);
                    selectedPenalty(cp,objective,LinearExpr.newBuilder().add(restStart[j]).addTerm(restFinish[i],-1),next,bound);
                }
            }
            cp.addEquality(count,0).onlyEnforceIf(empty);
            cp.addGreaterOrEqual(count,1).onlyEnforceIf(empty.not());
        }
        cp.minimize(objective);
    }
    void selectedPenalty(CpModel cp,LinearExprBuilder objective,LinearArgument difference,BoolVar selected,int bound){
        var raw=cp.newIntVar(0,bound,"nonnegativeMinutes");
        cp.addMaxEquality(raw,new LinearArgument[]{cp.newConstant(0),difference});
        var paid=cp.newIntVar(0,bound,"selectedMinutes");
        cp.addEquality(paid,raw).onlyEnforceIf(selected);cp.addEquality(paid,0).onlyEnforceIf(selected.not());
        objective.add(paid);
    }
    void addRules(CpModel cp,ProblemData m,IntVar[] start,IntVar[] day){
        for(var r:m.rules)for(int i=0;i<m.events.size();i++)for(int j=0;j<m.events.size();j++){
            if(i==j)continue;var a=m.events.get(i);var b=m.events.get(j);
            String second=r[1].equals("once daily")?r[2]:r[3];
            if(!a.code().equals(r[2])||!b.code().equals(second)||!r[4].isBlank()&&!(a.groups().contains(r[4])&&b.groups().contains(r[4])))continue;
            switch(r[1]){
                case "different days","once daily"->cp.addDifferent(day[i],day[j]);
                case "no overlap"->cp.addNoOverlap(new IntervalVar[]{cp.newFixedSizeIntervalVar(start[i],a.span(),"ruleA"),cp.newFixedSizeIntervalVar(start[j],b.span(),"ruleB")});
                case "after"->cp.addGreaterOrEqual(start[j],offset(start[i],a.span()+a.rest()));
                case "consecutive"->{cp.addEquality(day[i],day[j]);cp.addEquality(start[j],offset(start[i],a.span()+a.rest()));}
            }
        }
    }
    LinearExprBuilder sumFor(ProblemData m,BoolVar[][] take,String teacher,String day){
        var sum=LinearExpr.newBuilder();for(int i=0;i<m.events.size();i++)if(m.events.get(i).teacher().equals(teacher))
            for(int k=0;k<take[i].length;k++)if(day==null||m.choices.get(i).get(k).day().equals(day))sum.addTerm(take[i][k],Objectives.teaching(m.events.get(i)));
        return sum;
    }
    void workload(CpModel cp,ProblemData m,BoolVar[][] take){
        for(var t:m.data.rows.get("Workloads")){
            if(!t[2].isBlank())cp.addLessOrEqual(sumFor(m,take,t[0],null),Data.positive(t[2]));
            if(!t[1].isBlank())for(String day:m.days)cp.addLessOrEqual(sumFor(m,take,t[0],day),Data.positive(t[1]));
        }
    }
    void addObjective(CpModel cp,ProblemData m,BoolVar[][] take){
        int[] w=Objectives.weights(m);var objective=LinearExpr.newBuilder();Set<String> groups=new TreeSet<>(),teachers=new TreeSet<>();
        m.events.forEach(e->{groups.addAll(m.audience(e));teachers.add(e.teacher());});
        for(String group:groups)scopes(cp,m,take,objective,group,false,w);
        if(w[1]>0)for(String teacher:teachers)scopes(cp,m,take,objective,teacher,true,w);
        var rooms=new TreeMap<String,Integer>();for(var room:m.data.rows.get("Rooms"))rooms.put(room[0],Data.positive(room[2]));
        var prefs=Objectives.preferences(m);
        Map<String,Map<String,List<BoolVar>>> subjectRooms=new TreeMap<>();
        for(int i=0;i<m.events.size();i++){var e=m.events.get(i);
            for(int k=0;k<take[i].length;k++){
                var p=m.choices.get(i).get(k);long penalty=w[3]*(Math.max(0,Data.minute(prefs[0])-p.start())+Math.max(0,p.start()+e.span()-Data.minute(prefs[1])));
                penalty+=(long)w[4]*Math.max(0,Math.min(p.start()+e.span(),Data.minute(prefs[3]))-Math.max(p.start(),Data.minute(prefs[2])));
                penalty+=(long)w[7]*(rooms.get(p.room())-e.seats());
                for(var t:m.data.rows.get("Workloads"))if(t[0].equals(e.teacher())&&!t[3].isBlank()&&!Data.names(t[3]).contains(p.day()))penalty+=w[8];
                objective.addTerm(take[i][k],penalty);
                if(w[6]>0)subjectRooms.computeIfAbsent(e.code(),c->new TreeMap<>()).computeIfAbsent(p.room(),r->new ArrayList<>()).add(take[i][k]);
            }
        }
        for(var roomChoices:subjectRooms.values()){
            objective.add(-w[6]);for(var choices:roomChoices.values()){
                var used=cp.newBoolVar("usedRoom");var sum=LinearExpr.sum(choices.toArray(BoolVar[]::new));
                cp.addGreaterOrEqual(sum,1).onlyEnforceIf(used);cp.addEquality(sum,0).onlyEnforceIf(used.not());objective.addTerm(used,w[6]);
            }
        }
        cp.minimize(objective);
    }
    void scopes(CpModel cp,ProblemData m,BoolVar[][] take,LinearExprBuilder objective,String id,boolean teacher,int[] w){
        int total=0;for(var e:m.events)if(teacher?e.teacher().equals(id):m.audience(e).contains(id))total+=Objectives.teaching(e);
        for(String day:m.days){
            var count=LinearExpr.newBuilder();var load=LinearExpr.newBuilder();var occupied=LinearExpr.newBuilder();
            int weight=teacher?w[1]:w[0];var firstChoices=new ArrayList<IntVar>();var lastChoices=new ArrayList<IntVar>();
            for(int i=0;i<m.events.size();i++){
                var e=m.events.get(i);if(!(teacher?e.teacher().equals(id):m.audience(e).contains(id)))continue;
                var onDay=LinearExpr.newBuilder();var begins=LinearExpr.newBuilder();var ends=LinearExpr.newBuilder();
                for(int k=0;k<take[i].length;k++){
                    var p=m.choices.get(i).get(k);if(!p.day().equals(day))continue;
                    int begin=m.activeMinute(day,p.start()),end=m.activeMinute(day,p.start()+e.span()+(teacher?0:e.rest()));
                    occupied.addTerm(take[i][k],end-begin);count.add(take[i][k]);load.addTerm(take[i][k],Objectives.teaching(e));
                    onDay.add(take[i][k]);begins.addTerm(take[i][k],begin);ends.addTerm(take[i][k],end);
                }
                if(weight>0){
                    var present=cp.newBoolVar("onDay");cp.addEquality(present,onDay);
                    var begin=cp.newIntVar(0,2880,"beginOrSentinel");cp.addEquality(begin,begins.add(2880).addTerm(present,-2880));firstChoices.add(begin);
                    var end=cp.newIntVar(0,2880,"endOrZero");cp.addEquality(end,ends);lastChoices.add(end);
                }
            }
            if(weight>0&&!firstChoices.isEmpty()){
                var rawFirst=cp.newIntVar(0,2880,"rawFirst");var first=cp.newIntVar(0,2880,"first");var last=cp.newIntVar(0,2880,"last");
                cp.addMinEquality(rawFirst,firstChoices);cp.addMaxEquality(last,lastChoices);
                var any=cp.newBoolVar("any");cp.addGreaterOrEqual(count,1).onlyEnforceIf(any);cp.addEquality(count,0).onlyEnforceIf(any.not());
                cp.addEquality(first,rawFirst).onlyEnforceIf(any);cp.addEquality(first,0).onlyEnforceIf(any.not());
                objective.addTerm(last,weight).addTerm(first,-weight).addTerm(occupied,-weight);
            }
            if(!teacher&&w[5]>0){var single=cp.newBoolVar("single");cp.addEquality(count,1).onlyEnforceIf(single);cp.addDifferent(count,1).onlyEnforceIf(single.not());objective.addTerm(single,w[5]);}
            if(!teacher&&w[2]>0){
                var difference=LinearExpr.newBuilder().addTerm(load,m.days.size()).add(-total);
                var abs=cp.newIntVar(0,(long)total*m.days.size(),"balance");cp.addAbsEquality(abs,difference);objective.addTerm(abs,w[2]);
            }
        }
    }
}
