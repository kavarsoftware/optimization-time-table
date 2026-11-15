package edu.timetable;
import java.nio.file.*;
import java.io.*;
import java.util.*;
/** Reproducible multi-seed experiments. Budgets include solver construction, not input parsing. */
public final class ExperimentRunner {
    static Data realistic(){
        Data d=new Data();
        d.rows.get("Campus").add(new String[]{"Demonstration University","Software Engineering","Fall 2026","30","60","0","after","yes","groups"});
        d.rows.get("Institution").add(new String[]{"Engineering","2","1","300"});
        d.rows.get("Weights").add(new String[]{"1","0","0","0","0","60","0","0","0"});
        for(String day:List.of("Sunday","Monday","Tuesday","Wednesday","Thursday")){
            d.rows.get("Days").add(new String[]{day,"08:00","15:00"});
            d.rows.get("Periods").add(new String[]{day,"Morning","08:00","12:00","Teaching"});
            d.rows.get("Periods").add(new String[]{day,"Lunch","12:00","13:00","Required break"});
            d.rows.get("Periods").add(new String[]{day,"Afternoon","13:00","15:00","Teaching"});
        }
        for(int t=0;t<15;t++)d.rows.get("Teachers").add(new String[]{"Teacher"+t,"Dr.","*"});
        for(int r=0;r<25;r++)d.rows.get("Rooms").add(new String[]{"R"+r,r<15?"Lecture":"Lab",r%2==0?"30":"40","*"});
        for(int stage=1;stage<=5;stage++){
            d.rows.get("Stages").add(new String[]{"Stage "+stage});
            for(int group=0;group<2;group++){
                String g=stage+(group==0?"A":"B");d.rows.get("Groups").add(new String[]{g,"Stage "+stage});
                d.rows.get("GroupCounts").add(new String[]{g,"30"});
                for(int subject=0;subject<8;subject++)d.rows.get("Subjects").add(new String[]{g+"-"+subject,"Subject "+subject,"Teacher"+((stage*2+group+subject)%15),g,"","30",subject<6?"Lecture":"Lab","1","60","0","after","yes"});
            }
        }return d;
    }
    public static void main(String[] args)throws Exception{
        int seeds=args.length>0?Integer.parseInt(args[0]):30;double budget=args.length>1?Double.parseDouble(args[1]):2;
        Path out=Path.of("results");Files.createDirectories(out);realistic().save(Path.of("examples/realistic.json"));
        String hardware=System.getProperty("os.name")+" "+System.getProperty("os.arch")+" / JVM "+System.getProperty("java.version")+" / "+Runtime.getRuntime().availableProcessors()+" logical CPUs";
        try(var runs=new PrintWriter(Files.newBufferedWriter(out.resolve("experiments.csv")));var trace=new PrintWriter(Files.newBufferedWriter(out.resolve("convergence.csv")))){
            runs.println("instance,method,version,seed,budget_seconds,population,generations,mutation,workers,hardware,events,placements,status,feasible,objective,runtime_seconds,evaluations");
            trace.println("instance,method,seed,evaluations,objective,seconds");
            for(String name:List.of("tiny","impossible","realistic")){
                ProblemData m=new ProblemData(name.equals("realistic")?realistic():Data.load(Path.of("examples/"+name+".week")));
                long placements=m.choices.stream().mapToLong(List::size).sum();
                List<String> methods=name.equals("tiny")?List.of("Brute-force","Random","Hybrid"):List.of("Random","Hybrid");
                for(String method:methods)for(int seed=1;seed<=seeds;seed++){
                    SearchEngine s=new SearchEngine(m,new SearchEngine.Settings(seed,budget,16,100,.12,Long.MAX_VALUE),()->false);
                    s.hybridStopping=new HybridStopping(HybridStopping.Mode.TIME_LIMIT,1000,15);s.run(method,new ConstraintSearch());
                    runs.printf(Locale.ROOT,"%s,%s,UniPlan5-1.0,%d,%.3f,16,100,0.12,1,%s,%d,%d,%s,%b,%s,%.6f,%d%n",name,method,seed,budget,hardware,m.events.size(),placements,s.status.replace(',',';'),s.best!=null,s.best==null?"":s.score,s.elapsed(),s.evaluations);runs.flush();
                    for(double[] p:s.trace)trace.printf(Locale.ROOT,"%s,%s,%d,%.0f,%.0f,%.6f%n",name,method,seed,p[0],p[1],p[2]);trace.flush();
                }
                System.out.println("Completed "+name+" ("+m.events.size()+" sessions, "+placements+" placements)");
            }
        }
    }
}
