package edu.timetable;
import java.nio.file.*;
import java.util.*;
public class Bench {
    public static void main(String[] args)throws Exception{
        boolean full=List.of(args).contains("full");
        SearchEngine.ConstraintSolver cp=full?(SearchEngine.ConstraintSolver)Class.forName("edu.timetable.ConstraintSearch").getDeclaredConstructor().newInstance():null;
        System.out.println("dataset,method,seed,seconds_budget,sample_limit,evaluations,feasible,score,runtime_ms");
        for(String name:List.of("tiny","department")){
            var m=new ProblemData(Data.load(Path.of("examples/"+name+".week")));
            var methods=new ArrayList<>(List.of("Random"));if(name.equals("tiny"))methods.add("Brute-force");if(full)methods.add("Hybrid");
            for(String method:methods)for(long cap:method.equals("Random")?new long[]{1,100,Long.MAX_VALUE}:new long[]{Long.MAX_VALUE}){
                for(int seed=1;seed<=5;seed++){
                    double budget=full?5:.12;
                    var s=new SearchEngine(m,new SearchEngine.Settings(seed,budget,16,80,.12,cap),()->false);
                    s.hybridStopping=new HybridStopping(HybridStopping.Mode.TIME_LIMIT,1000,15);s.run(method,cp);
                    System.out.printf(Locale.ROOT,"%s,%s,%d,%.2f,%d,%d,%b,%s,%.3f%n",name,method,seed,budget,cap,s.evaluations,s.best!=null,s.best==null?"":s.score,s.elapsed()*1000);
                }
            }
        }
    }
}
