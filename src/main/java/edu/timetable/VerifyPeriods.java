package edu.timetable;
import java.nio.file.*;
import java.util.*;
public class VerifyPeriods {
    static int count;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception{
        var d=Data.load(Path.of("examples/periods-demo.week"));var m=new ProblemData(d);
        check(m.choices.getFirst().size()==4,"90-minute class fits two periods on each of two days");
        var row=d.rows.get("Subjects").getFirst();row[8]="30";m=new ProblemData(d);
        check(m.choices.getFirst().stream().anyMatch(p->p.start()==660),"Exact 11:00–11:30 period preserved");
        row[8]="120";check(new ProblemData(d).choices.getFirst().isEmpty(),"Too-long meeting has no placements");
        row[8]="90";m=new ProblemData(d);
        int[] genes={0,1};check(Evaluator.evaluate(m,genes).minutes()==60,"10:30–13:00 interval excludes rest and lunch, leaves 60");
        row[9]="30";m=new ProblemData(d);check(Evaluator.evaluate(m,genes).minutes()==60,"Required rest overlapping marked break counted once");
        var portfolio=new Portfolio();portfolio.put(d);
        var spring=Data.parse(d.text());spring.rows.get("Campus").getFirst()[2]="Spring 2026–2027";spring.rows.get("Subjects").getFirst()[1]="Spring subject";portfolio.put(spring);
        var other=Data.parse(d.text());other.rows.get("Campus").getFirst()[1]="Civil Engineering";other.rows.get("Stages").clear();
        for(int i=1;i<=5;i++)other.rows.get("Stages").add(new String[]{"Stage "+i});portfolio.put(other);
        var file=Files.createTempFile("verify-portfolio",".planner");portfolio.save(file);var read=Portfolio.load(file);Files.delete(file);
        check(read.entries.size()==3,"Two semesters and a separate five-stage department survive reopening");
        check(read.entries.get(Portfolio.key(d)).rows.get("Subjects").getFirst()[1].equals("AI"),"Fall inputs unchanged after Spring edit");
        check(new ProblemData(read.entries.get(Portfolio.key(other))).stages.containsValue("Stage 4"),"Five-stage department preserves groups");
        var bad=Data.parse(d.text());bad.rows.get("Periods").add(new String[]{"Sunday","Overlap","10:00","11:00","Teaching"});
        try{new ProblemData(bad);throw new AssertionError("Accepted overlap");}catch(IllegalArgumentException expected){check(true,"Overlapping periods rejected");}
        for(String name:List.of("tiny","department","impossible")){new ProblemData(Data.load(Path.of("examples/"+name+".week")));check(true,"Legacy "+name+" dataset loads");}
        if(List.of(args).contains("full")){
            for(String method:List.of("CP","Hybrid")){
                var run=new SearchEngine(m,new SearchEngine.Settings(7,8,12,50,.15,10000),()->false);run.hybridStopping=new HybridStopping(HybridStopping.Mode.STAGNANT_GENERATIONS,2,15);if(method.equals("CP"))run.solveConstraints(new ConstraintSearch());else run.run(method,new ConstraintSearch());
                check(run.best!=null&&Evaluator.evaluate(m,run.best).valid(),method+" feasible with custom periods");
                check(run.score==0,method+" achieves zero gap");
            }
            // Fix both classes on Sunday so a positive objective must match CP's compressed clock.
            for(var t:d.rows.get("Teachers"))t[2]="Sunday@09:00-15:00";
            m=new ProblemData(d);var cp=new SearchEngine(m,new SearchEngine.Settings(7,8,8,10,.1,1000),()->false);cp.solveConstraints(new ConstraintSearch());
            var exhaustive=new SearchEngine(m,new SearchEngine.Settings(7,8,8,10,.1,1000),()->false);exhaustive.run("Brute-force",null);
            check(cp.proved&&cp.score==60&&cp.score==exhaustive.score,"CP and exhaustive agree on positive gap of 60");
        }
        System.out.println(count+" checks passed.");
    }
}
