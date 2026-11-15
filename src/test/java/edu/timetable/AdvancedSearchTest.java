package edu.timetable;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AdvancedSearchTest {
    SimpleProject project(int subjects){
        var p=SimpleProject.tinyDemo();p.subjects.clear();
        for(int i=0;i<subjects;i++)p.subjects.add(new SimpleProject.Subject("s"+i,1,"A","Subject "+i,"Theory",30,1,30,"Teacher "+i));
        for(int i=0;i<5;i++)p.days.set(i,new SimpleProject.Day(SimpleProject.WEEK.get(i),i==0,540,900,30,List.of()));
        return p;
    }
    int choice(ProblemData m,int event,String day,int start){
        for(int k=0;k<m.choices.get(event).size();k++){var p=m.choices.get(event).get(k);if(p.day().equals(day)&&p.start()==start)return k;}
        throw new AssertionError("Missing choice "+day+" "+start);
    }
    SearchEngine run(ProblemData m,String method){
        var run=new SearchEngine(m,new SearchEngine.Settings(7,5,8,10,.5,10000),()->false);
        run.hybridStopping=new HybridStopping(HybridStopping.Mode.STAGNANT_GENERATIONS,2,15);run.workers=1;run.reportDiagnostics=false;run.warmStart=false;if(method.equals("CP"))run.solveConstraints(new ConstraintSearch());else run.run(method,new ConstraintSearch());return run;
    }
    @Test void dotIsASeparatorAndComparisonAlwaysUsesHardFirst(){
        assertEquals("60.2",new SolutionScore(60,2).toString());
        assertEquals("360.2",new SolutionScore(360,2).toString());
        assertEquals("0.0",new SolutionScore(0,0).toString());
        assertTrue(new SolutionScore(1000000,0).compareTo(new SolutionScore(0,1))<0);
        assertTrue(new SolutionScore(60,2).compareTo(new SolutionScore(360,2))<0);
        assertTrue(new SolutionScore(0,10).compareTo(new SolutionScore(0,2))>0);
    }
    @Test void eachGapDeductsOnlyItsConfiguredRestWithoutNegativeOffsets(){
        var p=project(4);p.days.set(0,new SimpleProject.Day("Sunday",true,540,900,30,List.of(new SimpleProject.Break(600,630),new SimpleProject.Break(690,720))));var m=new ProblemData(p.toData());
        // Gaps 0, 30 and 90 contain scheduled rests of 0, 30 and 30 minutes => penalties 0, 0 and 60.
        int[] genes={choice(m,0,"Sunday",540),choice(m,1,"Sunday",570),choice(m,2,"Sunday",630),choice(m,3,"Sunday",750)};
        var result=Evaluator.evaluate(m,genes);assertTrue(result.valid());assertEquals("60.0",result.ranking().toString());
        p.optimizationGoal=ScheduleObjective.Goal.EARLIER_FINISH;
        assertEquals(60,Evaluator.evaluate(new ProblemData(p.toData()),genes).minutes());
    }
    @Test void fiveTenAmStartsAndSixtyExtraGapMinutesMatchTheExample(){
        var p=project(6);
        for(int i=0;i<5;i++)p.days.set(i,new SimpleProject.Day(SimpleProject.WEEK.get(i),true,540,900,30,List.of()));
        p.days.set(0,new SimpleProject.Day("Sunday",true,540,900,30,List.of(new SimpleProject.Break(630,660))));
        for(int i=0;i<5;i++)p.pin(new SimpleProject.Placement("s"+i,1,SimpleProject.WEEK.get(i),600,"Tiny room"));
        p.pin(new SimpleProject.Placement("s5",1,"Sunday",720,"Tiny room"));
        var m=new ProblemData(p.toData());assertEquals(60,Evaluator.evaluate(m,new int[6]).minutes());
        p.optimizationGoal=ScheduleObjective.Goal.EARLIER_FINISH;m=new ProblemData(p.toData());
        var score=Evaluator.evaluate(m,new int[6]);assertTrue(score.valid());assertEquals("360.0",score.ranking().toString());
        var parts=ScheduleObjective.parts(m,new int[6]);assertEquals(60,parts.gaps());assertEquals(300,parts.starts());
        assertEquals(360,run(m,"CP").score);
        // Two extra groups overlap only in their teacher and room, adding two hard violations.
        // Evaluate directly without the UI health gate, which correctly blocks conflicting pins.
        var d=m.data;d.rows.get("Groups").add(new String[]{"1 B","Stage 1"});d.rows.get("Groups").add(new String[]{"1 C","Stage 1"});
        d.rows.get("Teachers").add(new String[]{"Shared","","*"});
        for(String group:List.of("B","C")){
            d.rows.get("Subjects").add(new String[]{group,group,"Shared","1 "+group,"","30","Lecture","1","30","0","after","yes"});
            d.rows.get("Locks").add(new String[]{group.equals("B")?"6":"7","pin","Sunday","09:00","Tiny room"});
        }
        assertEquals("360.2",Evaluator.evaluate(new ProblemData(d),new int[8]).ranking().toString());
        d.rows.get("SearchObjective").getFirst()[0]=ScheduleObjective.Goal.FEWER_GAPS.name();
        assertEquals("60.2",Evaluator.evaluate(new ProblemData(d),new int[8]).ranking().toString());
    }
    @Test void universityOpeningAtNineIsTheEarliestEligibleStart(){
        var p=project(1);p.days.set(0,new SimpleProject.Day("Sunday",true,480,720,30,List.of()));
        for(var goal:ScheduleObjective.Goal.values()){
            p.optimizationGoal=goal;var m=new ProblemData(p.toData());
            assertTrue(m.choices.getFirst().stream().allMatch(x->x.start()>=540));
            assertEquals(540,m.choices.getFirst().getFirst().start());
        }
    }
    @Test void scheduledRestsAreFreeAndEmptyDaysCostNothing(){
        var p=project(2);p.optimizationGoal=ScheduleObjective.Goal.EARLIER_FINISH;
        p.days.set(0,new SimpleProject.Day("Sunday",true,540,900,30,List.of(new SimpleProject.Break(630,690))));
        var m=new ProblemData(p.toData());int[] genes={choice(m,0,"Sunday",600),choice(m,1,"Sunday",690)};
        assertEquals(60,Evaluator.evaluate(m,genes).minutes()); // Scheduled rest is free; the 10 AM start adds 60 minutes.
        assertEquals(0,ScheduleObjective.parts(m,new int[]{choice(m,0,"Sunday",540),choice(m,1,"Sunday",570)}).total());
    }
    @Test void overlappingResourcesAreCountedSeparatelyAndInvalidSchedulesKeepTheirSoftScore(){
        var p=project(2);p.groups.add(new SimpleProject.Group(1,"B"));
        p.subjects.set(1,new SimpleProject.Subject("s1",1,"B","Subject 1","Theory",30,1,30,"Teacher 0"));
        p.optimizationGoal=ScheduleObjective.Goal.EARLIER_FINISH;
        var m=new ProblemData(p.toData());var score=Evaluator.evaluate(m,new int[]{choice(m,0,"Sunday",600),choice(m,1,"Sunday",600)});
        assertFalse(score.valid());assertEquals(2,score.violations().size());assertEquals("120.2",score.ranking().toString());
    }
    @Test void cpMatchesExhaustiveForBothGoalsWithMultipleDaysAndUnevenDurations(){
        for(var goal:ScheduleObjective.Goal.values()){
            var p=project(3);p.optimizationGoal=goal;
            p.subjects.set(0,new SimpleProject.Subject("s0",1,"A","Short","Theory",30,1,20,"Teacher 0"));
            p.days.set(0,new SimpleProject.Day("Sunday",true,600,780,30,List.of(new SimpleProject.Break(660,720))));
            p.days.set(1,new SimpleProject.Day("Monday",true,540,630,30,List.of()));
            p.pin(new SimpleProject.Placement("s1",1,"Sunday",600,"Tiny room"));
            var m=new ProblemData(p.toData());var exact=run(m,"Brute-force");var cp=run(m,"CP");
            assertTrue(exact.proved);assertTrue(cp.proved);assertNotNull(cp.best);assertEquals(exact.score,cp.score,goal.toString());
            assertEquals(Evaluator.evaluate(m,cp.best).minutes(),cp.score);
        }
    }
    @Test void hybridUsesCpSeedingGeneticMutationAndCpPolishing(){
        var p=project(2);p.optimizationGoal=ScheduleObjective.Goal.EARLIER_FINISH;
        p.days.set(0,new SimpleProject.Day("Sunday",true,600,900,30,List.of(new SimpleProject.Break(630,660))));var m=new ProblemData(p.toData());
        int[] seed={choice(m,0,"Sunday",600),choice(m,1,"Sunday",720)};
        var search=new SearchEngine(m,new SearchEngine.Settings(7,5,4,2,1,1000),()->false);search.reportDiagnostics=false;search.hybridStopping=new HybridStopping(HybridStopping.Mode.STAGNANT_GENERATIONS,2,15);
        var calls=new ArrayList<Boolean>();
        search.run("Hybrid",(run,child,optimize,seconds)->{calls.add(optimize);return seed.clone();});
        assertTrue(calls.size()>=2);assertTrue(calls.getFirst());assertTrue(calls.getLast());assertTrue(search.mutations>0);
        assertNotNull(search.best);assertTrue(Evaluator.evaluate(m,search.best).valid());assertTrue(search.score<=120);
    }
    @Test void realHybridReturnsVerifiedResultsForBothGoalsAndPreservesGoalOnRestore(){
        for(var goal:ScheduleObjective.Goal.values()){
            var p=project(2);p.optimizationGoal=goal;p.pin(new SimpleProject.Placement("s0",1,"Sunday",600,"Tiny room"));
            var search=run(new ProblemData(p.toData()),"Hybrid");assertNotNull(search.best);assertTrue(Evaluator.evaluate(search.m,search.best).valid());
            p.captureResult(search,"Hybrid");var copy=SimpleProject.parse(p.json());assertEquals(goal,copy.optimizationGoal);
            var restored=copy.restoreResult();assertNotNull(restored);assertEquals(search.scoreText(),restored.scoreText());
            copy.optimizationGoal=goal==ScheduleObjective.Goal.FEWER_GAPS?ScheduleObjective.Goal.EARLIER_FINISH:ScheduleObjective.Goal.FEWER_GAPS;
            assertNull(copy.restoreResult());
        }
    }
}
