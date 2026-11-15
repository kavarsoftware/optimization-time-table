package edu.timetable;
import java.nio.file.*;
public class VerifyAvailability {
    public static void main(String[] args)throws Exception{
        Data d=Data.load(Path.of("examples/daily-windows.week"));AvailabilityEditor.validate(d);
        ProblemData m=new ProblemData(d);
        if(m.choices.getFirst().stream().noneMatch(t->t.day().equals("Sunday")&&t.start()==480))throw new AssertionError("08:00 lost");
        if(m.choices.getFirst().stream().anyMatch(t->t.day().equals("Monday")&&t.start()==480))throw new AssertionError("Sunday time leaked to Monday");
        d.rows.get("Subjects").getFirst()[5]="31";
        m=new ProblemData(d);if(!m.choices.getFirst().isEmpty()||!m.diagnostics.getFirst().contains("31 students"))throw new AssertionError("Capacity");
        d.rows.get("Subjects").getFirst()[5]="30";d.rows.get("Subjects").getFirst()[8]="150";
        m=new ProblemData(d);if(!m.choices.getFirst().isEmpty()||!m.diagnostics.getFirst().contains("150 minutes"))throw new AssertionError("Duration");
        d.rows.get("Subjects").getFirst()[8]="90";
        d.rows.get("Periods").getFirst()[2]="07:00";AvailabilityEditor.bounds(d,"Sunday");AvailabilityEditor.validate(d);
        if(!d.rows.get("Days").getFirst()[1].equals("07:00"))throw new AssertionError("Bounds not updated");
        var file=Files.createTempFile("windows",".week");d.save(file);
        if(!Data.load(file).rows.get("Periods").getFirst()[2].equals("07:00"))throw new AssertionError("Save lost edit");Files.delete(file);
        var run=new SearchEngine(new ProblemData(d),new SearchEngine.Settings(7,5,12,30,.12,1000),()->false);
        run.solveConstraints(new ConstraintSearch());if(run.best==null||!Evaluator.evaluate(run.m,run.best).valid())throw new AssertionError("Invalid output");
        System.out.println("PASS distinct Sunday and Monday windows, capacity diagnosis, duration diagnosis, changed bounds, save/reopen, CP schedule");
    }
}
