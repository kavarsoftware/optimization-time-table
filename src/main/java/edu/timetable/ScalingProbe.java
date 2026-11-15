package edu.timetable;
import java.nio.file.*;
import java.util.*;
/** One longer-budget probe, explicitly separate from the equal-budget 30-seed experiment. */
public final class ScalingProbe {
    public static void main(String[] args)throws Exception{
        double seconds=args.length==0?30:Double.parseDouble(args[0]);
        var d=ExperimentRunner.realistic();var m=new ProblemData(d);
        var run=new SearchEngine(m,new SearchEngine.Settings(7,seconds,16,100,.12,Long.MAX_VALUE),()->false);
        run.hybridStopping=new HybridStopping(HybridStopping.Mode.TIME_LIMIT,1000,15);run.run("Hybrid",new ConstraintSearch());
        String report=String.format(Locale.ROOT,"method=Hybrid%nseed=7%nbudget=%.1f%nstatus=%s%nfeasible=%s%nobjective=%s%nruntime=%.6f%nevaluations=%d%n",seconds,run.status,run.best!=null,run.best==null?"NA":run.score,run.elapsed(),run.evaluations);
        Files.writeString(Path.of("results/scaling-probe.txt"),report);System.out.print(report);
        if(run.best!=null)SheetExport.save(d,run,Path.of("results/realistic-timetable.html"));
    }
}
