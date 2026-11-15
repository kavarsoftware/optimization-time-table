package edu.timetable;
import java.nio.file.*;
import java.util.*;
public class Verify {
    static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);System.out.println("PASS "+label);}
    static SearchEngine run(ProblemData model,String method,int seed,long budget,SearchEngine.ConstraintSolver cp){
        SearchEngine s=new SearchEngine(model,new SearchEngine.Settings(seed,2,12,40,.12,budget),()->false);s.hybridStopping=new HybridStopping(HybridStopping.Mode.STAGNANT_GENERATIONS,2,15);if(method.equals("CP"))s.solveConstraints(cp);else s.run(method,cp);return s;
    }
    public static void main(String[] args)throws Exception{
        Data d=Data.load(Path.of("examples/tiny.week"));ProblemData m=new ProblemData(d);
        check(m.choices.get(0).size()==1&&m.choices.get(1).size()==3,"Exact capacity and teacher windows");
        check(Evaluator.evaluate(m,new int[]{0,1}).minutes()==30,"Noon start gives 30 avoidable minutes");
        check(Evaluator.evaluate(m,new int[]{0,0}).minutes()==0,"11:30 starts after required rest");
        var result=run(m,"Brute-force",7,1000,null);check(result.proved&&result.evaluations==3&&result.score==0,"Tiny optimum and complete enumeration");
        d.rows.get("Rules").getFirst()[0]="yes";m=new ProblemData(d);
        result=run(m,"Brute-force",7,1000,null);check(result.best[1]==2&&m.choices.get(1).get(2).day().equals("Monday"),"Different-day demo forces Monday");
        Path file=Files.createTempFile("verify",".week");d.save(file);check(new ProblemData(Data.load(file)).rules.size()==1,"Edited dataset round trip");Files.delete(file);
        check(run(new ProblemData(Data.load(Path.of("examples/impossible.week"))),"Random",7,1000,null).status.startsWith("INFEASIBLE"),"Insufficient seats");
        var t=new ProblemData.Time("Sunday",540,"A");var u=new ProblemData.Time("Sunday",570,"B");
        check(m.conflict(0,t,1,u)!=null,"Different start times still overlap");
        var a=d.rows.get("Subjects").getFirst();var b=d.rows.get("Subjects").getLast();
        a[4]=b[4]="student1";d.rows.get("Groups").add(new String[]{"4B","Stage 4"});b[3]="4B";d.rows.get("Rules").clear();
        check(new ProblemData(d).conflict(0,t,1,u)!=null,"Shared enrollment across groups");a[4]=b[4]="";b[2]=a[2];
        check(new ProblemData(d).conflict(0,t,1,u)!=null,"Shared teacher across groups");
        a[10]="split";a[11]="no";var e=new ProblemData(d).events.getFirst();check(e.span()==150&&e.roomIntervals(540).getLast()[0]==630,"Split teaching releases room at break");
        a[10]="inside";check(new ProblemData(d).events.getFirst().span()==120,"Inside break fits block");
        d.rows.get("Days").getFirst()[2]="11:00";a[10]="after";check(new ProblemData(d).choices.getFirst().size()==1,"End exactly at closing");
        var trace=new ProblemData(Data.load(Path.of("examples/trace.week")));
        check(Evaluator.evaluate(trace,new int[]{0,5}).minutes()==0,"Trace parent A");
        check(Evaluator.evaluate(trace,new int[]{6,0}).minutes()==30,"Trace parent B");
        check(!Evaluator.evaluate(trace,new int[]{0,0}).valid(),"Trace child clashes");
        if(List.of(args).contains("full")){
            var solver=(SearchEngine.ConstraintSolver)Class.forName("edu.timetable.ConstraintSearch").getDeclaredConstructor().newInstance();
            for(String method:List.of("CP","Hybrid")){
                var tested=run(trace,method,7,1000,solver);
                check(tested.best!=null&&Evaluator.evaluate(trace,tested.best).valid(),method+" returned a valid timetable");
                if(method.equals("CP"))check(tested.score==0&&tested.proved,"CP proves tiny objective");
            }
            var repair=new SearchEngine(trace,new SearchEngine.Settings(7,5,8,10,.1,1000),()->false);
            int[] repaired=solver.solve(repair,new int[]{0,0},false,4);
            check(repaired!=null&&Evaluator.evaluate(trace,repaired).valid(),"Native repair of overlapping child");
        }
        var large=new ProblemData(Data.load(Path.of("examples/department.week")));check(large.events.size()==15&&large.stages.size()==10,"Five-stage dataset and lab sections");
    }
}
