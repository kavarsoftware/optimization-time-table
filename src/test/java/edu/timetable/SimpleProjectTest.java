package edu.timetable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SimpleProjectTest {
    @TempDir Path temp;
    @Test void nativePresolveIncumbentIsCheckedAgainstExactObjective(){
        NativeSolver.load();var model=new com.google.ortools.sat.CpModel();
        var x=model.newIntVar(0,10,"x");var y=model.newIntVar(0,10,"y");var top=model.newIntVar(0,30,"top");
        model.addMaxEquality(top,new com.google.ortools.sat.LinearArgument[]{x,com.google.ortools.sat.LinearExpr.term(y,2)});
        model.addLessOrEqual(com.google.ortools.sat.LinearExpr.sum(new com.google.ortools.sat.IntVar[]{x,y}),10);
        model.minimize(com.google.ortools.sat.LinearExpr.weightedSum(new com.google.ortools.sat.IntVar[]{top,x,y},new long[]{1,-1,-2}));
        model.addDecisionStrategy(new com.google.ortools.sat.IntVar[]{top,x,y},com.google.ortools.sat.DecisionStrategyProto.VariableSelectionStrategy.CHOOSE_FIRST,com.google.ortools.sat.DecisionStrategyProto.DomainReductionStrategy.SELECT_MAX_VALUE);
        var solver=new com.google.ortools.sat.CpSolver();solver.getParameters().setNumSearchWorkers(1).setStopAfterFirstSolution(true).setSearchBranching(com.google.ortools.sat.SatParameters.SearchBranching.FIXED_SEARCH);
        assertEquals(com.google.ortools.sat.CpSolverStatus.FEASIBLE,solver.solve(model));
        int exact=(int)(solver.value(top)-solver.value(x)-2*solver.value(y));
        assertTrue(ConstraintSearch.objectiveAgrees(solver.objectiveValue(),exact,false));
    }
    @Test void intermediateObjectiveCanBeLooseButOptimalMustBeExact(){
        assertTrue(ConstraintSearch.objectiveAgrees(1621.0,1620,false));
        assertTrue(ConstraintSearch.objectiveAgrees(1620.0,1620,true));
        assertFalse(ConstraintSearch.objectiveAgrees(1619.0,1620,false));
        assertFalse(ConstraintSearch.objectiveAgrees(1621.0,1620,true));
    }
    @Test void largeDocumentPersistsAllRecordsWithoutTruncation()throws Exception{
        var p=SimpleProject.demo();var first=p.subjects.getFirst();
        p.subjects.clear();
        for(int i=0;i<2500;i++)p.subjects.add(new SimpleProject.Subject("saved-"+i,first.stage(),first.group(),"Subject "+i,"Theory",2000,1,90,""));
        p.rooms=new ArrayList<>(List.of(new SimpleProject.Room("Large hall","Classroom",5000)));
        assertTrue(p.health().stream().noneMatch(i->i.message().contains("enrollment")||i.message().contains("capacity 1")));
        var path=temp.resolve("large.json");p.save(path);var copy=SimpleProject.load(path);
        assertEquals(2500,copy.subjects.size());assertEquals("saved-2499",copy.subjects.getLast().id());assertEquals(5000,copy.rooms.getFirst().capacity());
    }
    @Test void mutationChangesEligibleGenesAndRespectsSeed(){
        var m=new ProblemData(SimpleProject.demo().toData());
        var settings=new SearchEngine.Settings(7,5,8,20,1,1000);
        var a=new SearchEngine(m,settings,()->false);var b=new SearchEngine(m,settings,()->false);
        int[] x=new int[m.events.size()],y=x.clone();a.mutate(x);b.mutate(y);
        assertArrayEquals(x,y);assertEquals(x.length,a.mutations);
        for(int i=0;i<x.length;i++){assertNotEquals(0,x[i]);assertTrue(x[i]<m.choices.get(i).size());}
        var no=new SearchEngine(m,new SearchEngine.Settings(7,5,8,20,0,1000),()->false);int[] unchanged=new int[x.length];no.mutate(unchanged);assertArrayEquals(new int[x.length],unchanged);
    }
    @Test void demoHasFiveSubjectsIncludingTwoLabsPerGroup(){
        var p=SimpleProject.demo();assertEquals(40,p.subjects.size());
        for(var g:p.groups){var subjects=p.subjects.stream().filter(s->s.stage()==g.stage()&&s.group().equals(g.name())).toList();assertEquals(5,subjects.size());assertEquals(2,subjects.stream().filter(s->s.type().equals("Lab")).count());}
    }
    @Test void hybridRetainsOnlyFeasibleResults()throws Exception{
        var d=Data.load(Path.of("examples/trace.week"));d.rows.get("Weights").add(new String[]{"1","0","1","0","0","0","0","0","0"});
        var search=new SearchEngine(new ProblemData(d),new SearchEngine.Settings(7,4,8,30,.5,1000),()->false);
        search.hybridStopping=new HybridStopping(HybridStopping.Mode.STAGNANT_GENERATIONS,2,15);search.run("Hybrid",new ConstraintSearch());assertNotNull(search.best);assertTrue(Evaluator.evaluate(search.m,search.best).valid());assertEquals(Evaluator.evaluate(search.m,search.best).minutes(),search.score);
    }
    @Test void demoIsFourStagesAndAllResources(){var p=SimpleProject.demo();assertTrue(p.health().isEmpty());var m=new ProblemData(p.toData());assertEquals(64,m.events.size());assertEquals(8,m.stages.size());assertEquals(14,p.rooms.size());assertEquals(5,m.days.size());assertTrue(m.choices.stream().noneMatch(List::isEmpty));assertEquals(0,p.toData().rows.get("Term").size());}
    @Test void everyChoiceFitsAClassPeriodAndNeverABreak(){
        var p=SimpleProject.demo();var m=new ProblemData(p.toData());
        for(int i=0;i<m.events.size();i++)for(var choice:m.choices.get(i)){
            var e=m.events.get(i);var day=p.days.stream().filter(d->d.name().equals(choice.day())).findFirst().orElseThrow();
            assertTrue(SimpleProject.periods(day).stream().anyMatch(r->r[4].equals("Teaching")&&Data.minute(r[2])<=choice.start()&&choice.start()+e.span()<=Data.minute(r[3])));
            assertTrue(day.breaks().stream().noneMatch(b->ProblemData.overlap(choice.start(),choice.start()+e.span(),b.start(),b.end())));
        }
    }
    @Test void independentDaysAndPartialRemainder(){
        var d=new SimpleProject.Day("Monday",true,480,780,90,List.of(new SimpleProject.Break(570,600)));
        var periods=SimpleProject.periods(d);assertEquals(4,periods.size());assertEquals("08:00",periods.getFirst()[2]);assertEquals("Required break",periods.get(1)[4]);
        var e=new SimpleProject.Day("Sunday",true,540,750,90,List.of());assertEquals("Unavailable",SimpleProject.periods(e).getLast()[4]);
        assertFalse(SimpleProject.dayError(new SimpleProject.Day("Sunday",true,540,500,90,List.of())).isEmpty());
        assertFalse(SimpleProject.dayError(new SimpleProject.Day("Sunday",true,540,900,90,List.of(new SimpleProject.Break(550,600),new SimpleProject.Break(590,610)))).isEmpty());
    }
    @Test void optionalTeachersAreIndependentAndNamedTeachersShareAResource(){
        var p=SimpleProject.demo();p.subjects.replaceAll(s->new SimpleProject.Subject(s.id(),s.stage(),s.group(),s.name(),s.type(),s.students(),s.sessions(),s.minutes(),""));
        var d=p.toData();assertEquals(40,d.rows.get("Teachers").size());assertTrue(p.health().stream().allMatch(i->!i.error()));
        var first=p.subjects.getFirst();p.subjects.set(0,new SimpleProject.Subject(first.id(),first.stage(),first.group(),first.name(),first.type(),first.students(),2,first.minutes(),"Shared"));
        var second=p.subjects.get(5);p.subjects.set(5,new SimpleProject.Subject(second.id(),second.stage(),second.group(),second.name(),second.type(),second.students(),1,second.minutes(),"Shared"));
        var m=new ProblemData(p.toData());assertEquals(63,m.events.size());assertNotNull(m.conflict(0,new ProblemData.Time("Sunday",540,"Classroom 1"),8,new ProblemData.Time("Sunday",540,"Classroom 2")));
    }
    @Test void healthReportsCapacityAndLongSubject(){
        var p=SimpleProject.demo();var s=p.subjects.getFirst();p.subjects.set(0,new SimpleProject.Subject(s.id(),s.stage(),s.group(),"Oversized",s.type(),70,1,120,s.instructor()));
        assertTrue(p.health().stream().anyMatch(i->i.error()&&i.step()==4&&i.message().contains("70")&&i.message().contains("60")));
        assertTrue(p.health().stream().anyMatch(i->i.error()&&i.step()==3&&i.message().contains("120")));
        assertThrows(IllegalArgumentException.class,p::toData);
    }
    @Test void jsonRoundTripUnicodePipesAndSchema()throws Exception{
        var p=SimpleProject.demo();p.university="زانکۆ | جامعة";var file=temp.resolve("project.json");p.save(file);var copy=SimpleProject.load(file);assertEquals(p.json(),copy.json());
        assertThrows(IllegalArgumentException.class,()->SimpleProject.parse(p.json().replace("\"schemaVersion\": 1","\"schemaVersion\": 99")));
        p.save(file);assertEquals(40,SimpleProject.load(file).subjects.size());
    }
    @Test void fixedWeightsAndSimpleCpAgreement(){
        var p=SimpleProject.demo();p.subjects=new ArrayList<>(p.subjects.subList(0,2));var m=new ProblemData(p.toData());assertArrayEquals(new int[]{1,0,1,0,0,0,0,0,0},Objectives.weights(m));
        var cp=new SearchEngine(m,new SearchEngine.Settings(7,5,16,10,.1,1000),()->false);cp.solveConstraints(new ConstraintSearch());assertNotNull(cp.best);assertTrue(Evaluator.evaluate(m,cp.best).valid());
        assertEquals(Evaluator.evaluate(m,cp.best).minutes(),cp.score);
    }
    @Test void cpHandlesUnevenDurationsAndDifferentDailyWindows(){
        for(int seed:new int[]{1,7,42}){
            var p=SimpleProject.demo();p.subjects=new ArrayList<>(p.subjects.subList(0,3));
            for(int i=0;i<p.subjects.size();i++){var s=p.subjects.get(i);p.subjects.set(i,new SimpleProject.Subject(s.id(),s.stage(),s.group(),s.name(),s.type(),s.students(),1,31+i*7,s.instructor()));}
            p.rooms=new ArrayList<>(List.of(new SimpleProject.Room("Test hall","Classroom",100)));
            for(int j=2;j<5;j++){var d=p.days.get(j);p.days.set(j,new SimpleProject.Day(d.name(),false,d.start(),d.end(),d.classMinutes(),d.breaks()));}
            p.days.set(1,new SimpleProject.Day("Monday",true,481,901,91,List.of(new SimpleProject.Break(620,651))));
            var m=new ProblemData(p.toData());var search=new SearchEngine(m,new SearchEngine.Settings(seed,5,16,10,.1,1000),()->false);
            search.solveConstraints(new ConstraintSearch());assertNotNull(search.best,search.status);var evaluation=Evaluator.evaluate(m,search.best);
            assertTrue(evaluation.valid());assertEquals(evaluation.minutes(),search.score);
        }
    }
    @Test void exportsEscapeTextAndUseGreenPalette()throws Exception{
        var file=temp.resolve("out.csv");SimpleExport.csv(file,List.<String[]>of(new String[]{"Sunday","09:00","10:30","A, B \"C\"","دکتۆر","1 A","Room | 1"}));assertTrue(Files.readString(file).contains("\"A, B \"\"C\"\"\""));
        String css=SimpleExport.htmlCss();assertTrue(css.contains("--primary:#1F8A5B"));assertFalse(css.contains("#15253c"));
    }
}
