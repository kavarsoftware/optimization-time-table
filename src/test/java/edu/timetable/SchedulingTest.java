package edu.timetable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingTest {
    @TempDir Path temp;
    SearchEngine solve(Data d,String method,int seed){
        SearchEngine s=new SearchEngine(new ProblemData(d),new SearchEngine.Settings(seed,8,12,40,.2,10000),()->false);
        s.hybridStopping=new HybridStopping(HybridStopping.Mode.STAGNANT_GENERATIONS,2,15);
        if(method.equals("CP"))s.solveConstraints(new ConstraintSearch());else s.run(method,new ConstraintSearch());return s;
    }
    @Test void tinyAndImpossible()throws Exception{
        var tiny=Data.load(Path.of("examples/tiny.week"));
        assertEquals(0,solve(tiny,"Brute-force",7).score);
        assertEquals(0,solve(tiny,"CP",7).score);
        assertNull(solve(Data.load(Path.of("examples/impossible.week")),"CP",7).best);
    }
    @Test void jsonUnicodeAndLegacy()throws Exception{
        var d=Data.load(Path.of("examples/tiny.week"));d.rows.get("Campus").getFirst()[0]="زانکۆ | جامعة\nUniversity";
        var file=temp.resolve("dataset.json");d.save(file);
        assertEquals(d.rows.get("Campus").getFirst()[0],Data.load(file).rows.get("Campus").getFirst()[0]);
        var p=new Portfolio();p.put(d);p.save(temp.resolve("project.json"));
        assertEquals(1,Portfolio.load(temp.resolve("project.json")).entries.size());
    }
    @Test void weightedCpMatchesExhaustive()throws Exception{
        for(int seed=1;seed<=12;seed++){
            var d=Data.load(Path.of("examples/trace.week"));var r=new Random(seed);
            String[] weights=new String[9];for(int i=0;i<weights.length;i++)weights[i]=""+r.nextInt(4);d.rows.get("Weights").add(weights);
            d.rows.get("Workloads").add(new String[]{"Narin","300","600","Monday"});
            d.rows.get("Workloads").add(new String[]{"Rina","300","600","Monday"});
            var exact=solve(d,"Brute-force",seed);var cp=solve(d,"CP",seed);
            assertNotNull(cp.best);assertEquals(exact.score,cp.score,"seed "+seed);assertTrue(Evaluator.evaluate(cp.m,cp.best).valid());
        }
    }
    @Test void periodsAndDates()throws Exception{
        var d=Data.load(Path.of("examples/periods-demo.week"));
        d.rows.get("Term").add(new String[]{"2026-09-01","2026-09-30","2026-09-06","2026-09-13/2026-09-19","no"});
        assertFalse(TermCalendar.dates(d).contains(java.time.LocalDate.of(2026,9,6)));
        assertTrue(TermCalendar.weeks(d)>0);
        var m=new ProblemData(d);assertEquals(4,m.choices.getFirst().size());
        assertEquals(List.of(60,60),WeeklyTime.lengths(120,2,"60,120"));
        assertThrows(IllegalArgumentException.class,()->WeeklyTime.lengths(100,2,"60,120"));
        assertEquals(solve(d,"Brute-force",7).score,solve(d,"CP",7).score);
    }
    @Test void locksWorkloadAndLns()throws Exception{
        var d=Data.load(Path.of("examples/trace.week"));d.rows.get("Locks").add(new String[]{"0","pin","Sunday","09:00","R101"});
        var cp=solve(d,"CP",7);assertEquals(540,cp.m.choices.getFirst().get(cp.best[0]).start());
        var lns=solve(d,"Hybrid",7);assertNotNull(lns.best);assertTrue(Evaluator.evaluate(lns.m,lns.best).valid());
        d.rows.get("Workloads").add(new String[]{"Narin","60","60",""});assertNull(solve(d,"CP",7).best);
    }
    @Test void pinnedRandomSchedulesAgreeWithCp()throws Exception{
        var base=Data.load(Path.of("examples/trace.week"));var model=new ProblemData(base);var random=new Random(2026);
        for(int sample=0;sample<30;sample++){
            int[] genes=new int[model.events.size()];var pinned=Data.parse(base.text());
            for(int i=0;i<genes.length;i++){
                genes[i]=random.nextInt(model.choices.get(i).size());var place=model.choices.get(i).get(genes[i]);
                pinned.rows.get("Locks").add(new String[]{""+i,"pin",place.day(),Data.clock(place.start()),place.room()});
            }
            var expected=Evaluator.evaluate(model,genes);var cp=solve(pinned,"CP",sample);
            assertEquals(expected.valid(),cp.best!=null,"sample "+sample);
            if(expected.valid())assertEquals(expected.minutes(),cp.score);
        }
    }
    @Test void adjacentWindowsAndIndependentParts()throws Exception{
        var d=Data.load(Path.of("examples/trace.week"));d.rows.get("Periods").clear();
        for(String day:List.of("Sunday","Monday")){
            d.rows.get("Periods").add(new String[]{day,"a","09:00","10:00","Teaching"});
            d.rows.get("Periods").add(new String[]{day,"b","10:00","11:00","Teaching"});
        }
        d.rows.get("Term").add(new String[]{"2026-09-01","2026-10-01","","","no"});
        assertTrue(new ProblemData(d).choices.getFirst().isEmpty());
        d.rows.get("Term").getFirst()[4]="yes";
        assertFalse(new ProblemData(d).choices.getFirst().isEmpty());
        d.rows.get("Parts").add(new String[]{"AI","4A","Lecture","60","60","1"});
        var lab=d.rows.get("Subjects").getFirst().clone();lab[6]="Lab";lab[8]="90";
        d.rows.get("Subjects").add(lab);d.rows.get("Rooms").add(new String[]{"Lab","Lab","30","*"});
        var m=new ProblemData(d);assertEquals(60,m.events.getFirst().duration());assertEquals(90,m.events.getLast().duration());
        d.rows.get("Locks").add(new String[]{"100","pin","Sunday","09:00","R101"});
        assertThrows(IllegalArgumentException.class,()->new ProblemData(d));
    }
    @Test void datedCalendarExportExcludesHolidays()throws Exception{
        var d=Data.load(Path.of("examples/tiny.week"));
        d.rows.get("Term").add(new String[]{"2026-09-01","2026-09-30","2026-09-06","","no"});
        var run=solve(d,"CP",7);var path=temp.resolve("term.ics");CalendarExport.save(d,run,path);
        var content=Files.readString(path);assertTrue(content.contains("BEGIN:VEVENT"));assertFalse(content.contains("DTSTART:20260906"));
        String longText="SUMMARY:"+"زانکۆ".repeat(50);
        for(String line:CalendarExport.folded(longText).split("\r\n"))assertTrue(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=75);
    }
}
